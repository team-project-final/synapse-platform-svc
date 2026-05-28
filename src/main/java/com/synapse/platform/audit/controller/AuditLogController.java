package com.synapse.platform.audit.controller;

import com.synapse.platform.audit.dto.AuditLogResponse;
import com.synapse.platform.audit.service.AuditLogService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLogResponse> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID userId,
            Pageable pageable) {
        return auditLogService.getAuditLogs(action, userId, pageable);
    }
}
