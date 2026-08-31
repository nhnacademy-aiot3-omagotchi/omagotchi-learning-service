package site.omagotchi.learningservice.cohort.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.application.port.JoinCodePersistence;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;

import java.util.Optional;

/**
 * 가입 코드 포트의 JPA 구현.
 *
 * <p>부분 유니크 인덱스 위반을 서비스 트랜잭션 안에서 변환할 수 있도록 쓰기는 즉시
 * flush한다. 만료된 ACTIVE 코드의 폐기도 신규 INSERT 전에 반영해 인덱스 자리를 비운다.
 */
@Repository
@RequiredArgsConstructor
public class JpaJoinCodePersistence implements JoinCodePersistence {

    private final CohortJoinCodeRepository repository;

    @Override
    public Optional<CohortJoinCode> findLatestByCohortId(Long cohortId) {
        return repository.findFirstByCohortIdOrderByIssuedAtDesc(cohortId);
    }

    @Override
    public Optional<CohortJoinCode> findActiveByCohortId(Long cohortId) {
        return repository.findFirstByCohortIdAndStatusOrderByIssuedAtDesc(
                cohortId,
                CohortJoinCodeStatus.ACTIVE
        );
    }

    @Override
    public Optional<CohortJoinCode> findByCodeHash(String codeHash) {
        return repository.findByCodeHash(codeHash);
    }

    @Override
    public CohortJoinCode saveIssued(CohortJoinCode joinCode) {
        try {
            return repository.saveAndFlush(joinCode);
        } catch (DataIntegrityViolationException exception) {
            throw JoinCodeConstraintTranslator.translate(exception);
        }
    }

    @Override
    public void saveRevoked(CohortJoinCode joinCode) {
        repository.saveAndFlush(joinCode);
    }

}
