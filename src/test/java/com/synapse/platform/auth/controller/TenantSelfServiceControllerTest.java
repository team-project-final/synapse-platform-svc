package com.synapse.platform.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.dto.response.MyTenantResponse;
import com.synapse.platform.auth.dto.response.TenantInvitationResponse;
import com.synapse.platform.auth.dto.response.TenantMemberPageResponse;
import com.synapse.platform.auth.dto.response.TenantMemberResponse;
import com.synapse.platform.auth.service.TenantSelfServiceService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TenantSelfServiceControllerTest {

    private final TenantSelfServiceService tenantSelfServiceService = mock(TenantSelfServiceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TenantSelfServiceController(tenantSelfServiceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void getMe_withoutJwt_shouldReturnUnauthorizedProblem() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void getMe_authenticatedUser_shouldReturnTenant() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        given(tenantSelfServiceService.getMyTenant(userId)).willReturn(tenantResponse(tenantId, "owner"));

        mockMvc.perform(get("/api/v1/tenants/me")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId.toString()))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.settings.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.myRole").value("owner"));
    }

    @Test
    void updateMe_validRequest_shouldDelegate() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        given(tenantSelfServiceService.updateMyTenant(eq(userId), any()))
                .willReturn(tenantResponse(tenantId, "admin"));

        mockMvc.perform(put("/api/v1/tenants/me")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Acme Team",
                                "settings", Map.of("timezone", "Asia/Seoul")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("admin"));
    }

    @Test
    void listMembers_shouldClampPageSizeAndReturnMembers() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        OffsetDateTime joinedAt = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        given(tenantSelfServiceService.listMembers(eq(userId), any()))
                .willReturn(new TenantMemberPageResponse(
                        List.of(new TenantMemberResponse(
                                memberId,
                                "member@example.com",
                                "Member",
                                "member",
                                joinedAt)),
                        0,
                        100,
                        1,
                        1));

        mockMvc.perform(get("/api/v1/tenants/me/members")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .queryParam("page", "-1")
                        .queryParam("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value(memberId.toString()))
                .andExpect(jsonPath("$.items[0].role").value("member"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tenantSelfServiceService).listMembers(eq(userId), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getSort())
                .isEqualTo(Sort.by(
                        Sort.Order.asc("joinedAt"),
                        Sort.Order.asc("userId")));
    }

    @Test
    void updateMemberRole_shouldDelegate() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        OffsetDateTime joinedAt = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        given(tenantSelfServiceService.updateMemberRole(userId, memberId, "viewer"))
                .willReturn(new TenantMemberResponse(
                        memberId,
                        "member@example.com",
                        "Member",
                        "viewer",
                        joinedAt));

        mockMvc.perform(put("/api/v1/tenants/me/members/{userId}", memberId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "viewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("viewer"));
    }

    @Test
    void removeMember_shouldReturnNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/tenants/me/members/{userId}", memberId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNoContent());

        verify(tenantSelfServiceService).removeMember(userId, memberId);
    }

    @Test
    void createInvitation_shouldReturnCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        OffsetDateTime expiresAt = timestamp.plusDays(7);
        given(tenantSelfServiceService.createInvitation(eq(userId), any()))
                .willReturn(new TenantInvitationResponse(
                        invitationId,
                        "new@example.com",
                        "member",
                        "pending",
                        expiresAt,
                        timestamp));

        mockMvc.perform(post("/api/v1/tenants/me/invitations")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "new@example.com",
                                "role", "member"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(invitationId.toString()))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("member"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void createInvitation_invalidEmailShouldReturnBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tenants/me/invitations")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invalid-email",
                                "role", "member"))))
                .andExpect(status().isBadRequest());
    }

    private static MyTenantResponse tenantResponse(UUID tenantId, String myRole) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        return new MyTenantResponse(
                tenantId,
                "Acme",
                "acme",
                "free",
                "active",
                "personal",
                "ap-northeast-2",
                Map.of("timezone", "Asia/Seoul"),
                myRole,
                timestamp,
                timestamp);
    }
}
