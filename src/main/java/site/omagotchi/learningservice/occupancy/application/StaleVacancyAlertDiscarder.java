package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;

import java.util.Collection;

/**
 * 소속이 끝난 신청자의 대기 알림을 자기 Transaction에서 폐기한다.
 *
 * <p>{@link VacancyAlertDispatcher}가 {@code NOT_SUPPORTED}라 Transaction 없이 도는 것이
 * 이 Class가 있는 이유다. 벌크 삭제는 Transaction을 요구하므로 거기서 바로 부르면
 * {@code TransactionRequiredException}이 된다. {@link VacancyAlertDelivery}가 건별 발송을
 * {@code REQUIRES_NEW}로 감싸는 것과 같은 구조다.</p>
 *
 * <p><b>발송과 분리된 Transaction인 것이 의도다.</b> 폐기가 실패해도 나머지 대기자에게는
 * 발송돼야 하고, 반대로 발송 실패가 폐기를 되돌려서도 안 된다 — 소속이 끝났다는 사실은
 * 발송 결과와 무관하다.</p>
 */
@Component
@RequiredArgsConstructor
public class StaleVacancyAlertDiscarder {

    private final VacancyAlertRepository alertRepository;

    /**
     * 이 멤버십들의 대기 중 신청을 지운다.
     *
     * @return 지운 건수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int discard(Collection<Long> inactiveMembershipIds) {
        return alertRepository.deleteWaitingByMembershipIds(inactiveMembershipIds);
    }
}
