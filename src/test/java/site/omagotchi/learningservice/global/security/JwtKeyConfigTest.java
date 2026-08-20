package site.omagotchi.learningservice.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

class JwtKeyConfigTest {

    private final JwtKeyConfig jwtKeyConfig = new JwtKeyConfig();

    @Test
    @DisplayName("2048 bit RSA 공개키 로딩")
    void loadsSupportedRsaPublicKey() {
        RSAPublicKey expected = generatePublicKey(2048);

        RSAPublicKey actual = jwtKeyConfig.jwtPublicKey(properties(pemResource(expected)));

        then(actual.getModulus()).isEqualTo(expected.getModulus());
        then(actual.getPublicExponent()).isEqualTo(expected.getPublicExponent());
    }

    @Test
    @DisplayName("2048 bit 미만 RSA 공개키 거부")
    void rejectsWeakRsaPublicKey() {
        Throwable throwable = catchThrowable(() -> jwtKeyConfig.jwtPublicKey(
                properties(pemResource(generatePublicKey(1024)))
        ));

        then(throwable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048 bit 이상");
    }

    @Test
    @DisplayName("잘못된 PEM 공개키 거부")
    void rejectsMalformedPublicKey() {
        Throwable throwable = catchThrowable(() -> jwtKeyConfig.jwtPublicKey(
                properties(new ByteArrayResource("not-a-public-key".getBytes(StandardCharsets.UTF_8)))
        ));

        then(throwable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("읽을 수 없습니다");
    }

    private JwtProperties properties(ByteArrayResource publicKeyResource) {
        return new JwtProperties(
                TestJwtKeyConfig.ISSUER,
                TestJwtKeyConfig.AUDIENCE,
                publicKeyResource
        );
    }

    private RSAPublicKey generatePublicKey(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return (RSAPublicKey) generator.generateKeyPair().getPublic();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("테스트 RSA key를 생성할 수 없습니다.", exception);
        }
    }

    private ByteArrayResource pemResource(RSAPublicKey publicKey) {
        String encodedKey = Base64.getMimeEncoder(
                64,
                "\n".getBytes(StandardCharsets.UTF_8)
        ).encodeToString(publicKey.getEncoded());
        String pem = """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----
                """.formatted(encodedKey);
        return new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8));
    }
}
