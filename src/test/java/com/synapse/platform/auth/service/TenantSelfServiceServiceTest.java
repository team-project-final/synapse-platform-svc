package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.dto.request.UpdateTenantRequest;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TenantSelfServiceServiceTest {

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private UserApi userApi;

    @Test
    void getMyTenant_memberCanReadTenant() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        givenTenantContext(userId, tenant, member(tenantId, userId, "member"));

        var response = service().getMyTenant(userId);

        assertThat(response.id()).isEqualTo(tenantId);
        assertThat(response.myRole()).isEqualTo("member");
        assertThat(response.settings()).containsEntry("theme", "dark");
    }

    @Test
    void updateMyTenant_adminCanUpdateNameAndMergeSettings() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        givenTenantContext(userId, tenant, member(tenantId, userId, "admin"));

        var response = service().updateMyTenant(
                userId,
                new UpdateTenantRequest(" Acme Team ", Map.of("timezone", "Asia/Seoul")));

        assertThat(response.name()).isEqualTo("Acme Team");
        assertThat(response.settings())
                .containsEntry("theme", "dark")
                .containsEntry("timezone", "Asia/Seoul");
        assertThat(tenant.getName()).isEqualTo("Acme Team");
    }

    @Test
    void updateMyTenant_memberShouldFail() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        givenTenantContext(userId, tenant, member(tenantId, userId, "member"));

        assertThatThrownBy(() -> service().updateMyTenant(
                        userId,
                        new UpdateTenantRequest("Acme Team", Map.of())))
                .isInstanceOfSatisfying(TenantSelfServiceException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(403));
    }

    @Test
    void listMembers_shouldReturnTenantMembersWithUserSummaries() {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        TenantMember owner = member(tenantId, userId, "owner");
        TenantMember member = member(tenantId, memberId, "member");
        PageRequest pageRequest = PageRequest.of(0, 20);
        givenTenantContext(userId, tenant, owner);
        given(tenantMemberRepository.findByTenantId(tenantId, defaultMemberSort()))
                .willReturn(List.of(owner, member));
        given(userApi.findSummariesByIds(List.of(userId, memberId))).willReturn(List.of(
                new UserSummary(userId, "owner@example.com", "Owner"),
                new UserSummary(memberId, "member@example.com", "Member")));

        var response = service().listMembers(userId, pageRequest);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).email()).isEqualTo("owner@example.com");
        assertThat(response.items().get(1).role()).isEqualTo("member");
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    void listMembers_shouldExcludeMembersMissingUserSummary() {
        UUID userId = UUID.randomUUID();
        UUID deletedUserId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        TenantMember owner = member(tenantId, userId, "owner");
        TenantMember deletedMember = member(tenantId, deletedUserId, "member");
        TenantMember activeMember = member(tenantId, memberId, "member");
        PageRequest pageRequest = PageRequest.of(0, 20);
        givenTenantContext(userId, tenant, owner);
        given(tenantMemberRepository.findByTenantId(tenantId, defaultMemberSort()))
                .willReturn(List.of(owner, deletedMember, activeMember));
        given(userApi.findSummariesByIds(List.of(userId, deletedUserId, memberId))).willReturn(List.of(
                new UserSummary(userId, "owner@example.com", "Owner"),
                new UserSummary(memberId, "member@example.com", "Member")));

        var response = service().listMembers(userId, pageRequest);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .extracting(com.synapse.platform.auth.dto.response.TenantMemberResponse::userId)
                .containsExactly(userId, memberId);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void listMembers_largePageOffsetShouldReturnEmptyItems() {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        TenantMember owner = member(tenantId, userId, "owner");
        TenantMember member = member(tenantId, memberId, "member");
        PageRequest pageRequest = PageRequest.of(Integer.MAX_VALUE, 100);
        givenTenantContext(userId, tenant, owner);
        given(tenantMemberRepository.findByTenantId(tenantId, defaultMemberSort()))
                .willReturn(List.of(owner, member));
        given(userApi.findSummariesByIds(List.of(userId, memberId))).willReturn(List.of(
                new UserSummary(userId, "owner@example.com", "Owner"),
                new UserSummary(memberId, "member@example.com", "Member")));

        var response = service().listMembers(userId, pageRequest);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void updateMemberRole_ownerCanChangeMemberRole() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        TenantMember targetMember = member(tenantId, memberId, "member");
        givenTenantContext(ownerId, tenant, member(tenantId, ownerId, "owner"));
        given(tenantMemberRepository.findByTenantIdAndUserId(tenantId, memberId))
                .willReturn(Optional.of(targetMember));
        given(userApi.findSummariesByIds(List.of(memberId))).willReturn(List.of(
                new UserSummary(memberId, "member@example.com", "Member")));

        var response = service().updateMemberRole(ownerId, memberId, " VIEWER ");

        assertThat(response.role()).isEqualTo("viewer");
        assertThat(targetMember.getRole()).isEqualTo("viewer");
    }

    @Test
    void updateMemberRole_selfMutationShouldFail() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        givenTenantContext(ownerId, tenant, member(tenantId, ownerId, "owner"));

        assertThatThrownBy(() -> service().updateMemberRole(ownerId, ownerId, "viewer"))
                .isInstanceOfSatisfying(TenantSelfServiceException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(400));
    }

    @Test
    void updateMemberRole_ownerRoleChangeShouldFail() {
        UUID requesterId = UUID.randomUUID();
        UUID targetOwnerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        TenantMember targetOwner = member(tenantId, targetOwnerId, "owner");
        givenTenantContext(requesterId, tenant, member(tenantId, requesterId, "owner"));
        given(tenantMemberRepository.findByTenantIdAndUserId(tenantId, targetOwnerId))
                .willReturn(Optional.of(targetOwner));

        assertThatThrownBy(() -> service().updateMemberRole(requesterId, targetOwnerId, "member"))
                .isInstanceOfSatisfying(TenantSelfServiceException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(409));
    }

    @Test
    void removeMember_ownerCanRemoveMember() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        TenantMember targetMember = member(tenantId, memberId, "member");
        givenTenantContext(ownerId, tenant, member(tenantId, ownerId, "owner"));
        given(tenantMemberRepository.findByTenantIdAndUserId(tenantId, memberId))
                .willReturn(Optional.of(targetMember));

        service().removeMember(ownerId, memberId);

        verify(tenantMemberRepository).delete(targetMember);
        verify(tenantMemberRepository, never()).countByTenantIdAndRole(any(), any());
    }

    @Test
    void removeMember_adminCannotRemoveOwner() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        givenTenantContext(adminId, tenant, member(tenantId, adminId, "admin"));
        given(tenantMemberRepository.findByTenantIdAndUserId(tenantId, ownerId))
                .willReturn(Optional.of(member(tenantId, ownerId, "owner")));

        assertThatThrownBy(() -> service().removeMember(adminId, ownerId))
                .isInstanceOfSatisfying(TenantSelfServiceException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(403));
    }

    @Test
    void removeMember_lastOwnerShouldFail() {
        UUID ownerId = UUID.randomUUID();
        UUID targetOwnerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        givenTenantContext(ownerId, tenant, member(tenantId, ownerId, "owner"));
        given(tenantMemberRepository.findByTenantIdAndUserId(tenantId, targetOwnerId))
                .willReturn(Optional.of(member(tenantId, targetOwnerId, "owner")));
        given(tenantMemberRepository.countByTenantIdAndRole(tenantId, "owner")).willReturn(1L);

        assertThatThrownBy(() -> service().removeMember(ownerId, targetOwnerId))
                .isInstanceOfSatisfying(TenantSelfServiceException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(409));
    }

    private TenantSelfServiceService service() {
        return new TenantSelfServiceService(
                tenantRepository,
                tenantMemberRepository,
                userApi,
                new ObjectMapper());
    }

    private void givenTenantContext(UUID userId, Tenant tenant, TenantMember requesterMember) {
        given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
                userId,
                "user@example.com",
                "User",
                tenant.getId())));
        given(tenantRepository.findByIdAndDeletedAtIsNull(tenant.getId())).willReturn(Optional.of(tenant));
        given(tenantMemberRepository.findByTenantIdAndUserId(tenant.getId(), userId))
                .willReturn(Optional.of(requesterMember));
    }

    private static Tenant tenant(UUID tenantId) {
        Tenant tenant = Tenant.ofPersonal("Acme", "acme");
        ReflectionTestUtils.setField(tenant, "id", tenantId);
        ReflectionTestUtils.setField(tenant, "settings", "{\"theme\":\"dark\"}");
        ReflectionTestUtils.setField(tenant, "createdAt", TIMESTAMP);
        ReflectionTestUtils.setField(tenant, "updatedAt", TIMESTAMP);
        return tenant;
    }

    private static TenantMember member(UUID tenantId, UUID userId, String role) {
        TenantMember member = TenantMember.ofOwner(tenantId, userId);
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "joinedAt", TIMESTAMP);
        return member;
    }

    private static Sort defaultMemberSort() {
        return Sort.by(
                Sort.Order.asc("joinedAt"),
                Sort.Order.asc("userId"));
    }
}
