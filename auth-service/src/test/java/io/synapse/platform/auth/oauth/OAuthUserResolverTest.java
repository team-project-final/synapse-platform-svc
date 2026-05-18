package io.synapse.platform.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.synapse.platform.auth.domain.OAuthIdentity;
import io.synapse.platform.auth.domain.Tenant;
import io.synapse.platform.auth.domain.TenantMember;
import io.synapse.platform.auth.repository.OAuthIdentityRepository;
import io.synapse.platform.auth.repository.TenantMemberRepository;
import io.synapse.platform.auth.repository.TenantRepository;
import io.synapse.platform.auth.user.domain.User;
import io.synapse.platform.auth.user.domain.UserSettings;
import io.synapse.platform.auth.user.repository.UserRepository;
import io.synapse.platform.auth.user.repository.UserSettingsRepository;
import io.synapse.platform.auth.util.SlugGenerator;
import io.synapse.platform.common.crypto.FieldEncryptor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuthUserResolverTest {

    private static final String AES_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private SlugGenerator slugGenerator;

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(AES_KEY);

    @Test
    void resolveUser_newAppleUserWithNullName_shouldUseEmailPrefixDisplayName() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OAuthAttributes attributes = new OAuthAttributes(
                "apple",
                "apple-123",
                "apple@example.com",
                null,
                null);
        given(oauthIdentityRepository.findByProviderAndProviderUserId("apple", "apple-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("apple@example.com")).willReturn(Optional.empty());
        given(slugGenerator.generate("apple@example.com")).willReturn("apple");
        given(tenantRepository.save(any(Tenant.class))).willAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            ReflectionTestUtils.setField(tenant, "id", tenantId);
            return tenant;
        });
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", userId);
            return user;
        });
        OAuthUserResolver resolver = resolver();

        // When
        User result = resolver.resolveUser(attributes, "token");

        // Then
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        assertThat(result.getId()).isEqualTo(userId);
        verify(tenantRepository).save(tenantCaptor.capture());
        verify(userRepository).save(userCaptor.capture());
        verify(oauthIdentityRepository).save(identityCaptor.capture());
        verify(tenantMemberRepository).save(any(TenantMember.class));
        verify(userSettingsRepository).save(any(UserSettings.class));
        assertThat(tenantCaptor.getValue().getName()).isEqualTo("apple");
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("apple");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("apple@example.com");
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo("apple");
        assertThat(identityCaptor.getValue().getEmail()).isEqualTo("apple@example.com");
        assertThat(fieldEncryptor.decrypt(identityCaptor.getValue().getAccessTokenEnc())).isEqualTo("token");
    }

    private OAuthUserResolver resolver() {
        return new OAuthUserResolver(
                userRepository,
                oauthIdentityRepository,
                tenantRepository,
                tenantMemberRepository,
                userSettingsRepository,
                slugGenerator,
                fieldEncryptor);
    }
}
