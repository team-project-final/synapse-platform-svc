package io.synapse.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

import io.synapse.platform.auth.repository.TenantRepository;
import io.synapse.platform.auth.util.SlugGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlugGeneratorTest {

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void generate_uniqueEmail_shouldReturnBaseSlug() {
        given(tenantRepository.existsBySlug("user")).willReturn(false);
        SlugGenerator slugGenerator = new SlugGenerator(tenantRepository);

        String slug = slugGenerator.generate("user@example.com");

        assertThat(slug).isEqualTo("user");
    }

    @Test
    void generate_duplicateEmail_shouldReturnBaseWithSixCharSuffix() {
        given(tenantRepository.existsBySlug("user")).willReturn(true);
        given(tenantRepository.existsBySlug(argThat(slug -> slug.matches("user_[a-z0-9]{6}"))))
                .willReturn(false);
        SlugGenerator slugGenerator = new SlugGenerator(tenantRepository);

        String slug = slugGenerator.generate("user@example.com");

        assertThat(slug).matches("user_[a-z0-9]{6}");
    }

    @Test
    void generate_blankBase_shouldUseUserPrefix() {
        given(tenantRepository.existsBySlug("user")).willReturn(false);
        SlugGenerator slugGenerator = new SlugGenerator(tenantRepository);

        String slug = slugGenerator.generate("!!!@example.com");

        assertThat(slug).isEqualTo("user");
    }
}