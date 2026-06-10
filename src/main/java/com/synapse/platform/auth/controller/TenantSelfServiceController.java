package com.synapse.platform.auth.controller;

import com.synapse.platform.auth.dto.request.UpdateTenantMemberRoleRequest;
import com.synapse.platform.auth.dto.request.UpdateTenantRequest;
import com.synapse.platform.auth.dto.response.MyTenantResponse;
import com.synapse.platform.auth.dto.response.TenantMemberPageResponse;
import com.synapse.platform.auth.dto.response.TenantMemberResponse;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.service.TenantSelfServiceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantSelfServiceController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TenantSelfServiceService tenantSelfServiceService;

    public TenantSelfServiceController(TenantSelfServiceService tenantSelfServiceService) {
        this.tenantSelfServiceService = tenantSelfServiceService;
    }

    @GetMapping("/me")
    public MyTenantResponse getMe(Authentication authentication) {
        return tenantSelfServiceService.getMyTenant(currentUserId(authentication));
    }

    @PutMapping("/me")
    public MyTenantResponse updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateTenantRequest request) {
        return tenantSelfServiceService.updateMyTenant(currentUserId(authentication), request);
    }

    @GetMapping("/me/members")
    public TenantMemberPageResponse listMembers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return tenantSelfServiceService.listMembers(currentUserId(authentication), pageRequest(page, size));
    }

    @PutMapping("/me/members/{userId}")
    public TenantMemberResponse updateMemberRole(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateTenantMemberRoleRequest request) {
        return tenantSelfServiceService.updateMemberRole(
                currentUserId(authentication),
                userId,
                request.role());
    }

    @DeleteMapping("/me/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            Authentication authentication,
            @PathVariable UUID userId) {
        tenantSelfServiceService.removeMember(currentUserId(authentication), userId);
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(
                        Sort.Order.asc("joinedAt"),
                        Sort.Order.asc("userId")));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedTokenException("Authentication required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedTokenException("Authentication required");
        }
    }
}
