package com.synapse.platform.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OAuthIdentitySchemaTest {

    @Test
    void oauthIdentityMigration_doneWhenSchema_shouldUseProviderIdColumn() throws IOException {
        // Given
        ClassPathResource migration = new ClassPathResource("db/migration/V3__init_users_and_auth.sql");

        // When
        String ddl = migration.getContentAsString(StandardCharsets.UTF_8);

        // Then
        assertThat(ddl).contains("provider_id");
        assertThat(ddl).contains("access_token_enc");
        assertThat(ddl).contains("oauth_identities(provider, provider_id)");
        assertThat(ddl).doesNotContain("provider_user_id");
    }

    @Test
    void oauthIdentityEntity_doneWhenSchema_shouldMapProviderIdColumn() throws NoSuchFieldException {
        // Given
        Column column = OAuthIdentity.class.getDeclaredField("providerUserId").getAnnotation(Column.class);

        // When
        String columnName = column.name();

        // Then
        assertThat(columnName).isEqualTo("provider_id");
    }
}
