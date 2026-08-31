package site.omagotchi.learningservice.cohort.application.result;

import java.util.List;
import java.util.UUID;

/** 관리자 사용자 목록 화면의 "기수 운영 권한" 컬럼을 채우기 위한 사용자 단위 결과다. */
public record UserManagedCohortsResult(
        UUID userId,
        List<ManagedCohortResult> cohorts
) {

    public UserManagedCohortsResult {
        cohorts = cohorts == null ? List.of() : List.copyOf(cohorts);
    }
}
