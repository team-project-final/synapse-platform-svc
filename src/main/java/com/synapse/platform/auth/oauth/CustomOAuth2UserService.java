package com.synapse.platform.auth.oauth;

import com.synapse.platform.auth.domain.OAuthIdentity;
import com.synapse.platform.auth.domain.Tenant;
import com.synapse.platform.auth.domain.TenantMember;
import com.synapse.platform.auth.domain.User;
import com.synapse.platform.auth.domain.UserSettings;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.auth.repository.UserRepository;
import com.synapse.platform.auth.repository.UserSettingsRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SlugGenerator slugGenerator;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Autowired
    public CustomOAuth2UserService(
            UserRepository userRepository,
            OAuthIdentityRepository oauthIdentityRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            UserSettingsRepository userSettingsRepository,
            SlugGenerator slugGenerator) {
        this(
                userRepository,
                oauthIdentityRepository,
                tenantRepository,
                tenantMemberRepository,
                userSettingsRepository,
                slugGenerator,
                new DefaultOAuth2UserService());
    }

    CustomOAuth2UserService(
            UserRepository userRepository,
            OAuthIdentityRepository oauthIdentityRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            UserSettingsRepository userSettingsRepository,
            SlugGenerator slugGenerator,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.userRepository = userRepository;
        this.oauthIdentityRepository = oauthIdentityRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.slugGenerator = slugGenerator;
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User oAuth2User = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        User user = resolveUser(attributes);

        Map<String, Object> enrichedAttributes = new HashMap<>(oAuth2User.getAttributes());
        enrichedAttributes.put("userId", user.getId().toString());
        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                enrichedAttributes,
                attributes.nameAttributeKey());
    }

    private User resolveUser(OAuthAttributes attributes) {
        Optional<OAuthIdentity> identity = oauthIdentityRepository.findByProviderAndProviderUserId(
                attributes.provider(),
                attributes.providerId());
        if (identity.isPresent()) {
            return userRepository.findById(identity.get().getUserId()).orElseThrow();
        }

        if (attributes.email() != null) {
            Optional<User> existingUser = userRepository.findByEmail(attributes.email());
            if (existingUser.isPresent()) {
                oauthIdentityRepository.save(OAuthIdentity.of(
                        existingUser.get(),
                        attributes.provider(),
                        attributes.providerId(),
                        attributes.email()));
                return existingUser.get();
            }
        }

        return signUp(attributes);
    }

    private User signUp(OAuthAttributes attributes) {
        String email = attributes.email() != null
                ? attributes.email()
                : attributes.name() + "@github.placeholder";
        String slug = slugGenerator.generate(email);

        Tenant tenant = tenantRepository.save(Tenant.ofPersonal(attributes.name(), slug));
        User user = User.ofOAuth(email, slug, attributes.name(), attributes.avatarUrl());
        user.updateDefaultTenantId(tenant.getId());
        User savedUser = userRepository.save(user);

        oauthIdentityRepository.save(OAuthIdentity.of(
                savedUser,
                attributes.provider(),
                attributes.providerId(),
                attributes.email()));
        tenantMemberRepository.save(TenantMember.ofOwner(tenant.getId(), savedUser.getId()));
        userSettingsRepository.save(UserSettings.defaultFor(savedUser.getId()));

        return savedUser;
    }
}
