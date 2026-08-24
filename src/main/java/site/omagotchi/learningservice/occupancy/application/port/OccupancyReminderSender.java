package site.omagotchi.learningservice.occupancy.application.port;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 점유 만료 임박 알림의 실제 발송 경계 (MR-12).
 *
 * <p>정상 반환은 실제 발송 성공을 뜻하고, 실패는 예외로 알려야 한다. 이 계약의 구현이
 * 없는 동안 스케줄러는 알림 후보를 소진하지 않으며 {@code reminder_sent_at}도 기록하지
 * 않는다. Spring 이벤트의 단순 발행은 실제 전달 성공이 아니므로 이 계약을 대신할 수 없다.</p>
 */
public interface OccupancyReminderSender {

    void sendExpiryReminder(ExpiryReminder reminder);

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
