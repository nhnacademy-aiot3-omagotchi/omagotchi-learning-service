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
