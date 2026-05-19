package com.synapse.platform.auth.oauth;

import com.synapse.platform.auth.domain.OAuthIdentity;
import com.synapse.platform.auth.domain.Tenant;
import com.synapse.platform.auth.domain.TenantMember;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import com.synapse.platform.global.crypto.FieldEncryptor;
import com.synapse.platform.user.api.OAuthUserCreateCommand;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OAuthUserResolver {

    private final UserApi userApi;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final SlugGenerator slugGenerator;
    private final FieldEncryptor fieldEncryptor;

    public OAuthUserResolver(
            UserApi userApi,
            OAuthIdentityRepository oauthIdentityRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            SlugGenerator slugGenerator,
            FieldEncryptor fieldEncryptor) {
        this.userApi = userApi;
        this.oauthIdentityRepository = oauthIdentityRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.slugGenerator = slugGenerator;
        this.fieldEncryptor = fieldEncryptor;
    }

    public UserInfo resolveUser(OAuthAttributes attributes, String accessToken) {
        String accessTokenEnc = encryptedAccessToken(accessToken);
        Optional<OAuthIdentity> identity = oauthIdentityRepository.findByProviderAndProviderUserId(
                attributes.provider(),
                attributes.providerId());
        if (identity.isPresent()) {
            OAuthIdentity existing = identity.get();
            existing.updateAccessTokenEnc(accessTokenEnc);
            oauthIdentityRepository.save(existing);
            return userApi.findById(existing.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found"));
        }

        if (attributes.email() != null) {
            Optional<UserInfo> existingUser = userApi.findByEmail(attributes.email());
            if (existingUser.isPresent()) {
                UserInfo user = existingUser.get();
                oauthIdentityRepository.save(OAuthIdentity.of(
                        user.id(),
                        attributes.provider(),
                        attributes.providerId(),
                        attributes.email(),
                        accessTokenEnc));
                return user;
            }
        }

        return signUp(attributes, accessTokenEnc);
    }

    private UserInfo signUp(OAuthAttributes attributes, String accessTokenEnc) {
        String displayName = displayName(attributes);
        String email = email(attributes, displayName);
        String slug = slugGenerator.generate(email);

        Tenant tenant = tenantRepository.save(Tenant.ofPersonal(displayName, slug));
        OAuthUserCreateCommand command = new OAuthUserCreateCommand(
                email,
                slug,
                displayName,
                attributes.avatarUrl(),
                tenant.getId());
        UserInfo savedUser = userApi.createForOAuth(command);

        oauthIdentityRepository.save(OAuthIdentity.of(
                savedUser.id(),
                attributes.provider(),
                attributes.providerId(),
                attributes.email(),
                accessTokenEnc));
        tenantMemberRepository.save(TenantMember.ofOwner(tenant.getId(), savedUser.id()));

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
