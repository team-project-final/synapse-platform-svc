package com.synapse.platform.user.controller;

import com.synapse.platform.user.dto.request.AdminUserSearchRequest;
import com.synapse.platform.user.dto.request.UserStatusChangeRequest;
import com.synapse.platform.user.dto.response.AdminUserPageResponse;
import com.synapse.platform.user.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminUserPageResponse listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return AdminUserPageResponse.from(
                adminUserService.listUsers(new AdminUserSearchRequest(q, status, page, size)));
    }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeUserStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusChangeRequest request) {
        if ("suspended".equals(request.status())) {
            adminUserService.suspendUser(id, currentUserId(authentication));
            return;
        }
        adminUserService.activateUser(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(Authentication authentication, @PathVariable UUID id) {
        adminUserService.deleteUser(id, currentUserId(authentication));
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
