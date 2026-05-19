package com.synapse.platform.auth.oauth;

import com.synapse.platform.user.api.UserInfo;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final OAuthUserResolver oAuthUserResolver;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Autowired
    public CustomOidcUserService(OAuthUserResolver oAuthUserResolver) {
        this(oAuthUserResolver, new OidcUserService());
    }

    CustomOidcUserService(
            OAuthUserResolver oAuthUserResolver,
            OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.oAuthUserResolver = oAuthUserResolver;
        this.delegate = delegate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser oidcUser = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, oidcUser.getAttributes());
        UserInfo user = oAuthUserResolver.resolveUser(attributes, accessToken(request));

        Map<String, Object> enrichedAttributes = new HashMap<>(oidcUser.getAttributes());
        enrichedAttributes.put("userId", user.id().toString());
        return new DefaultOidcUser(
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                new OidcUserInfo(enrichedAttributes),
                attributes.nameAttributeKey());
    }

    private String accessToken(OidcUserRequest request) {
        if (request.getAccessToken() == null || request.getAccessToken().getTokenValue() == null) {
            return null;
        }
        return request.getAccessToken().getTokenValue();
    }
}
