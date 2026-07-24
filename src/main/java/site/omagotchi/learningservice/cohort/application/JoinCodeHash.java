package site.omagotchi.learningservice.cohort.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class JoinCodeHash {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private JoinCodeHash() {
    }

    public static String sha256(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
