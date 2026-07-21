package site.omagotchi.learningservice.cohort.application.dto.result;

import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;

import java.time.OffsetDateTime;

/**
 * 새로 발급된 가입 코드와 메타데이터 응답
 */
public record IssuedJoinCodeResponse(
        Long cohortId,
        String code,
        CohortJoinCodeStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime issuedAt
) {

    public static IssuedJoinCodeResponse from(CohortJoinCode joinCode, String code) {
        return new IssuedJoinCodeResponse(
                joinCode.getCohortId(),
                code,
                joinCode.getStatus(),
                joinCode.getExpiresAt(),
                joinCode.getIssuedAt()
        );
    }
}
