-- Password management: every account must change its password on first login
-- (initial passwords are set by an admin, never by email). DEFAULT TRUE also
-- forces existing accounts (including demo users) to pick a new password the
-- next time they sign in. Raw INSERT helpers in tests omit the column and get
-- the default.
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT TRUE;
