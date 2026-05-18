package com.synapse.platform.user.repository;

import com.synapse.platform.user.domain.UserSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
