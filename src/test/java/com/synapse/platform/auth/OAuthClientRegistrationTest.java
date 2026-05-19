package com.synapse.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@SpringBootTest
class OAuthClientRegistrationTest {

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void appleRegistration_shouldUseOidcAndClientSecretPost() {
        // Given
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("apple");

        // Then
        assertThat(registration.getScopes()).contains("openid", "name", "email");
        assertThat(registration.getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(registration.getProviderDetails().getJwkSetUri())
                .isEqualTo("https://appleid.apple.com/auth/keys");
        assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                .isEqualTo("sub");
    }
}
