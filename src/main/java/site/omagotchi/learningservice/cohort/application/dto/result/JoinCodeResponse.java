package site.omagotchi.learningservice.cohort.application.dto.result;

import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;

import java.time.OffsetDateTime;

/**
 * 가입 코드 원문을 제외한 가입 코드 상태 조회 결과
 */
public record JoinCodeResponse(
        Long cohortId,
        CohortJoinCodeStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime issuedAt,
        OffsetDateTime revokedAt
) {

    public static JoinCodeResponse from(CohortJoinCode joinCode) {
        return new JoinCodeResponse(
                joinCode.getCohortId(),
                joinCode.getStatus(),
                joinCode.getExpiresAt(),
                joinCode.getIssuedAt(),
                joinCode.getRevokedAt()
        );
    }
}
