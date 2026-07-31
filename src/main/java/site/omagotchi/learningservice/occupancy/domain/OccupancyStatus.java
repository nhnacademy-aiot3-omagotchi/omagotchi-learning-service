package site.omagotchi.learningservice.occupancy.domain;

/**
 * 점유 세션 상태. 종료 3종은 최종 상태이며 재전이가 없다 (명세서 02).
 *
 * <p>행은 종료 후에도 이력으로 보존한다 — 통계 원천이다. 삭제하면 안 된다.</p>
 *
 * <p>{@code ck_room_occupancies_end}가 {@code (status = 'ACTIVE') = (ended_at IS NULL)}을
 * 강제한다. 상태를 바꾸는 모든 전이는 {@code endedAt}을 반드시 함께 세팅해야 한다.</p>
 */
public enum OccupancyStatus {

    /** 사용 중. 부분 유니크 2종이 이 값에만 걸린다. */
    ACTIVE,

    /** 점유자 수동 반납 (MR-14). 공실 알림 발송 대상이다. */
    RELEASE,

    /** expires_at 경과 (스케줄러 #9). 공실 알림 발송 대상이다. */
    EXPIRED,

    /** 기수 매니저 강제 종료 (MR-21). 공실 알림을 발송하지 않는다. */
    FORCE_RELEASE
}
