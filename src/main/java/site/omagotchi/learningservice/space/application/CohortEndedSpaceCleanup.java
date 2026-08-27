package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;

/**
 * 기수 종료 시 그 기수가 관리하던 공간의 관리 주체를 해제한다 (CE-04, 명세 08 §2 4단계).
 *
 * <p>{@code SpaceCommandService}와 나눈 이유는 성격이 다르기 때문이다. 저쪽은 요청자
 * 권한을 검증하는 사용자 경로이고, 이쪽은 기수 종료라는 시스템 사건이 근거라 행위자가
 * 없다 — 권한 없는 Method가 행위자 기반 Service에 섞이면 실수로 노출되기 쉽다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEndedSpaceCleanup {

    private final SpaceRepository spaceRepository;

    /**
     * 관리 주체 해제. 조건부 벌크 UPDATE라 두 번 호출해도 두 번째는 0건이다.
     *
     * @return 해제한 공간 수. 기수당 여러 개일 수 있다 (RM-26)
     */
    @Transactional
    public int unassignSpaces(Long cohortId) {
        int unassigned = spaceRepository.unassignByCohort(cohortId);
        if (unassigned > 0) {
            log.info("기수 종료로 공간 관리 주체를 해제했습니다. cohortId={}, 해제={}개",
                    cohortId, unassigned);
        }
        return unassigned;
    }
}
