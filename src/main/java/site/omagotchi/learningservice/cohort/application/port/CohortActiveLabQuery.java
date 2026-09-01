package site.omagotchi.learningservice.cohort.application.port;

/**
 * 기수 활성화에 필요한 공간 조건을 cohort가 소유하는 조회 경계.
 *
 * <p>구현은 공간 데이터를 소유한 space infrastructure에 둬 feature 의존 방향을
 * {@code space -> cohort}로 유지한다.</p>
 */
public interface CohortActiveLabQuery {

    boolean existsActiveLab(Long cohortId);
}
