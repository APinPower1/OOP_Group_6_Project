#!/usr/bin/env bash
# End-to-end API smoke test against a RUNNING backend.
#
#   ./scripts/smoke.sh [baseUrl]        # default http://localhost:8080
#
# Uses the four demo accounts (created with DEMO_SEED=true). Creates one
# timestamped event, announcement and user, so run it against a disposable or
# demo database — it intentionally never cleans up after itself.
# Requires: bash, curl, python3. Exit code 0 = all checks passed.

set -uo pipefail
API="${1:-http://localhost:8080}/api"
TS="$(date +%Y%m%d%H%M%S)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
PASS=0
FAIL=0

pass() { PASS=$((PASS + 1)); printf 'PASS  %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); printf 'FAIL  %s\n' "$1"; }

check() { # check <name> <expected> <actual>
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1 (expected [$2] got [$3])"; fi
}

req() { # req <METHOD> <path> [body] [token] -> prints HTTP status, body in $WORK/body
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-s -o "$WORK/body" -w '%{http_code}' -X "$method" "$API$path"
              -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(--data "$body")
  curl "${args[@]}" 2>/dev/null || echo "000"
}

json_get() { # json_get <key-or-index>...  (reads $WORK/body)
  python3 -c '
import json, sys
try:
    value = json.load(open(sys.argv[1]))
    for part in sys.argv[2:]:
        value = value[int(part)] if part.lstrip("-").isdigit() else value[part]
    print("true" if value is True else "false" if value is False
          else value if value is not None else "")
except Exception:
    print("")
' "$WORK/body" "$@" 2>/dev/null
}

login() { # login <email> <password> -> prints access token
  local status
  status="$(req POST /auth/login "{\"email\":\"$1\",\"password\":\"$2\"}")"
  [ "$status" = "200" ] || { echo ""; return; }
  json_get accessToken
}

STUDENT="student@demo.mec.edu"   ORGANIZER="organizer@demo.mec.edu"
FACULTY="faculty@demo.mec.edu"   ADMIN="admin@demo.mec.edu"

echo "== Student Portal API smoke test against $API"
echo "== Run id: $TS"
echo

# 1. Health
status="$(req GET /health)"
check "health endpoint is UP" "200" "$status"
grep -q '"status":"UP"' "$WORK/body" && pass "health body reports UP" || fail "health body reports UP"

# 2. Authentication
status="$(req POST /auth/login '{"email":"student@demo.mec.edu","password":"wrong"}')"
check "wrong password is generic 401" "401" "$status"

status="$(req GET /events)"
check "anonymous call to protected route is 401" "401" "$status"

STUDENT_TOKEN="$(login "$STUDENT" DemoStudent123)"
ORGANIZER_TOKEN="$(login "$ORGANIZER" DemoOrganizer123)"
FACULTY_TOKEN="$(login "$FACULTY" DemoFaculty123)"
ADMIN_TOKEN="$(login "$ADMIN" DemoAdmin123)"
check "all four demo roles log in" "1 1 1 1" \
  "$(for t in "$STUDENT_TOKEN" "$ORGANIZER_TOKEN" "$FACULTY_TOKEN" "$ADMIN_TOKEN"; do
       [ -n "$t" ] && echo 1 || echo 0; done | tr '\n' ' ' | sed 's/ $//')"

status="$(req GET /auth/me "" "$ADMIN_TOKEN")"
check "auth/me echoes the authenticated admin" "200:$ADMIN" "$status:$(json_get email)"

# 3. Role authority
status="$(req POST /events '{"title":"x","description":"x","venue":"x","startTime":"2027-01-01T10:00:00","endTime":"2027-01-01T11:00:00","capacity":5}' "$STUDENT_TOKEN")"
check "student cannot create an event (403)" "403" "$status"

status="$(req GET /admin/users "" "$ORGANIZER_TOKEN")"
check "organizer cannot reach admin routes (403)" "403" "$status"

# 4. Event lifecycle
TITLE="Smoke Event $TS"
EV_BODY="$(printf '{"title":"%s","description":"End-to-end smoke","venue":"Smoke Hall %s","startTime":"2027-01-01T10:00:00","endTime":"2027-01-01T12:00:00","capacity":5}' "$TITLE" "$TS")"
status="$(req POST /events "$EV_BODY" "$ORGANIZER_TOKEN")"
EVENT_ID="$(json_get id)"
check "organizer creates proposal (201, PENDING_APPROVAL)" "201:PENDING_APPROVAL" "$status:$(json_get status)"
[ -n "$EVENT_ID" ] && pass "proposal has an id ($EVENT_ID)" || fail "proposal has an id"

status="$(req GET "/events?q=Smoke%20Event%20$TS" "" "$STUDENT_TOKEN")"
LEN="$(python3 -c 'import json;print(len(json.load(open("'"$WORK"'/body"))))' 2>/dev/null || echo err)"
check "student feed hides the pending proposal" "0" "$LEN"

status="$(req POST "/events/$EVENT_ID/approve" "" "$FACULTY_TOKEN")"
check "faculty approves proposal (200, APPROVED)" "200:APPROVED" "$status:$(json_get status)"

status="$(req GET "/events?q=Smoke%20Event%20$TS" "" "$STUDENT_TOKEN")"
LEN="$(python3 -c 'import json;print(len(json.load(open("'"$WORK"'/body"))))' 2>/dev/null || echo err)"
check "student feed shows the approved event" "1" "$LEN"
check "approved event is visible with correct capacity" "5:APPROVED" "$(json_get 0 capacity):$(json_get 0 status)"

# 5. Registration seat math
status="$(req POST "/events/$EVENT_ID/registrations" "" "$STUDENT_TOKEN")"
check "student registers (201, one seat consumed)" "201:4" "$status:$(json_get availableSlots)"

status="$(req POST "/events/$EVENT_ID/registrations" "" "$STUDENT_TOKEN")"
check "duplicate registration is 409 DUPLICATE_REGISTRATION" "409:DUPLICATE_REGISTRATION" "$status:$(json_get code)"

status="$(req DELETE "/events/$EVENT_ID/registrations/me" "" "$STUDENT_TOKEN")"
check "cancel registration releases the seat (200, slots back to 5)" "200:5" "$status:$(json_get availableSlots)"

# 6. Announcements + notifications (audience filtering)
status="$(req POST /announcements '{"title":"Smoke","content":"Smoke","audienceRole":"STUDENT"}' "$STUDENT_TOKEN")"
check "student cannot publish announcements (403)" "403" "$status"

ANN_BODY="$(printf '{"title":"Smoke Notice %s","content":"Smoke test announcement","audienceRole":"STUDENT"}' "$TS")"
status="$(req POST /announcements "$ANN_BODY" "$ORGANIZER_TOKEN")"
ANN_ID="$(json_get id)"
check "organizer publishes announcement (201)" "201" "$status"

status="$(req GET /notifications/me "" "$STUDENT_TOKEN")"
ANN_NOTIF="$(python3 -c '
import json, sys
items = json.load(open(sys.argv[1]))
print(1 if any(n.get("announcementId") == int(sys.argv[2]) for n in items) else 0)
' "$WORK/body" "$ANN_ID" 2>/dev/null || echo 0)"
check "student received the audience-scoped notification" "1" "$ANN_NOTIF"

status="$(req GET /notifications/me "" "$ORGANIZER_TOKEN")"
ANN_NOTIF="$(python3 -c '
import json, sys
items = json.load(open(sys.argv[1]))
print(1 if any(n.get("announcementId") == int(sys.argv[2]) for n in items) else 0)
' "$WORK/body" "$ANN_ID" 2>/dev/null || echo 0)"
check "organizer (out of audience) got no notification" "0" "$ANN_NOTIF"

NOTIF_ID="$(req GET /notifications/me "" "$STUDENT_TOKEN" >/dev/null; python3 -c '
import json, sys
items = json.load(open(sys.argv[1]))
for n in items:
    if n.get("announcementId") == int(sys.argv[2]):
        print(n["id"]); break
else:
    print("")
' "$WORK/body" "$ANN_ID" 2>/dev/null || echo "")"
status="$(req PATCH "/notifications/$NOTIF_ID/read" "" "$STUDENT_TOKEN")"
check "student marks own notification read (200, readAt set)" "200:1" "$status:$(python3 -c 'import json,sys;print(1 if json.load(open(sys.argv[1])).get("readAt") else 0)' "$WORK/body" 2>/dev/null || echo 0)"

# 7. Admin: report + user lifecycle
status="$(req GET /admin/reports/participation "" "$ADMIN_TOKEN")"
check "admin participation report (200)" "200" "$status"
IN_REPORT="$(python3 -c '
import json, sys
rows = json.load(open(sys.argv[1])).get("participation", [])
print(1 if any(r.get("eventId") == int(sys.argv[2]) for r in rows) else 0)
' "$WORK/body" "$EVENT_ID" 2>/dev/null || echo 0)"
check "report includes the smoke event" "1" "$IN_REPORT"
grep -q '@demo.mec.edu' "$WORK/body" && fail "report exposes no student email" || pass "report exposes no student email"

NEW_EMAIL="smoke.user.$TS@smoke.example.edu"
CREATE_BODY="$(printf '{"fullName":"Smoke User","email":"%s","password":"Initial-Pass-123","department":"CSE","role":"STUDENT","profile":{"rollNumber":"R-SMOKE-%s","semester":4,"batch":"2027"}}' "$NEW_EMAIL" "$TS")"
status="$(req POST /admin/users "$CREATE_BODY" "$ADMIN_TOKEN")"
NEW_ID="$(json_get id)"
check "admin creates a fully profiled user (201)" "201:true" "$status:$(json_get enabled)"

status="$(req POST /auth/login "{\"email\":\"$NEW_EMAIL\",\"password\":\"Initial-Pass-123\"}")"
check "new user can log in (200)" "200" "$status"

status="$(req POST "/admin/users/$NEW_ID/disable" "" "$ADMIN_TOKEN")"
check "admin disables the user (200)" "200" "$status"
status="$(req POST /auth/login "{\"email\":\"$NEW_EMAIL\",\"password\":\"Initial-Pass-123\"}")"
check "disabled user cannot log in (401)" "401" "$status"

status="$(req POST "/admin/users/$NEW_ID/enable" "" "$ADMIN_TOKEN")"
status2="$(req POST /auth/login "{\"email\":\"$NEW_EMAIL\",\"password\":\"Initial-Pass-123\"}")"
check "re-enabled user logs in again (200)" "200:200" "$status:$status2"

echo
echo "== Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
