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

    /**
     * gitops/배포가 표준 PEM(-----BEGIN PRIVATE KEY----- + 줄바꿈)으로 키를 주입해도 파싱돼야 한다.
     * 과거 raw base64만 받던 시절 PEM 키가 들어와 부팅이 실패(Invalid RSA key)했던 회귀를 코드로 막는다.
     */
    @Test
    void rsaKeys_pemFormatWithHeadersAndNewlines_parseSuccessfully() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privatePem = toPem(
                "PRIVATE KEY", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        String publicPem = toPem(
                "PUBLIC KEY", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        JwtProperties properties = new JwtProperties(privatePem, publicPem, "kid", "issuer");

        assertThat(properties.rsaPrivateKey()).isNotNull();
        assertThat(properties.rsaPublicKey()).isNotNull();
    }

    /** base64 본문을 64자마다 줄바꿈하고 PEM 헤더/푸터로 감싸 표준 PEM 블록을 만든다. */
    private static String toPem(String label, String base64) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < base64.length(); index += 64) {
            body.append(base64, index, Math.min(index + 64, base64.length())).append('\n');
        }
        return "-----BEGIN " + label + "-----\n" + body + "-----END " + label + "-----\n";
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
