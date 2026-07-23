package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.command.IssueJoinCodeRequest;
import site.omagotchi.learningservice.cohort.application.dto.result.IssuedJoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.dto.result.JoinCodeResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortJoinCodeRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JoinCodeService {

    private static final int CODE_BYTE_LENGTH = 16;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final CohortRepository cohortRepository;
    private final CohortJoinCodeRepository joinCodeRepository;
    private final CohortAccessService accessService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 특정 기수의 현재 ACTIVE 가입 코드 메타데이터를 조회한다.
     * 원문 코드는 저장하지 않으므로 응답에도 포함하지 않는다.
     */
    public JoinCodeResponse getActiveJoinCode(Long cohortId, Long actorUserId) {
        accessService.requireManager(cohortId, actorUserId);

        CohortJoinCode joinCode = joinCodeRepository
                .findFirstByCohortIdAndStatusOrderByIssuedAtDesc(cohortId, CohortJoinCodeStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.JOIN_CODE_NOT_FOUND));

        return JoinCodeResponse.from(joinCode);
    }

    /**
     * 가입 코드를 발급하거나 재발급한다.
     * 기존 ACTIVE 코드는 폐기하고, 새 원문 코드는 이 응답에서만 1회 반환한다.
     */
    @Transactional
    public IssuedJoinCodeResponse issue(Long cohortId, IssueJoinCodeRequest request, Long issuedByUserId) {
        accessService.requireManager(cohortId, issuedByUserId);

        Cohort cohort = getCohortOrThrow(cohortId);
        validateIssuable(cohort, request.expiresAt());

        joinCodeRepository.findFirstByCohortIdAndStatusOrderByIssuedAtDesc(
                cohortId,
                CohortJoinCodeStatus.ACTIVE
        ).ifPresent(CohortJoinCode::revoke);

        String rawCode = generateRawCode();
        CohortJoinCode joinCode = CohortJoinCode.issue(
                cohortId,
                JoinCodeHash.sha256(rawCode),
                request.expiresAt(),
                issuedByUserId
        );

        CohortJoinCode savedJoinCode = joinCodeRepository.save(joinCode);
        return IssuedJoinCodeResponse.from(savedJoinCode, rawCode);
    }

    /**
     * 특정 기수의 현재 ACTIVE 가입 코드를 폐기한다.
     * 폐기 이후 해당 코드는 참가 신청에 사용할 수 없다.
     */
    @Transactional
    public JoinCodeResponse revoke(Long cohortId, Long actorUserId) {
        accessService.requireManager(cohortId, actorUserId);

        CohortJoinCode joinCode = joinCodeRepository
                .findFirstByCohortIdAndStatusOrderByIssuedAtDesc(cohortId, CohortJoinCodeStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.JOIN_CODE_NOT_FOUND));

        joinCode.revoke();
        return JoinCodeResponse.from(joinCode);
    }

    private Cohort getCohortOrThrow(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }

    private void validateIssuable(Cohort cohort, OffsetDateTime expiresAt) {
        if (cohort.getStatus() == CohortStatus.CLOSED) {
            throw new BusinessException(CohortErrorCode.COHORT_ALREADY_CLOSED);
        }
        if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new BusinessException(CohortErrorCode.JOIN_CODE_EXPIRES_AT_INVALID);
        }
    }

    private String generateRawCode() {
        byte[] bytes = new byte[CODE_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes).toUpperCase();
    }
}
