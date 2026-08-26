package site.omagotchi.learningservice.team.application.port;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 팀 알림의 실제 발송 경계 (GR-19).
 *
 * <p>{@code VacancyAlertSender}와 계약이 같다 — 정상 반환은 <b>실제 발송 성공</b>을 뜻하고
 * 실패는 예외로 알린다. 구현이 없으면 발송을 시도하지 않는다.</p>
 *
 * <p><b>전달 보장은 at-most-once다.</b> 통보 대상인 {@code team_members} 행을 <b>지운 뒤</b>에
 * 보내므로 실패하면 끝이다 — 재시도할 원천이 남지 않는다 (명세 06 §5 "해체 통보 실패 →
 * 해체는 유지, 통보 실패 로깅"). 실패를 예외로 알리는 계약은 같지만, 호출부가 그것으로
 * 할 수 있는 일은 기록뿐이다.</p>
 */
public interface TeamNotificationSender {

    void sendDisbandNotice(DisbandNotice notice);

    /**
     * 해체 통보 한 건.
     *
     * @param teamId          해체된 팀. 사람이 읽는 문구는 {@code teamName}을 쓰고, 이 값은
     *                        로깅·멱등 판단처럼 식별이 필요한 곳에 쓴다
     * @param teamName        사람이 읽는 팀 이름. 해체 시점 스냅샷이다
     * @param recipientUserId 받는 사람. 팀원은 멤버십 단위지만 발송은 계정으로 나간다
     * @param disbandedAt     해체 시각. 발송이 늦어도 정본은 이 값이다
     */
    record DisbandNotice(
            Long teamId,
            String teamName,
            UUID recipientUserId,
            OffsetDateTime disbandedAt
    ) {
    }
}
