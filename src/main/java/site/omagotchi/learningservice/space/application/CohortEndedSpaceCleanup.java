package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;

/**
 * 기수 종료 시 그 기수의 실습실 배정을 해제한다 (CE-04, 명세 08 §2 4단계).
 *
 * <p>{@code SpaceCommandService}와 나눈 이유는 성격이 다르기 때문이다. 저쪽은 요청자
 * 권한을 검증하는 사용자 경로이고, 이쪽은 기수 종료라는 시스템 사건이 근거라 행위자가
 * 없다 — 권한 없는 Method가 행위자 기반 Service에 섞이면 실수로 노출되기 쉽다.</p>
 *
 * <p><b>이 해제를 빠뜨리면 실습실이 배정 상태로 남아</b> 다음 기수의 배정 요청이
 * {@code LAB_ALREADY_ASSIGNED}(409)로 막힌다. 반대로 실습실이 아닌 공간까지 해제하면
 * 관리 주체가 사라져 아무도 지울 수 없는 공간이 생긴다(RM-25) — 그 경계는 Persistence의
 * {@code LAB} 필터가 지킨다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEndedSpaceCleanup {

    private final SpaceRepository spaceRepository;

    /**
     * 배정 해제. 조건부 벌크 UPDATE라 두 번 호출해도 두 번째는 0건이다.
     *
     * @return 해제한 실습실 수. 기수당 여러 개일 수 있다 (RM-26)
     */
    @Transactional
    public int unassignLabs(Long cohortId) {
        int unassigned = spaceRepository.unassignLabsByCohort(cohortId);
        if (unassigned > 0) {
            log.info("기수 종료로 실습실 배정을 해제했습니다. cohortId={}, 해제={}개", cohortId, unassigned);
        }
        return unassigned;
    }
}
