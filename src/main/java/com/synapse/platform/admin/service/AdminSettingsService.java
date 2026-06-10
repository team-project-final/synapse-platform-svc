package com.synapse.platform.admin.service;

import com.synapse.platform.admin.dto.AdminSettingsResponse;
import com.synapse.platform.admin.dto.AdminSettingsResponse.FeatureFlagItem;
import com.synapse.platform.admin.dto.AdminSettingsResponse.PlanQuotaItem;
import com.synapse.platform.admin.dto.AdminSettingsResponse.RateLimitSettings;
import com.synapse.platform.admin.dto.AdminSettingsUpdateRequest;
import com.synapse.platform.admin.dto.AdminSettingsUpdateRequest.FeatureFlagUpdate;
import com.synapse.platform.admin.entity.AdminSetting;
import com.synapse.platform.admin.repository.AdminSettingRepository;
import com.synapse.platform.auth.api.PlanQuotaInfo;
import com.synapse.platform.auth.api.TenantApi;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSettingsService {

    private static final String FEATURE_PREFIX = "feature.";
    private static final String RATE_LIMIT_KEY = "rateLimit.apiRequestsPerMinute";
    private static final int DEFAULT_API_REQUESTS_PER_MINUTE = 100;

    private static final List<FeatureFlagDefinition> FEATURE_FLAGS = List.of(
            new FeatureFlagDefinition("aiCardAutoGeneration", "AI 카드 자동 생성", true),
            new FeatureFlagDefinition("googleSocialLogin", "소셜 로그인 (Google)", true),
            new FeatureFlagDefinition("githubSocialLogin", "소셜 로그인 (GitHub)", false),
            new FeatureFlagDefinition("realtimeCollaborativeEditing", "실시간 협업 편집", false),
            new FeatureFlagDefinition("voiceReviewBeta", "베타: 음성 복습", false)
    );

    private static final Map<String, FeatureFlagDefinition> FEATURE_FLAG_BY_KEY = FEATURE_FLAGS.stream()
            .collect(Collectors.toUnmodifiableMap(FeatureFlagDefinition::key, Function.identity()));

    private static final Map<String, Integer> PLAN_ORDER = Map.of(
            "free", 0,
            "pro", 1,
            "team", 2,
            "enterprise", 3
    );

    private final TenantApi tenantApi;
    private final AdminSettingRepository adminSettingRepository;

    public AdminSettingsService(
            TenantApi tenantApi,
            AdminSettingRepository adminSettingRepository) {
        this.tenantApi = tenantApi;
        this.adminSettingRepository = adminSettingRepository;
    }

    @Transactional(readOnly = true)
    public AdminSettingsResponse getSettings() {
        List<AdminSetting> settings = adminSettingRepository.findAll();
        return toResponse(settings);
    }

    @Transactional
    public AdminSettingsResponse updateSettings(AdminSettingsUpdateRequest request) {
        Map<String, AdminSetting> settings = settingsByKey(adminSettingRepository.findAll());
        List<AdminSetting> changedSettings = new ArrayList<>();

        for (FeatureFlagUpdate featureFlag : uniqueFeatureFlags(request.featureFlags())) {
            FeatureFlagDefinition definition = FEATURE_FLAG_BY_KEY.get(featureFlag.key());
            if (definition == null) {
                throw badRequest("Unknown feature flag key: " + featureFlag.key());
            }
            changedSettings.add(upsert(
                    settings,
                    featureSettingKey(definition.key()),
                    Boolean.toString(featureFlag.enabled())));
        }

        changedSettings.add(upsert(
                settings,
                RATE_LIMIT_KEY,
                Integer.toString(request.rateLimit().apiRequestsPerMinute())));
        adminSettingRepository.saveAll(changedSettings);

        return toResponse(new ArrayList<>(settings.values()));
    }

    private List<FeatureFlagUpdate> uniqueFeatureFlags(List<FeatureFlagUpdate> featureFlags) {
        Map<String, FeatureFlagUpdate> unique = new LinkedHashMap<>();
        for (FeatureFlagUpdate featureFlag : featureFlags) {
            if (unique.putIfAbsent(featureFlag.key(), featureFlag) != null) {
                throw badRequest("Duplicated feature flag key: " + featureFlag.key());
            }
        }
        return List.copyOf(unique.values());
    }

    private AdminSetting upsert(Map<String, AdminSetting> settings, String settingKey, String settingValue) {
        AdminSetting setting = settings.get(settingKey);
        if (setting == null) {
            AdminSetting created = AdminSetting.create(settingKey, settingValue);
            settings.put(settingKey, created);
            return created;
        }
        setting.updateValue(settingValue);
        return setting;
    }

    private AdminSettingsResponse toResponse(List<AdminSetting> settings) {
        Map<String, AdminSetting> settingsMap = settingsByKey(settings);
        return new AdminSettingsResponse(
                planQuotas(),
                featureFlags(settingsMap),
                rateLimit(settingsMap),
                latestUpdatedAt(settings));
    }

    private Map<String, AdminSetting> settingsByKey(List<AdminSetting> settings) {
        return settings.stream()
                .collect(Collectors.toMap(
                        AdminSetting::getSettingKey,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    private List<PlanQuotaItem> planQuotas() {
        return tenantApi.listPlanQuotas().stream()
                .sorted(Comparator
                        .comparingInt((PlanQuotaInfo quota) -> PLAN_ORDER.getOrDefault(quota.plan(), Integer.MAX_VALUE))
                        .thenComparing(PlanQuotaInfo::plan))
                .map(this::toPlanQuotaItem)
                .toList();
    }

    private PlanQuotaItem toPlanQuotaItem(PlanQuotaInfo quota) {
        return new PlanQuotaItem(
                quota.plan(),
                quota.displayName(),
                quota.maxNotes(),
                quota.maxCards(),
                quota.maxStorageBytes(),
                quota.maxAiTokensMonthly(),
                quota.maxAiCardGenerationsMonthly(),
                quota.maxUsersPerTenant());
    }

    private List<FeatureFlagItem> featureFlags(Map<String, AdminSetting> settings) {
        return FEATURE_FLAGS.stream()
                .map(definition -> new FeatureFlagItem(
                        definition.key(),
                        definition.label(),
                        booleanValue(settings, featureSettingKey(definition.key()), definition.enabledByDefault())))
                .toList();
    }

    private RateLimitSettings rateLimit(Map<String, AdminSetting> settings) {
        return new RateLimitSettings(integerValue(
                settings,
                RATE_LIMIT_KEY,
                DEFAULT_API_REQUESTS_PER_MINUTE));
    }

    private boolean booleanValue(Map<String, AdminSetting> settings, String settingKey, boolean defaultValue) {
        return Optional.ofNullable(settings.get(settingKey))
                .map(AdminSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    private int integerValue(Map<String, AdminSetting> settings, String settingKey, int defaultValue) {
        return Optional.ofNullable(settings.get(settingKey))
                .map(AdminSetting::getSettingValue)
                .flatMap(this::parseInteger)
                .orElse(defaultValue);
    }

    private Optional<Integer> parseInteger(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private OffsetDateTime latestUpdatedAt(List<AdminSetting> settings) {
        return settings.stream()
                .map(AdminSetting::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(Comparator.naturalOrder())
                .map(this::toOffsetDateTime)
                .orElse(null);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String featureSettingKey(String featureKey) {
        return FEATURE_PREFIX + featureKey;
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private record FeatureFlagDefinition(String key, String label, boolean enabledByDefault) {
    }
}
