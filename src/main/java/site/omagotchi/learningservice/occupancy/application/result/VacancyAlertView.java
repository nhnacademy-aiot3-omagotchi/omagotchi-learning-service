package site.omagotchi.learningservice.occupancy.application.result;

import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.time.OffsetDateTime;

/**
 * 대기 중인 공실 알림 신청 한 건.
 *
 * <p>{@code notifiedAt}을 담지 않는 것이 의도다. 이 Type이 나오는 경로는 대기 목록뿐이라
 * 항상 {@code null}이며, 넣으면 소비처가 "값이 있을 수도 있다"고 오해한다.</p>
 *
 * @param cohortId 어느 기수 소속으로 신청했는지. 다기수 담당자가 같은 방에 두 번 신청할 수
 *                 있으므로(§4) 화면이 두 행을 구분하려면 이 값이 필요하다
 */
public record VacancyAlertView(
        Long alertId,
        Long spaceId,
        Long cohortId,
        OffsetDateTime createdAt
) {

    public static VacancyAlertView of(VacancyAlert alert, Long cohortId) {
        return new VacancyAlertView(
                alert.getId(),
                alert.getSpaceId(),
                cohortId,
                alert.getCreatedAt()
        );
    }
}
