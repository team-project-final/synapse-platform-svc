package com.synapse.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlugGeneratorTest {

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void generate_중복없음_shouldReturnBaseSlug() {
        // Given
        given(tenantRepository.existsBySlug("user")).willReturn(false);
        SlugGenerator slugGenerator = new SlugGenerator(tenantRepository);

        // When
        String slug = slugGenerator.generate("user@example.com");

        // Then
        assertThat(slug).isEqualTo("user");
    }

    @Test
    void generate_중복1회_shouldReturnBaseWithSixCharSuffix() {
        // Given
        given(tenantRepository.existsBySlug("user")).willReturn(true);
        given(tenantRepository.existsBySlug(argThat(slug -> slug.matches("user_[a-z0-9]{6}"))))
                .willReturn(false);
        SlugGenerator slugGenerator = new SlugGenerator(tenantRepository);

        // When
        String slug = slugGenerator.generate("user@example.com");

        // Then
        assertThat(slug).matches("user_[a-z0-9]{6}");
    }

    @Test
    void generate_빈Base_shouldUseUserPrefix() {
        // Given
        given(tenantRepository.existsBySlug("user")).willReturn(false);
        SlugGenerator slugGenerator = new SlugGenerator(tenantRepository);

        // When
        String slug = slugGenerator.generate("!!!@example.com");

        // Then
        assertThat(slug).isEqualTo("user");
    }
}
