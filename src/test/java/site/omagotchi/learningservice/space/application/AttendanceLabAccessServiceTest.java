package site.omagotchi.learningservice.space.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.PresenceSpaceQueryService;
import site.omagotchi.learningservice.attendance.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("출결 실습실 선택 공간 정책")
class AttendanceLabAccessServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private PresenceSpaceQueryService presenceSpaceQueryService;

    @InjectMocks
    private AttendanceLabAccessService service;

    @Test
    @DisplayName("자기 기수 활성 LAB에 자리가 있으면 잠금 후 선택을 승인한다")
    void approvesSelectableLabAfterLock() {
        when(spaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space(
                10L, 42L, SpaceType.LAB, SpaceOperationalStatus.ACTIVE, 3, false)));
        when(presenceSpaceQueryService.summarize(10L))
                .thenReturn(new SpacePresenceSummary(1L, 1L));

        service.requireSelectableLab(42L, 100L, 10L);

        verify(spaceRepository).findByIdForUpdate(10L);
        verify(presenceSpaceQueryService).isReserved(10L, 100L);
    }

    @Test
    @DisplayName("유효하지 않은 공간 ID는 Space 입력 오류로 거절한다")
    void rejectsInvalidSpaceId() {
        assertError(SpaceErrorCode.INVALID_SPACE_ID,
                () -> service.requireSelectableLab(42L, 100L, null));

        verify(spaceRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("타 기수 LAB은 선택할 수 없다")
    void rejectsLabAssignedToDifferentCohort() {
        when(spaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space(
                10L, 99L, SpaceType.LAB, SpaceOperationalStatus.ACTIVE, 3, false)));

        assertError(SpaceErrorCode.LAB_NOT_SELECTABLE,
                () -> service.requireSelectableLab(42L, 100L, 10L));

        verify(presenceSpaceQueryService, never()).summarize(10L);
    }

    @Test
    @DisplayName("비활성·삭제·LAB 아닌 공간은 선택할 수 없다")
    void rejectsUnavailableOrNonLabSpace() {
        when(spaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space(
                10L, 42L, SpaceType.MEETING, SpaceOperationalStatus.ACTIVE, 3, false)));

        assertError(SpaceErrorCode.LAB_NOT_SELECTABLE,
                () -> service.requireSelectableLab(42L, 100L, 10L));
    }

    @Test
    @DisplayName("현재 체류와 복귀 예약을 합친 정원이 가득 차면 선택을 거절한다")
    void rejectsFullLab() {
        when(spaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space(
                10L, 42L, SpaceType.LAB, SpaceOperationalStatus.ACTIVE, 2, false)));
        when(presenceSpaceQueryService.summarize(10L))
                .thenReturn(new SpacePresenceSummary(1L, 1L));
        when(presenceSpaceQueryService.isReserved(10L, 100L)).thenReturn(false);

        assertError(SpaceErrorCode.LAB_CAPACITY_EXCEEDED,
                () -> service.requireSelectableLab(42L, 100L, 10L));
    }

    @Test
    @DisplayName("이미 그 LAB 좌석에 포함된 이동 재요청은 정원이 가득 차도 허용한다")
    void allowsIdempotentSelectionWhenAlreadyReserved() {
        when(spaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space(
                10L, 42L, SpaceType.LAB, SpaceOperationalStatus.ACTIVE, 2, false)));
        when(presenceSpaceQueryService.summarize(10L))
                .thenReturn(new SpacePresenceSummary(1L, 1L));
        when(presenceSpaceQueryService.isReserved(10L, 100L)).thenReturn(true);

        service.requireSelectableLab(42L, 100L, 10L);
    }

    private Space space(
            Long id,
            Long cohortId,
            SpaceType type,
            SpaceOperationalStatus status,
            int capacity,
            boolean deleted
    ) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return Space.restore(
                id,
                cohortId,
                "공간 " + id,
                type,
                capacity,
                status,
                status == SpaceOperationalStatus.INACTIVE ? "점검" : null,
                now.minusDays(1),
                now,
                deleted ? now : null
        );
    }

    private void assertError(
            SpaceErrorCode code,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call
    ) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isSameAs(code));
    }
}
