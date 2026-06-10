package com.synapse.platform.admin.controller;

import com.synapse.platform.admin.dto.AdminSettingsResponse;
import com.synapse.platform.admin.dto.AdminSettingsUpdateRequest;
import com.synapse.platform.admin.service.AdminSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    public AdminSettingsController(AdminSettingsService adminSettingsService) {
        this.adminSettingsService = adminSettingsService;
    }

    @GetMapping
    public AdminSettingsResponse getSettings() {
        return adminSettingsService.getSettings();
    }

    @PutMapping
    public AdminSettingsResponse updateSettings(@Valid @RequestBody AdminSettingsUpdateRequest request) {
        return adminSettingsService.updateSettings(request);
    }
}
