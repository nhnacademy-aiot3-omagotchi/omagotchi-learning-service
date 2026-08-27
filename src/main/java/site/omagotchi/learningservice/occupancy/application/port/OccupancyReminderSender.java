package site.omagotchi.learningservice.occupancy.application.port;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 점유 만료 임박 알림의 실제 발송 경계 (MR-12).
 */
public interface OccupancyReminderSender {

    /**
     * @return 실제로 발송했으면 {@code true}, 수신자가 미연동이거나 알림을 꺼서 건너뛰었으면
     *         {@code false}
     */
    boolean sendExpiryReminder(ExpiryReminder reminder);

    /**
     * {@code occupancyId + expiresAt}은 한 만료 시점의 알림을 식별한다. 연장 뒤에는 같은
     * 점유라도 {@code expiresAt}이 달라져 다시 알림을 보내야 하므로 occupancyId만으로
     * 중복 제거해서는 안 된다.
     *
     * @param spaceId   공간 식별자. 사람이 읽는 문구는 {@code spaceName}을 쓴다
     * @param spaceName 사람이 읽는 공간 이름. 이름 조회 실패가 알림 자체를 막으면 안 된다
     */
    record ExpiryReminder(
            Long occupancyId,
            Long spaceId,
            String spaceName,
            UUID occupierUserId,
            OffsetDateTime expiresAt
    ) {
    }
}
