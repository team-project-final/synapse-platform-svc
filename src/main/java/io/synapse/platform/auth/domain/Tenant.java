package io.synapse.platform.auth.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String plan = "free";

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "tenant_type", nullable = false)
    private String tenantType = "personal";

    @Column(nullable = false)
    private String region = "ap-northeast-2";

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String settings = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected Tenant() {
    }

    public static Tenant ofPersonal(String displayName, String slug) {
        Tenant tenant = new Tenant();
        tenant.name = displayName;
        tenant.slug = slug;
        return tenant;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getPlan() {
        return plan;
    }

    public String getStatus() {
        return status;
    }

    public void activatePlan(String planCode) {
        this.plan = planCode;
        this.updatedAt = OffsetDateTime.now();
    }
}
