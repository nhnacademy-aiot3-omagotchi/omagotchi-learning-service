package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CommandReceiptService {

    private static final String HASH_ALGORITHM = "SHA-256";

    private String hash(String commandCode, String normalizedRequest) {
        try {
            byte[] bytes = (commandCode + '\n' + normalizedRequest)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(HASH_ALGORITHM).digest(bytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    public record CommandResult<T>(
            short httpStatus,
            String resultCode,
            T payload,
            UUID targetTimerRunId,
            UUID targetStudyRecordId
    ) {
        public CommandResult {
            if (httpStatus < 100 || httpStatus > 599) {
                throw new IllegalArgumentException("httpStatus가 유효하지 않습니다.");
            }
            Objects.requireNonNull(resultCode, "resultCode가 null입니다.");
        }
    }
}
