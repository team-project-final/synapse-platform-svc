package com.synapse.platform.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.dto.request.UpdateTenantRequest;
import com.synapse.platform.auth.dto.response.MyTenantResponse;
import com.synapse.platform.auth.dto.response.TenantMemberPageResponse;
import com.synapse.platform.auth.dto.response.TenantMemberResponse;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserSummary;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSelfServiceService {

    private static final String ROLE_OWNER = "owner";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_MEMBER = "member";
    private static final String ROLE_VIEWER = "viewer";
    private static final TypeReference<Map<String, Object>> SETTINGS_TYPE = new TypeReference<>() {
    };

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserApi userApi;
    private final ObjectMapper objectMapper;

    public TenantSelfServiceService(
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            UserApi userApi,
            ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.userApi = userApi;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MyTenantResponse getMyTenant(UUID userId) {
        TenantContext context = tenantContext(userId);
        return toTenantResponse(context.tenant(), context.requesterMember().getRole());
    }

    @Transactional
    public MyTenantResponse updateMyTenant(UUID userId, UpdateTenantRequest request) {
        TenantContext context = tenantContext(userId);
        ensureManager(context.requesterMember());
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw TenantSelfServiceException.invalidTenantName();
            }
            context.tenant().updateName(name);
        }
        if (request.settings() != null) {
            Map<String, Object> mergedSettings = readSettings(context.tenant().getSettings());
            mergedSettings.putAll(request.settings());
            context.tenant().updateSettings(writeSettings(mergedSettings));
        }
        return toTenantResponse(context.tenant(), context.requesterMember().getRole());
    }

    @Transactional(readOnly = true)
    public TenantMemberPageResponse listMembers(UUID userId, Pageable pageable) {
        TenantContext context = tenantContext(userId);
        List<TenantMember> members = tenantMemberRepository.findByTenantId(
                context.tenant().getId(),
                memberSort(pageable));
        Map<UUID, UserSummary> summaries = userSummaryMap(members.stream()
                .map(TenantMember::getUserId)
                .toList());
        List<TenantMemberResponse> activeItems = members.stream()
                .filter(member -> summaries.containsKey(member.getUserId()))
                .map(member -> toMemberResponse(member, summaries.get(member.getUserId())))
                .toList();
        int pageSize = pageable.getPageSize();
        long offset = pageable.getOffset();
        int fromIndex = offset >= activeItems.size() ? activeItems.size() : (int) offset;
        int toIndex = Math.min(fromIndex + pageSize, activeItems.size());
        List<TenantMemberResponse> items = activeItems.subList(fromIndex, toIndex);
        return new TenantMemberPageResponse(
                items,
                pageable.getPageNumber(),
                pageSize,
                activeItems.size(),
                totalPages(activeItems.size(), pageSize));
    }

    @Transactional
    public TenantMemberResponse updateMemberRole(UUID requesterId, UUID targetUserId, String role) {
        TenantContext context = tenantContext(requesterId);
        ensureManager(context.requesterMember());
        if (requesterId.equals(targetUserId)) {
            throw TenantSelfServiceException.selfMutationNotAllowed();
        }
        String normalizedRole = normalizeAssignableRole(role);
        TenantMember targetMember = findMember(context.tenant().getId(), targetUserId);
        if (ROLE_OWNER.equals(targetMember.getRole())) {
            throw TenantSelfServiceException.ownerRoleChangeNotAllowed();
        }
        targetMember.changeRole(normalizedRole);
        UserSummary summary = userSummaryMap(List.of(targetUserId)).get(targetUserId);
        if (summary == null) {
            throw TenantSelfServiceException.memberNotFound();
        }
        return toMemberResponse(targetMember, summary);
    }

    @Transactional
    public void removeMember(UUID requesterId, UUID targetUserId) {
        TenantContext context = tenantContext(requesterId);
        ensureManager(context.requesterMember());
        if (requesterId.equals(targetUserId)) {
            throw TenantSelfServiceException.selfMutationNotAllowed();
        }
        TenantMember targetMember = findMember(context.tenant().getId(), targetUserId);
        if (ROLE_OWNER.equals(targetMember.getRole()) && !ROLE_OWNER.equals(context.requesterMember().getRole())) {
            throw TenantSelfServiceException.ownerMutationNotAllowed();
        }
        if (ROLE_OWNER.equals(targetMember.getRole())
                && tenantMemberRepository.countByTenantIdAndRole(context.tenant().getId(), ROLE_OWNER) <= 1) {
            throw TenantSelfServiceException.lastOwnerRemovalNotAllowed();
        }
        tenantMemberRepository.delete(targetMember);
    }

    private TenantContext tenantContext(UUID userId) {
        UserInfo user = userApi.findById(userId)
                .orElseThrow(TenantSelfServiceException::userNotFound);
        if (user.defaultTenantId() == null) {
            throw TenantSelfServiceException.tenantNotFound();
        }
        Tenant tenant = tenantRepository.findByIdAndDeletedAtIsNull(user.defaultTenantId())
                .orElseThrow(TenantSelfServiceException::tenantNotFound);
        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenant.getId(), userId)
                .orElseThrow(TenantSelfServiceException::membershipRequired);
        return new TenantContext(tenant, member);
    }

    private TenantMember findMember(UUID tenantId, UUID userId) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(TenantSelfServiceException::memberNotFound);
    }

    private void ensureManager(TenantMember member) {
        if (!ROLE_OWNER.equals(member.getRole()) && !ROLE_ADMIN.equals(member.getRole())) {
            throw TenantSelfServiceException.adminRequired();
        }
    }

    private String normalizeAssignableRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (!ROLE_ADMIN.equals(normalizedRole)
                && !ROLE_MEMBER.equals(normalizedRole)
                && !ROLE_VIEWER.equals(normalizedRole)) {
            throw TenantSelfServiceException.invalidRole();
        }
        return normalizedRole;
    }

    private MyTenantResponse toTenantResponse(Tenant tenant, String myRole) {
        return new MyTenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getPlan(),
                tenant.getStatus(),
                tenant.getTenantType(),
                tenant.getRegion(),
                readSettings(tenant.getSettings()),
                myRole,
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }

    private TenantMemberResponse toMemberResponse(TenantMember member, UserSummary summary) {
        return new TenantMemberResponse(
                member.getUserId(),
                summary.email(),
                summary.displayName(),
                member.getRole(),
                member.getJoinedAt());
    }

    private Map<UUID, UserSummary> userSummaryMap(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userApi.findSummariesByIds(userIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    private Sort memberSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable.getSort();
        }
        return Sort.by(
                Sort.Order.asc("joinedAt"),
                Sort.Order.asc("userId"));
    }

    private int totalPages(int totalElements, int pageSize) {
        if (totalElements == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    private Map<String, Object> readSettings(String rawSettings) {
        if (rawSettings == null || rawSettings.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(rawSettings, SETTINGS_TYPE));
        } catch (JsonProcessingException exception) {
            throw TenantSelfServiceException.invalidSettings();
        }
    }

    private String writeSettings(Map<String, Object> settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw TenantSelfServiceException.invalidSettings();
        }
    }

    private record TenantContext(
            Tenant tenant,
            TenantMember requesterMember
    ) {
    }
}
