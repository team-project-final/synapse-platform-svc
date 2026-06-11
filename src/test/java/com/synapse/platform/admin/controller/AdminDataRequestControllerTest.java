package com.synapse.platform.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.admin.dto.AdminDataRequestActionRequest;
import com.synapse.platform.admin.dto.AdminDataRequestCreateRequest;
import com.synapse.platform.admin.dto.AdminDataRequestResponse;
import com.synapse.platform.admin.dto.AdminDataRequestSearchRequest;
import com.synapse.platform.admin.entity.GdprDataRequestStatus;
import com.synapse.platform.admin.entity.GdprDataRequestType;
import com.synapse.platform.admin.service.AdminDataRequestService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

class AdminDataRequestControllerTest {

    private final AdminDataRequestService adminDataRequestService =
            org.mockito.Mockito.mock(AdminDataRequestService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminDataRequestController(adminDataRequestService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listRequests_shouldReturnPage() throws Exception {
        UUID requestId = UUID.randomUUID();
        given(adminDataRequestService.listRequests(any(AdminDataRequestSearchRequest.class)))
                .willReturn(new PageImpl<>(List.of(response(requestId, UUID.randomUUID()))));

        mockMvc.perform(get("/api/v1/admin/data-requests")
                        .queryParam("status", "pending")
                        .queryParam("q", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("대기"));

        verify(adminDataRequestService).listRequests(new AdminDataRequestSearchRequest("pending", "user", 0, 20));
    }

    @Test
    void getRequest_shouldReturnDetail() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(adminDataRequestService.getRequest(requestId)).willReturn(response(requestId, userId));

        mockMvc.perform(get("/api/v1/admin/data-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.executionLogs[0]").value("created"));
    }

    @Test
    void createRequest_shouldReturnCreatedRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(adminDataRequestService.createRequest(any(AdminDataRequestCreateRequest.class)))
                .willReturn(response(requestId, userId));

        mockMvc.perform(post("/api/v1/admin/data-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "userId", userId,
                                "type", "DATA_EXPORT",
                                "reason", "test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));
    }

    @Test
    void applyAction_shouldReturnUpdatedRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        given(adminDataRequestService.applyAction(any(UUID.class), any(AdminDataRequestActionRequest.class)))
                .willReturn(response(requestId, UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/admin/data-requests/{id}/actions", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "action", "APPROVE",
                                "reason", "ok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));
    }

    @Test
    void applyAction_conflict_shouldReturnConflict() throws Exception {
        UUID requestId = UUID.randomUUID();
        given(adminDataRequestService.applyAction(any(UUID.class), any(AdminDataRequestActionRequest.class)))
                .willThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Data erasure requests require dedicated deletion workflow"));

        mockMvc.perform(post("/api/v1/admin/data-requests/{id}/actions", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "action", "EXECUTE",
                                "reason", "delete user data"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Data erasure requests require dedicated deletion workflow"));
    }

    @Test
    void createRequest_missingUserId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/data-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "type", "DATA_EXPORT"))))
                .andExpect(status().isBadRequest());
    }

    private static AdminDataRequestResponse response(UUID id, UUID userId) {
        return new AdminDataRequestResponse(
                id,
                userId,
                "user@example.com",
                "User",
                GdprDataRequestType.DATA_EXPORT,
                "데이터 내보내기",
                GdprDataRequestStatus.PENDING,
                "대기",
                OffsetDateTime.parse("2026-06-11T10:00:00Z"),
                OffsetDateTime.parse("2026-07-11T10:00:00Z"),
                30,
                null,
                "test",
                null,
                "summary",
                "created",
                List.of("created"));
    }
}
