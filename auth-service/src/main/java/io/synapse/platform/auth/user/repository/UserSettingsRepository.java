package io.synapse.platform.auth.user.repository;

import io.synapse.platform.auth.user.domain.UserSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
