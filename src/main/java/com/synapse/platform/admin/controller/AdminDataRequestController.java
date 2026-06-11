package com.synapse.platform.admin.controller;

import com.synapse.platform.admin.dto.AdminDataRequestActionRequest;
import com.synapse.platform.admin.dto.AdminDataRequestCreateRequest;
import com.synapse.platform.admin.dto.AdminDataRequestPageResponse;
import com.synapse.platform.admin.dto.AdminDataRequestResponse;
import com.synapse.platform.admin.dto.AdminDataRequestSearchRequest;
import com.synapse.platform.admin.service.AdminDataRequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/data-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDataRequestController {

    private final AdminDataRequestService adminDataRequestService;

    public AdminDataRequestController(AdminDataRequestService adminDataRequestService) {
        this.adminDataRequestService = adminDataRequestService;
    }

    @GetMapping
    public AdminDataRequestPageResponse listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return AdminDataRequestPageResponse.from(
                adminDataRequestService.listRequests(new AdminDataRequestSearchRequest(status, q, page, size)));
    }

    @GetMapping("/{id}")
    public AdminDataRequestResponse getRequest(@PathVariable UUID id) {
        return adminDataRequestService.getRequest(id);
    }

    @PostMapping
    public AdminDataRequestResponse createRequest(@Valid @RequestBody AdminDataRequestCreateRequest request) {
        return adminDataRequestService.createRequest(request);
    }

    @PostMapping("/{id}/actions")
    public AdminDataRequestResponse applyAction(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDataRequestActionRequest request) {
        return adminDataRequestService.applyAction(id, request);
    }
}
