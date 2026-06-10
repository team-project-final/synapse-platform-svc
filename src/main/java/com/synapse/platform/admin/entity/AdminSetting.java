package com.synapse.platform.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_settings")
public class AdminSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 1000)
    private String settingValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminSetting() {
    }

    private AdminSetting(String settingKey, String settingValue) {
        Instant now = Instant.now();
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static AdminSetting create(String settingKey, String settingValue) {
        return new AdminSetting(settingKey, settingValue);
    }

    public void updateValue(String settingValue) {
        if (this.settingValue.equals(settingValue)) {
            return;
        }
        this.settingValue = settingValue;
        this.updatedAt = Instant.now();
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
