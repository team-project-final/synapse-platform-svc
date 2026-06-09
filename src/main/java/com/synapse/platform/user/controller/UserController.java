package com.synapse.platform.user.controller;

import com.synapse.platform.user.dto.request.UserPasswordChangeRequest;
import com.synapse.platform.user.dto.request.UserProfileUpdateRequest;
import com.synapse.platform.user.dto.response.UserProfileResponse;
import com.synapse.platform.user.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserProfileResponse getMe(Authentication authentication) {
        return userService.getMyProfile(currentUserId(authentication));
    }

    @PutMapping("/me")
    UserProfileResponse updateMe(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return userService.updateMyProfile(currentUserId(authentication), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(
            Authentication authentication,
            @Valid @RequestBody UserPasswordChangeRequest request) {
        userService.changeMyPassword(
                currentUserId(authentication),
                request.currentPassword(),
                request.newPassword());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMe(Authentication authentication) {
        userService.deleteMyAccount(currentUserId(authentication));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
