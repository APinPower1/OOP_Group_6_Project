package edu.mec.studentportal.club;

import edu.mec.studentportal.common.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller for the club endpoints in docs/API_CONTRACT.md. */
@RestController
@RequestMapping("/api/clubs")
public class ClubController {
    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping
    public List<ClubResponse> list(Authentication authentication) {
        return clubService.listActive(CurrentUser.id(authentication));
    }

    @GetMapping("/{clubId}")
    public ClubResponse get(@PathVariable long clubId, Authentication authentication) {
        return clubService.get(clubId, CurrentUser.id(authentication));
    }

    @PostMapping
    public ResponseEntity<ClubResponse> create(
        @Valid @RequestBody ClubRequest request,
        Authentication authentication
    ) {
        ClubResponse response = clubService.create(request, CurrentUser.id(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{clubId}")
    public ClubResponse update(
        @PathVariable long clubId,
        @Valid @RequestBody ClubRequest request,
        Authentication authentication
    ) {
        return clubService.update(clubId, request, CurrentUser.id(authentication));
    }

    @PostMapping("/{clubId}/deactivate")
    public ClubResponse deactivate(@PathVariable long clubId, Authentication authentication) {
        return clubService.deactivate(clubId, CurrentUser.id(authentication));
    }

    @PostMapping("/{clubId}/membership")
    public ResponseEntity<MembershipResponse> join(@PathVariable long clubId, Authentication authentication) {
        MembershipResponse response = clubService.join(clubId, CurrentUser.id(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{clubId}/membership")
    public MembershipResponse leave(@PathVariable long clubId, Authentication authentication) {
        return clubService.leave(clubId, CurrentUser.id(authentication));
    }

    @GetMapping("/{clubId}/members")
    public List<ClubMemberResponse> members(@PathVariable long clubId, Authentication authentication) {
        return clubService.members(clubId, CurrentUser.id(authentication));
    }
}
