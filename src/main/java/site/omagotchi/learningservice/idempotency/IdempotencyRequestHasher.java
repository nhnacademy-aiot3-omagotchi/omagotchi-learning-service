package site.omagotchi.learningservice.idempotency;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class IdempotencyRequestHasher {

    private static final String HASH_ALGORITHM = "SHA-256";
    private final JsonMapper jsonMapper;

    public IdempotencyRequestHasher(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String hash(String operationCode, Object[] methodArguments) {
        try {
            HashInput hashInput = new HashInput(
                    operationCode,
                    extractBusinessArguments(methodArguments)
            );
            String json = jsonMapper.writeValueAsString(hashInput);

            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = messageDigest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("멱등성 요청 해시를 생성하지 못했습니다.", exception);
        }
    }

    private List<Object> extractBusinessArguments(Object[] methodArguments) {
        List<Object> businessArguments = new ArrayList<>();
        for (Object argument : methodArguments) {
            if (!(argument instanceof IdempotencyContext)) {
                businessArguments.add(argument);
            }
        }
        return businessArguments;
    }

    private record HashInput(
            String operationCode,
            List<Object> arguments
    ) {
    }
}
