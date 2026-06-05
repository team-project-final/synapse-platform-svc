package com.synapse.platform.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * JWT 키 파싱 단위 검증. 실제 배포에서 키가 잘못/누락됐을 때를 코드로 잡기 위함(#62 회고).
 */
class JwtPropertiesTest {

    @Test
    void rsaKeys_validBase64_parseSuccessfully() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        JwtProperties properties = new JwtProperties(privateKey, publicKey, "kid", "issuer");

        assertThat(properties.rsaPrivateKey()).isNotNull();
        assertThat(properties.rsaPublicKey()).isNotNull();
    }

    @Test
    void rsaPrivateKey_invalidValue_throwsIllegalArgument() {
        JwtProperties properties = new JwtProperties("local-dummy", "local-dummy", "kid", "issuer");

        assertThatThrownBy(properties::rsaPrivateKey)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid RSA private key");
    }

    @Test
    void rsaPublicKey_invalidValue_throwsIllegalArgument() {
        JwtProperties properties = new JwtProperties("local-dummy", "local-dummy", "kid", "issuer");

        assertThatThrownBy(properties::rsaPublicKey)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid RSA public key");
    }
}
