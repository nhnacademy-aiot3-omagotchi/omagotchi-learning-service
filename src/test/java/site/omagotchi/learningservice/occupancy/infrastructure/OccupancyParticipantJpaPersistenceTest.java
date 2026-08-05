package site.omagotchi.learningservice.occupancy.infrastructure;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 참여자 Persistence 어댑터.
 *
 * <p>점유 시작 경로에서는 이 {@code save}가 두 번째 INSERT이므로, 여기서 터지는 유니크
 * 위반이 앞선 점유 INSERT까지 롤백시킨다. 즉 이 변환이 곧 "다른 회의에 참여 중인 사람의
 * 점유 요청"(MR-30)을 막는 유일한 검사다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OccupancyParticipantJpaPersistenceTest {

    private static final Long OCCUPANCY_ID = 100L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private OccupancyParticipantJpaRepository participantJpaRepository;

    @InjectMocks
    private OccupancyParticipantJpaPersistence occupancyParticipantJpaPersistence;

    @Test
    @DisplayName("저장은 flush까지 즉시 수행한다.")
    void test1() {
        OccupancyParticipant participant = participant();
        given(participantJpaRepository.saveAndFlush(participant)).willReturn(participant);

        assertThat(occupancyParticipantJpaPersistence.save(participant)).isSameAs(participant);

        verify(participantJpaRepository).saveAndFlush(participant);
        verify(participantJpaRepository, never()).save(any(OccupancyParticipant.class));
    }

    @Test
    @DisplayName("활성 참여 유니크 위반은 이미 참여 중 오류로 옮긴다.")
    void test2() {
        OccupancyParticipant participant = participant();
        given(participantJpaRepository.saveAndFlush(any(OccupancyParticipant.class)))
                .willThrow(violation("uq_occupancy_participants_one_active"));

        assertThatThrownBy(() -> occupancyParticipantJpaPersistence.save(participant))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(OccupancyErrorCode.ALREADY_PARTICIPATING));
    }

    @Test
    @DisplayName("점유당 사람 1행 유니크 위반도 이미 참여 중 오류로 옮긴다.")
    void test3() {
        OccupancyParticipant participant = participant();
        given(participantJpaRepository.saveAndFlush(any(OccupancyParticipant.class)))
                .willThrow(violation("uq_occupancy_participants_pair"));

        assertThatThrownBy(() -> occupancyParticipantJpaPersistence.save(participant))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(OccupancyErrorCode.ALREADY_PARTICIPATING));
    }

    @Test
    @DisplayName("모르는 무결성 위반은 감싸지 않고 그대로 전파한다.")
    void test4() {
        OccupancyParticipant participant = participant();
        DataIntegrityViolationException original = violation("fk_occupancy_participants_member");
        given(participantJpaRepository.saveAndFlush(any(OccupancyParticipant.class)))
                .willThrow(original);

        assertThatThrownBy(() -> occupancyParticipantJpaPersistence.save(participant))
                .isSameAs(original)
                .isNotInstanceOf(BusinessException.class);
    }

    /** 이탈은 삭제가 아니라 left_at 기록이므로 정원 카운트는 열린 행만 세야 한다 (MR-28, MR-32). */
    @Test
    @DisplayName("정원 카운트는 이탈하지 않은 참여자만 센다.")
    void test5() {
        given(participantJpaRepository.countByOccupancyIdAndLeftAtIsNull(OCCUPANCY_ID))
                .willReturn(3L);

        assertThat(occupancyParticipantJpaPersistence.countActiveByOccupancyId(OCCUPANCY_ID))
                .isEqualTo(3L);
    }

    private OccupancyParticipant participant() {
        return OccupancyParticipant.join(OCCUPANCY_ID, 10L, USER_ID, NOW);
    }

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException(
                "중복 키 위반",
                new ConstraintViolationException(
                        "duplicate key value violates unique constraint",
                        new SQLException("duplicate key", "23505"),
                        constraintName
                )
        );
    }
}
