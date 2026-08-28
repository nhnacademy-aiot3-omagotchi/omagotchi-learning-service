package site.omagotchi.learningservice.cohort.application.port;

import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;

import java.util.Optional;

/**
 * 가입 코드 영속성 경계.
 *
 * <p>application은 Spring Data 메서드와 flush 시점을 알지 않는다. 기존 ACTIVE 코드의
 * 폐기 반영 및 신규 코드 저장 순서와 유니크 제약 위반 변환은 infrastructure 구현이 맡는다.
 */
public interface JoinCodePersistence {

    Optional<CohortJoinCode> findLatestByCohortId(Long cohortId);

    Optional<CohortJoinCode> findActiveByCohortId(Long cohortId);

    Optional<CohortJoinCode> findByCodeHash(String codeHash);

    CohortJoinCode saveIssued(CohortJoinCode joinCode);

    void saveRevoked(CohortJoinCode joinCode);
}
