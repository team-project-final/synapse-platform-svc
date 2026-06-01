package com.synapse.platform.billing.controller;

import com.synapse.platform.billing.dto.request.TenantStatusChangeRequest;
import com.synapse.platform.billing.dto.response.AdminTenantPageResponse;
import com.synapse.platform.billing.service.AdminTenantService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTenantController {

    private final AdminTenantService adminTenantService;

    public AdminTenantController(AdminTenantService adminTenantService) {
        this.adminTenantService = adminTenantService;
    }

    @GetMapping
    public AdminTenantPageResponse listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return AdminTenantPageResponse.from(adminTenantService.listTenants(page, size));
    }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeTenantStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TenantStatusChangeRequest request) {
        adminTenantService.changeTenantStatus(id, request.status());
    }
}
