package com.synapse.platform.auth.oauth;

import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.user.domain.User;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import com.synapse.platform.shared.crypto.FieldEncryptor;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuthUserResolver oAuthUserResolver;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Autowired
    public CustomOAuth2UserService(OAuthUserResolver oAuthUserResolver) {
        this(oAuthUserResolver, new DefaultOAuth2UserService());
    }

    CustomOAuth2UserService(
            OAuthUserResolver oAuthUserResolver,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.oAuthUserResolver = oAuthUserResolver;
        this.delegate = delegate;
    }

    CustomOAuth2UserService(
            UserRepository userRepository,
            OAuthIdentityRepository oauthIdentityRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            UserSettingsRepository userSettingsRepository,
            SlugGenerator slugGenerator,
            FieldEncryptor fieldEncryptor,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.oAuthUserResolver = new OAuthUserResolver(
                userRepository,
                oauthIdentityRepository,
                tenantRepository,
                tenantMemberRepository,
                userSettingsRepository,
                slugGenerator,
                fieldEncryptor);
        this.delegate = delegate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User oAuth2User = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());
        User user = oAuthUserResolver.resolveUser(attributes, accessToken(request));

        Map<String, Object> enrichedAttributes = new HashMap<>(oAuth2User.getAttributes());
        enrichedAttributes.put("userId", user.getId().toString());
        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                enrichedAttributes,
                attributes.nameAttributeKey());
    }

    private String accessToken(OAuth2UserRequest request) {
        if (request.getAccessToken() == null || request.getAccessToken().getTokenValue() == null) {
            return null;
        }
        return request.getAccessToken().getTokenValue();
    }
}
