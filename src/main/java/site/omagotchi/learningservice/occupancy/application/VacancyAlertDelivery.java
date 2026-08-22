package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 공실 알림 한 건을 잠그고 실제 발송 성공 뒤에만 소진 처리한다 (MR-03, MR-16).
 *
 * <p><b>별도 Component인 것이 이 Class의 존재 이유다.</b> 명세 04 §5가 "신청자 5명 중 2명
 * 발송 실패 → 성공 3건만 소진"을 요구하는데, 같은 Class 안에서 호출하면 Spring Proxy를
 * 거치지 않아 {@code @Transactional}이 적용되지 않는다 — 결국 한 건의 실패가 앞서 성공한
 * 소진 기록까지 되돌린다 ({@code OccupancyExpiration}과 같은 이유).</p>
 *
 * <p>{@code REQUIRES_NEW}인 것도 같다. 호출부는 {@code AFTER_COMMIT} 리스너라 트랜잭션
 * 밖에서 부르는 것이 정상이지만, {@code REQUIRED}면 나중에 누가 트랜잭션 안에서 불렀을 때
 * 조용히 합류해 건별 격리가 사라진다. 명세 §4가 <b>"누락하면 {@code notified_at} 기록이
 * 조용히 유실된다"</b>고 못박은 지점이다.</p>
 *
 * <p><b>수신자를 여기서 조회하지 않는다.</b> 건별 Transaction 안에서 되물으면 대기자가
 * N명일 때 기수 조회도 N번이 되어, {@code CohortMembershipQueryService.findUserIds}가
 * 계약으로 정한 배치 성질이 깨진다. 격리는 Transaction 경계가 만드는 것이지 조회 위치가
 * 만드는 것이 아니므로, 조회는 Dispatcher가 한 번에 끝내고 결과만 넘긴다.</p>
 *
 * <p><b>방이 다시 점유됐는지 재확인하지 않는다</b> (MR-04). "알림 ≠ 사용 권한 보장"이
 * 정책이므로, 발송 직전에 다시 조회해 걸러내면 오히려 아무도 알림을 받지 못하는 구간이
 * 생긴다 — 만료 임박 알림이 연장 여부를 재확인하는 것과 반대인데, 저쪽은 <b>틀린 시각</b>을
 * 알리는 문제라 성격이 다르다.</p>
 *
 * <p><b>전달 보장은 at-least-once다.</b> 외부 발송과 {@code notified_at} 기록은 하나의
 * 원자적 Transaction이 아니다. 발송은 성공했는데 이 Transaction의 커밋이 실패하면 신청이
 * 대기로 남아, 다음 공실에 <b>같은 사람에게 다시 발송된다.</b> 다중 Instance의 정상 경로는
 * 행 잠금과 대기 여부 재확인으로 직렬화되지만, 이 실패 구간은 그것으로 덮이지 않는다
 * ({@code OccupancyExpiryReminder}와 같은 성질이다).</p>
 *
 * <p><b>중복을 감수하는 것이 의도된 선택이다.</b> 순서를 뒤집어 먼저 소진시키면 반대쪽
 * 실패가 생긴다 — 발송이 실패한 사람의 신청만 사라져 <b>알림을 받지도 못한 채 대기에서
 * 빠진다.</b> 중복 안내는 성가실 뿐이고 MR-04가 이미 "알림이 사용을 보장하지 않는다"고
 * 정해 두었으므로 잘못된 정보도 아니지만, 유실은 사용자가 영영 알 수 없다.</p>
 *
 * <p>exactly-once로 만들려면 sender가 {@code alertId}를 멱등 키로 받아야 하는데 Telegram에는
 * 그런 장치가 없어 "보낸 알림" 저장소를 따로 두거나 outbox를 도입해야 한다. 더 큰 문제였던
 * 팀 정리 유실에도 outbox 대신 정합성 스윕을 택했고(ADR space-team/0013), 알림 종류가
 * 늘어나면 발송 계약 자체를 {@code telegram} Feature로 합칠 예정이라 전달 보장은 그때 한
 * 곳에서 설계한다.</p>
 */
@Component
@RequiredArgsConstructor
public class VacancyAlertDelivery {

    private final VacancyAlertRepository alertRepository;
    private final Clock clock;

    /**
     * 신청 한 건에 알림을 보내고 소진시킨다.
     *
     * @param alertId         {@code findWaitingBySpaceId}가 돌려준 후보. 조회 시점의 스냅샷이라
     *                        지금도 대기 중이라는 보장은 없다 — 그 판정은 잠금 뒤에 한다
     * @param spaceName       호출부가 미리 조회한 공간 이름. 여기서 다시 조회하면 대기자 수만큼
     *                        이름 조회가 반복돼 {@code VacancyAlertDispatcher}가 지키는 배치
     *                        성질이 깨진다
     * @param recipientUserId 호출부가 미리 확정한 수신자
     * @param vacatedAt       비워진 시각. 발송이 늦어도 이 값이 정본이다
     * @return 이번 호출로 발송·소진됐으면 {@code true}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean send(
            Long alertId,
            Long spaceId,
            String spaceName,
            UUID recipientUserId,
            OffsetDateTime vacatedAt,
            VacancyAlertSender sender
    ) {
        // 후보 조회 뒤 취소되었거나 다른 Instance가 이미 발송했으면 대상이 아니다.
        VacancyAlert alert = alertRepository.lockWaitingById(alertId).orElse(null);
        if (alert == null) {
            return false;
        }

        sender.sendVacancyAlert(new VacancyAlertSender.VacancyNotice(
                alert.getId(), spaceId, spaceName, recipientUserId, vacatedAt));

        // sender가 실제 성공을 뜻하는 정상 반환을 한 경우에만 기록한다.
        return alert.markNotified(OffsetDateTime.now(clock));
    }
}
