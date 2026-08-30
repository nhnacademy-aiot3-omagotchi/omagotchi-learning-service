package site.omagotchi.learningservice.cohort.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("가입 코드 도메인 테스트")
class CohortJoinCodeTest {

    @Test
    @DisplayName("저장하는 시간값은 데이터베이스 정밀도인 마이크로초로 맞춘다")
    void normalizesPersistedTimestampsToMicroseconds() {
        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusDays(1)
                .withNano(123_456_789);

        CohortJoinCode joinCode = CohortJoinCode.issue(
                1L,
                "join-code-hash",
                expiresAt,
                UUID.randomUUID()
        );

        assertEquals(expiresAt.truncatedTo(ChronoUnit.MICROS), joinCode.getExpiresAt());
        assertEquals(0, joinCode.getIssuedAt().getNano() % 1_000);

        joinCode.revoke();

        assertEquals(0, joinCode.getRevokedAt().getNano() % 1_000);
    }
}
