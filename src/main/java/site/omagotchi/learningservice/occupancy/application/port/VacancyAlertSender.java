package site.omagotchi.learningservice.occupancy.application.port;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 공실 알림의 실제 발송 경계 (MR-03).
 *
 * <p>{@link OccupancyReminderSender}와 계약이 같다 — 정상 반환은 <b>실제 발송 성공</b>을
 * 뜻하고 실패는 예외로 알린다. 구현이 없는 동안 신청은 소진되지 않으며, 그래야 나중에
 * 발송 수단이 붙었을 때 대기자가 그대로 남아 있다.</p>
 *
 * <p><b>만료 임박 알림과 Port를 나눈 이유</b>는 수신자 결정 방식이 다르기 때문이다.
 * 저쪽은 점유자 한 명이 대상이라 발송 단위가 곧 점유지만, 이쪽은 한 번의 공실에
 * 신청자 N명이 대상이고 각자가 독립적으로 성공·실패한다. 한 Port에 합치면 부분 실패를
 * 표현할 수 없다.</p>
 */
public interface VacancyAlertSender {

    void sendVacancyAlert(VacancyNotice notice);

    /**
     * 공간 비활성화로 신청이 삭제됐음을 알린다 (RM-15).
     *
     * <p>같은 Port에 둔 이유는 채널도 수신자 개념도 같기 때문이다 — 둘 다 "신청자에게
     * 그 신청에 관해" 보낸다. 발송 수단이 없으면 둘 다 나가지 않는 것도 같다.</p>
     *
     * <p><b>전달 보장은 다르다.</b> 공실 알림은 실패해도 신청이 대기로 남아 다음 공실에
     * 다시 잡히지만(at-least-once), 이 통보는 대상 행을 <b>지운 뒤</b>에 보내므로 실패하면
     * 끝이다 (at-most-once, 명세 04 §4). 실패를 예외로 알리는 계약은 같지만, 호출부가
     * 그것으로 할 수 있는 일은 기록뿐이다.</p>
     */
    void sendDiscardNotice(DiscardNotice notice);

    /**
     * 발송 한 건.
     *
     * <p>점유자·참여자 정보를 담지 않는 것이 의도다 (MR-36). 회의실은 여러 기수가
     * 공유하므로, 본문에 "누가 쓰던 방인지"가 들어가면 타 기수 사용자의 개인정보가
     * 신청자에게 노출된다.</p>
     *
     * @param alertId       소진 대상 신청. 구현이 멱등 키로 쓸 수 있다
     * @param spaceId       공간 식별자. 사람이 읽는 문구는 {@code spaceName}을 쓰고, 이 값은
     *                      로깅·멱등 판단처럼 식별이 필요한 곳에 쓴다
     * @param spaceName     사람이 읽는 공간 이름. 조회 시점 스냅샷이라 그 사이 이름이 바뀌어도
     *                      발송을 막지 않는다 — 이름 조회 실패가 알림 자체를 막으면 안 된다
     * @param recipientUserId 받는 사람. 신청은 멤버십 단위지만 발송은 계정으로 나간다
     * @param vacatedAt     비워진 시각. 발송이 늦어도 정본은 이 값이다
     */
    /**
     * 삭제 통보 한 건.
     *
     * <p>왜 사라졌는지를 담는다. 공간이 비활성화됐다는 사실을 빼면 사용자는 자기 신청이
     * 사라진 이유를 알 수 없고, 다시 신청하려다 400을 받는다.</p>
     */
    record DiscardNotice(
            Long spaceId,
            String spaceName,
            UUID recipientUserId,
            OffsetDateTime discardedAt
    ) {
    }

    record VacancyNotice(
            Long alertId,
            Long spaceId,
            String spaceName,
            UUID recipientUserId,
            OffsetDateTime vacatedAt
    ) {
    }
}
