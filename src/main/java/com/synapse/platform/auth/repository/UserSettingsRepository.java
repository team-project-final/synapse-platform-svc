package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.domain.UserSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
