package io.synapse.platform.auth.oauth;

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
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OAuthUserResolver {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SlugGenerator slugGenerator;
    private final FieldEncryptor fieldEncryptor;

    public OAuthUserResolver(
            UserRepository userRepository,
            OAuthIdentityRepository oauthIdentityRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            UserSettingsRepository userSettingsRepository,
            SlugGenerator slugGenerator,
            FieldEncryptor fieldEncryptor) {
        this.userRepository = userRepository;
        this.oauthIdentityRepository = oauthIdentityRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.slugGenerator = slugGenerator;
        this.fieldEncryptor = fieldEncryptor;
    }

    public User resolveUser(OAuthAttributes attributes, String accessToken) {
        String accessTokenEnc = encryptedAccessToken(accessToken);
        Optional<OAuthIdentity> identity = oauthIdentityRepository.findByProviderAndProviderUserId(
                attributes.provider(),
                attributes.providerId());
        if (identity.isPresent()) {
            OAuthIdentity existing = identity.get();
            existing.updateAccessTokenEnc(accessTokenEnc);
            oauthIdentityRepository.save(existing);
            return userRepository.findById(existing.getUserId()).orElseThrow();
        }

        if (attributes.email() != null) {
            Optional<User> existingUser = userRepository.findByEmail(attributes.email());
            if (existingUser.isPresent()) {
                oauthIdentityRepository.save(OAuthIdentity.of(
                        existingUser.get().getId(),
                        attributes.provider(),
                        attributes.providerId(),
                        attributes.email(),
                        accessTokenEnc));
                return existingUser.get();
            }
        }

        return signUp(attributes, accessTokenEnc);
    }

    private User signUp(OAuthAttributes attributes, String accessTokenEnc) {
        String displayName = displayName(attributes);
        String email = email(attributes, displayName);
        String slug = slugGenerator.generate(email);

        Tenant tenant = tenantRepository.save(Tenant.ofPersonal(displayName, slug));
        User user = User.ofOAuth(email, slug, displayName, attributes.avatarUrl());
        user.updateDefaultTenantId(tenant.getId());
        User savedUser = userRepository.save(user);

        oauthIdentityRepository.save(OAuthIdentity.of(
                savedUser.getId(),
                attributes.provider(),
                attributes.providerId(),
                attributes.email(),
                accessTokenEnc));
        tenantMemberRepository.save(TenantMember.ofOwner(tenant.getId(), savedUser.getId()));
        userSettingsRepository.save(UserSettings.defaultFor(savedUser.getId()));

        return savedUser;
    }

    private String encryptedAccessToken(String accessToken) {
        if (accessToken == null) {
            return null;
        }
        return fieldEncryptor.encrypt(accessToken);
    }

    private String displayName(OAuthAttributes attributes) {
        if (attributes.name() != null && !attributes.name().isBlank()) {
            return attributes.name();
        }
        if (attributes.email() != null && attributes.email().contains("@")) {
            return attributes.email().substring(0, attributes.email().indexOf('@'));
        }
        return attributes.provider() + "-" + attributes.providerId();
    }

    private String email(OAuthAttributes attributes, String displayName) {
        if (attributes.email() != null) {
            return attributes.email();
        }
        return displayName + "@" + attributes.provider() + ".placeholder";
    }
}
