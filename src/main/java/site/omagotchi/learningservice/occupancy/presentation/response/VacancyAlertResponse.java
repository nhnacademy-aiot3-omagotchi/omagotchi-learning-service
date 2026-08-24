package site.omagotchi.learningservice.occupancy.presentation.response;

import site.omagotchi.learningservice.occupancy.application.result.VacancyAlertView;

import java.time.OffsetDateTime;

/** 대기 중인 공실 알림 신청 한 건. */
public record VacancyAlertResponse(
        Long alertId,
        Long spaceId,
        Long cohortId,
        OffsetDateTime createdAt
) {

    public static VacancyAlertResponse from(VacancyAlertView view) {
        return new VacancyAlertResponse(
                view.alertId(),
                view.spaceId(),
                view.cohortId(),
                view.createdAt()
        );
    }
}
