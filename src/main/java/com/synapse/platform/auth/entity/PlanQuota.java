package com.synapse.platform.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_quotas")
public class PlanQuota {

    @Id
    private String plan;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "max_notes")
    private Integer maxNotes;

    @Column(name = "max_cards")
    private Integer maxCards;

    @Column(name = "max_storage_bytes")
    private Long maxStorageBytes;

    @Column(name = "max_ai_tokens_monthly")
    private Long maxAiTokensMonthly;

    @Column(name = "max_ai_card_generations_monthly")
    private Integer maxAiCardGenerationsMonthly;

    @Column(name = "max_users_per_tenant")
    private Integer maxUsersPerTenant;

    protected PlanQuota() {
    }

    public String getPlan() {
        return plan;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Integer getMaxNotes() {
        return maxNotes;
    }

    public Integer getMaxCards() {
        return maxCards;
    }

    public Long getMaxStorageBytes() {
        return maxStorageBytes;
    }

    public Long getMaxAiTokensMonthly() {
        return maxAiTokensMonthly;
    }

    public Integer getMaxAiCardGenerationsMonthly() {
        return maxAiCardGenerationsMonthly;
    }

    public Integer getMaxUsersPerTenant() {
        return maxUsersPerTenant;
    }
}
