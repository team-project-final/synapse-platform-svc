package com.synapse.platform.auth.config;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "synapse.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        String kid,
        String issuer
) {

    public RSAPrivateKey rsaPrivateKey() {
        try {
            byte[] keyBytes = decodeKey(privateKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RSA private key", exception);
        }
    }

    public RSAPublicKey rsaPublicKey() {
        try {
            byte[] keyBytes = decodeKey(publicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RSA public key", exception);
        }
    }

    /**
     * PEM(헤더/개행 포함)과 raw base64(DER)를 모두 허용한다.
     *
     * <p>gitops/배포 환경이 표준 PEM 형식(-----BEGIN PRIVATE KEY----- + 줄바꿈)으로 키를 주입해도
     * platform이 자족적으로 파싱하기 위함이다. 과거엔 {@code Base64.getDecoder().decode()}로 raw
     * base64만 받아 PEM이 들어오면 부팅이 실패(Invalid RSA key)했다. PEM 헤더/푸터와 모든 공백·개행을
     * 제거한 뒤 base64 디코딩하므로 두 형식 모두 동일하게 처리된다.
     */
    private static byte[] decodeKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }
        String normalized = key
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
