package site.omagotchi.learningservice.occupancy.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결과 → 응답 매핑.
 *
 * <p>점유자·참여자 정보를 담지 않는 것이 계약이다 (MR-36). 응답 레코드에 표시명이나
 * 계정 식별자가 추가되면 목록·상세 경로에서 타 기수 점유의 개인정보가 새어 나간다.</p>
 */
class RoomOccupancyResponseTest {

    private static final OffsetDateTime STARTED_AT =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));

    @Test
    @DisplayName("결과의 값을 그대로 옮긴다.")
    void test1() {
        RoomOccupancyResponse response = RoomOccupancyResponse.from(result());

        assertThat(response.occupancyId()).isEqualTo(100L);
        assertThat(response.spaceId()).isEqualTo(1L);
        assertThat(response.startedAt()).isEqualTo(STARTED_AT);
        assertThat(response.expiresAt()).isEqualTo(STARTED_AT.plusHours(2));
        assertThat(response.extensionCount()).isEqualTo(1);
        assertThat(response.remainingSeconds()).isEqualTo(1800L);
    }

    /** 상태는 enum이 아니라 이름 문자열로 나간다 — 응답 계약이 enum 순서에 묶이지 않는다. */
    @Test
    @DisplayName("상태는 이름 문자열로 내보낸다.")
    void test2() {
        assertThat(RoomOccupancyResponse.from(result()).status()).isEqualTo("ACTIVE");
    }

    /**
     * 남은 시간을 함께 내려주는 이유를 고정한다. expiresAt만 주면 단말 시계가 틀어졌을 때
     * 남은 시간이 어긋난다.
     */
    @Test
    @DisplayName("남은 시간은 결과가 계산한 값을 그대로 쓴다.")
    void test3() {
        RoomOccupancyResult result = new RoomOccupancyResult(
                100L, 1L, OccupancyStatus.ACTIVE,
                STARTED_AT, STARTED_AT.plusHours(2), 0, 0L
        );

        assertThat(RoomOccupancyResponse.from(result).remainingSeconds()).isZero();
    }

    private RoomOccupancyResult result() {
        return new RoomOccupancyResult(
                100L,
                1L,
                OccupancyStatus.ACTIVE,
                STARTED_AT,
                STARTED_AT.plusHours(2),
                1,
                1800L
        );
    }
}
