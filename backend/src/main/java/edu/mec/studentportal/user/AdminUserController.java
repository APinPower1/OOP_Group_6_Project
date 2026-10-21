package edu.mec.studentportal.user;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public AdminUserPageResponse listUsers(
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size,
        @RequestParam(name = "role", required = false) String role,
        @RequestParam(name = "enabled", required = false) String enabled,
        @RequestParam(name = "q", required = false) String q
    ) {
        return userService.listUsers(
            AdminUserListQuery.from(page, size, role, enabled, q)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createUser(
        @Valid @RequestBody AdminUserCreateRequest request
    ) {
        return userService.createUser(AdminUserCreateCommand.from(request));
    }

    @PutMapping("/{userId}")
    public AdminUserResponse updateUser(
        @PathVariable String userId,
        @AuthenticationPrincipal UserSecurityState currentUser,
        @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        return userService.updateUser(
            parseUserId(userId),
            currentUser.id(),
            AdminUserUpdateCommand.from(request)
        );
    }

    @PostMapping("/{userId}/disable")
    public AdminUserResponse disableUser(
        @PathVariable String userId,
        @AuthenticationPrincipal UserSecurityState currentUser
    ) {
        return userService.disableUser(parseUserId(userId), currentUser.id());
    }

    @PostMapping("/{userId}/enable")
    public AdminUserResponse enableUser(@PathVariable String userId) {
        return userService.enableUser(parseUserId(userId));
    }

    private static long parseUserId(String rawUserId) {
        long userId;
        try {
            userId = Long.parseLong(rawUserId);
        } catch (NumberFormatException exception) {
            throw invalidUserId();
        }
        if (userId <= 0) {
            throw invalidUserId();
        }
        return userId;
    }

    private static ApiException invalidUserId() {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_REQUEST,
            "userId must be a positive integer."
        );
    }
}
