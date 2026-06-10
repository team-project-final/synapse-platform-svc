package com.synapse.platform.admin.controller;

import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse;
import com.synapse.platform.admin.service.AdminAnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService) {
        this.adminAnalyticsService = adminAnalyticsService;
    }

    @GetMapping("/summary")
    public AdminAnalyticsSummaryResponse getSummary() {
        return adminAnalyticsService.getSummary();
    }
}
