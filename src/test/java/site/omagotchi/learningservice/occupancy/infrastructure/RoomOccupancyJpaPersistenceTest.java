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
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Persistence 어댑터의 두 가지 책임 — flush 시점 고정과 인덱스 위반 변환.
 *
 * <p>둘 다 이 클래스가 없으면 성립하지 않는다. flush가 커밋 시점으로 밀리면 유니크 위반이
 * 이 메서드 밖에서 터져 409가 아니라 500이 된다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RoomOccupancyJpaPersistenceTest {

    private static final Long SPACE_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private RoomOccupancyJpaRepository occupancyJpaRepository;

    @InjectMocks
    private RoomOccupancyJpaPersistence roomOccupancyJpaPersistence;

    /**
     * {@code save}가 아니라 {@code saveAndFlush}인 것이 계약의 일부다. 이 테스트가 깨지면
     * 유니크 위반이 어댑터 밖에서 터져 아래 변환 경로가 통째로 무력화된다.
     */
    @Test
    @DisplayName("저장은 flush까지 즉시 수행한다.")
    void test1() {
        RoomOccupancy occupancy = occupancy();
        given(occupancyJpaRepository.saveAndFlush(occupancy)).willReturn(occupancy);

        assertThat(roomOccupancyJpaPersistence.save(occupancy)).isSameAs(occupancy);

        verify(occupancyJpaRepository).saveAndFlush(occupancy);
        verify(occupancyJpaRepository, never()).save(any(RoomOccupancy.class));
    }

    @Test
    @DisplayName("회의실 활성 유니크 위반은 409로 옮긴다.")
    void test2() {
        RoomOccupancy occupancy = occupancy();
        given(occupancyJpaRepository.saveAndFlush(any(RoomOccupancy.class)))
                .willThrow(violation("uq_room_occupancies_one_active_per_space"));

        assertThatThrownBy(() -> roomOccupancyJpaPersistence.save(occupancy))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(OccupancyErrorCode.ROOM_ALREADY_OCCUPIED));
    }

    @Test
    @DisplayName("계정 활성 유니크 위반은 409로 옮긴다.")
    void test3() {
        RoomOccupancy occupancy = occupancy();
        given(occupancyJpaRepository.saveAndFlush(any(RoomOccupancy.class)))
                .willThrow(violation("uq_room_occupancies_one_active_per_user"));

        assertThatThrownBy(() -> roomOccupancyJpaPersistence.save(occupancy))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(OccupancyErrorCode.ALREADY_OCCUPYING));
    }

    @Test
    @DisplayName("모르는 무결성 위반은 감싸지 않고 그대로 전파한다.")
    void test4() {
        RoomOccupancy occupancy = occupancy();
        DataIntegrityViolationException original = violation("fk_room_occupancies_occupier");
        given(occupancyJpaRepository.saveAndFlush(any(RoomOccupancy.class))).willThrow(original);

        assertThatThrownBy(() -> roomOccupancyJpaPersistence.save(occupancy))
                .isSameAs(original)
                .isNotInstanceOf(BusinessException.class);
    }

    /** "행이 있다"와 "사용 중이다"는 다르다 — 종료 상태의 행도 이력으로 남는다. */
    @Test
    @DisplayName("존재 확인은 ACTIVE 상태로 좁혀 위임한다.")
    void test5() {
        given(occupancyJpaRepository.existsBySpaceIdAndStatus(SPACE_ID, OccupancyStatus.ACTIVE))
                .willReturn(true);
        given(occupancyJpaRepository.existsByOccupierUserIdAndStatus(USER_ID, OccupancyStatus.ACTIVE))
                .willReturn(false);

        assertThat(roomOccupancyJpaPersistence.existsActiveBySpaceId(SPACE_ID)).isTrue();
        assertThat(roomOccupancyJpaPersistence.existsActiveByUserId(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("활성 점유 조회도 ACTIVE 상태로 좁혀 위임한다.")
    void test6() {
        RoomOccupancy occupancy = occupancy();
        given(occupancyJpaRepository.findBySpaceIdAndStatus(SPACE_ID, OccupancyStatus.ACTIVE))
                .willReturn(Optional.of(occupancy));

        assertThat(roomOccupancyJpaPersistence.findActiveBySpaceId(SPACE_ID)).contains(occupancy);
    }

    @Test
    @DisplayName("만료 정리는 ACTIVE를 EXPIRED로 옮기도록 위임한다.")
    void test7() {
        given(occupancyJpaRepository.expireStaleBySpaceId(
                SPACE_ID, NOW, OccupancyStatus.ACTIVE, OccupancyStatus.EXPIRED)).willReturn(1);
        given(occupancyJpaRepository.expireStaleByUserId(
                USER_ID, NOW, OccupancyStatus.ACTIVE, OccupancyStatus.EXPIRED)).willReturn(2);

        assertThat(roomOccupancyJpaPersistence.expireStaleBySpaceId(SPACE_ID, NOW)).isEqualTo(1);
        assertThat(roomOccupancyJpaPersistence.expireStaleByUserId(USER_ID, NOW)).isEqualTo(2);
    }

    private RoomOccupancy occupancy() {
        return RoomOccupancy.start(SPACE_ID, 10L, USER_ID, NOW, NOW.plusHours(2));
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
