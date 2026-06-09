package com.synapse.platform.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String locale = "ko-KR";

    @Column(nullable = false)
    private String theme = "system";

    @Column(name = "srs_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String srsConfig = "{}";

    @Column(name = "editor_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String editorConfig = "{}";

    @Column(name = "notification_prefs", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String notificationPrefs = "{}";

    @Column(name = "pii_redaction_enabled", nullable = false)
    private boolean piiRedactionEnabled = false;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn = false;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserSettings() {
    }

    public static UserSettings defaultFor(UUID userId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        settings.updatedAt = OffsetDateTime.now();
        return settings;
    }

    public void updateLocale(String locale) {
        this.locale = locale;
        updatedAt = OffsetDateTime.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLocale() {
        return locale;
    }

    public String getTheme() {
        return theme;
    }
}
