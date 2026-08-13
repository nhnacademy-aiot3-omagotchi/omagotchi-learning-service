package site.omagotchi.learningservice.team.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository.MembershipRef;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 스윕 루프가 소유한 판단 — 무엇을 정리 대상으로 보는가, 실패를 어떻게 다루는가,
 * 커서를 어떻게 전진시키는가.
 *
 * <p>정리 규칙 자체는 {@code TeamMasterService}의 것이고 {@code TeamMasterServiceTest}와
 * {@code EndedMembershipSweepIT}가 검증한다. 여기서는 그 Method를 <b>언제 부르는지</b>만 본다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("고아 팀원 정합성 스윕")
class EndedMembershipSweepTest {

    private static final int BATCH = 3;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private TeamMasterService teamMasterService;

    @InjectMocks
    private EndedMembershipSweep endedMembershipSweep;

    /**
     * 살아 있는 소속을 지우면 안 된다.
     *
     * <p>이 스윕의 최악 실패는 정리를 못 하는 것이 아니라 <b>정상 팀원을 지우는 것</b>이다.
     * 판정을 기수 파트에 위임한 결과만 신뢰하고, 조회 결과에 없는 것은 건드리지 않는다.</p>
     */
    @Test
    @DisplayName("비활성 소속만 정리하고 활성 소속은 건드리지 않는다")
    void cleansOnlyInactiveMemberships() {
        given(teamMemberRepository.findMembershipRefsAfter(0L, BATCH))
                .willReturn(List.of(ref(1L, 11L), ref(2L, 22L)));
        given(cohortMembershipQueryService.findInactiveMembershipIds(any()))
                .willReturn(Set.of(22L));

        endedMembershipSweep.sweep(BATCH);

        verify(teamMasterService).removeEndedMember(22L);
        verify(teamMasterService, never()).removeEndedMember(11L);
    }

    /**
     * 기수 조회가 비면 아무것도 지우지 않는다.
     *
     * <p>{@code findInactiveMembershipIds}가 "비활성인 것"을 돌려주는 계약이라 성립하는
     * 성질이다. "활성인 것"을 받아 여집합을 계산하는 구조였다면 같은 상황에서
     * <b>전부가 정리 대상</b>이 됐다.</p>
     */
    @Test
    @DisplayName("정리 대상이 없으면 정리를 호출하지 않는다")
    void doesNotCleanWhenNothingIsInactive() {
        given(teamMemberRepository.findMembershipRefsAfter(0L, BATCH))
                .willReturn(List.of(ref(1L, 11L)));
        given(cohortMembershipQueryService.findInactiveMembershipIds(any()))
                .willReturn(Set.of());

        assertThat(endedMembershipSweep.sweep(BATCH)).isZero();

        verifyNoInteractions(teamMasterService);
    }

    /**
     * 한 건의 실패가 나머지를 막지 않는다 (ADR 0013 §6).
     *
     * <p>실패한 행은 그대로 남아 다음 주기에 다시 조회되므로 별도의 실패 상태 기록이
     * 필요 없다 — 이것이 아웃박스를 두지 않은 근거이기도 하다.</p>
     */
    @Test
    @DisplayName("한 대상 정리가 실패해도 나머지를 계속 처리한다")
    void continuesAfterSingleFailure() {
        given(teamMemberRepository.findMembershipRefsAfter(0L, BATCH))
                .willReturn(List.of(ref(1L, 11L), ref(2L, 22L)));
        given(cohortMembershipQueryService.findInactiveMembershipIds(any()))
                .willReturn(Set.of(11L, 22L));
        given(teamMasterService.removeEndedMember(11L))
                .willThrow(new IllegalStateException("락 충돌"));
        given(teamMasterService.removeEndedMember(22L)).willReturn(true);

        // 실패 1건은 결과에서 빠지고 예외는 밖으로 나오지 않는다.
        assertThat(endedMembershipSweep.sweep(BATCH)).isEqualTo(1);

        verify(teamMasterService).removeEndedMember(22L);
    }

    /**
     * 배치가 가득 차면 커서를 전진시켜 다음 배치를 읽는다.
     *
     * <p>조회 대상이 "고아"가 아니라 전체 소속 행이라 {@code LIMIT}만으로는 앞쪽 배치를
     * 반복해서 보게 된다 — 커서가 없으면 뒤쪽 행에 영원히 닿지 못한다.</p>
     */
    @Test
    @DisplayName("배치가 가득 차면 마지막 id를 커서로 다음 배치를 읽는다")
    void advancesCursorWhenBatchIsFull() {
        given(teamMemberRepository.findMembershipRefsAfter(0L, BATCH))
                .willReturn(List.of(ref(1L, 11L), ref(2L, 22L), ref(3L, 33L)));
        given(teamMemberRepository.findMembershipRefsAfter(3L, BATCH))
                .willReturn(List.of(ref(4L, 44L)));
        given(cohortMembershipQueryService.findInactiveMembershipIds(any()))
                .willReturn(Set.of());

        endedMembershipSweep.sweep(BATCH);

        verify(teamMemberRepository).findMembershipRefsAfter(0L, BATCH);
        verify(teamMemberRepository).findMembershipRefsAfter(3L, BATCH);
    }

    /**
     * 배치가 덜 찼으면 마지막 배치이므로 더 읽지 않는다.
     */
    @Test
    @DisplayName("배치가 덜 차면 순회를 멈춘다")
    void stopsWhenBatchIsNotFull() {
        given(teamMemberRepository.findMembershipRefsAfter(0L, BATCH))
                .willReturn(List.of(ref(1L, 11L)));
        given(cohortMembershipQueryService.findInactiveMembershipIds(any()))
                .willReturn(Set.of());

        endedMembershipSweep.sweep(BATCH);

        verify(teamMemberRepository, times(1)).findMembershipRefsAfter(anyLong(), anyInt());
    }

    /**
     * 소속 유효성 조회는 배치당 1회다.
     *
     * <p>건별로 물으면 그대로 N+1이 되고, 기수 모듈이 분리되면 N+1 원격 호출이 된다.</p>
     */
    @Test
    @DisplayName("소속 유효성 조회는 배치당 한 번만 호출한다")
    void queriesMembershipStatusOncePerBatch() {
        given(teamMemberRepository.findMembershipRefsAfter(0L, BATCH))
                .willReturn(List.of(ref(1L, 11L), ref(2L, 22L), ref(3L, 33L)));
        given(teamMemberRepository.findMembershipRefsAfter(3L, BATCH))
                .willReturn(List.of());
        given(cohortMembershipQueryService.findInactiveMembershipIds(
                eq(List.of(11L, 22L, 33L))))
                .willReturn(Set.of());

        endedMembershipSweep.sweep(BATCH);

        verify(cohortMembershipQueryService, times(1)).findInactiveMembershipIds(any());
    }

    private static MembershipRef ref(Long teamMemberId, Long cohortMembershipId) {
        return new MembershipRef(teamMemberId, cohortMembershipId);
    }
}
