package com.synapse.platform.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mfa_backup_codes")
public class MfaBackupCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected MfaBackupCode() {
    }

    public static MfaBackupCode create(UUID userId, String codeHash) {
        MfaBackupCode backupCode = new MfaBackupCode();
        backupCode.userId = userId;
        backupCode.codeHash = codeHash;
        return backupCode;
    }

    public void markUsed() {
        usedAt = OffsetDateTime.now();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }
}
