package site.omagotchi.learningservice.occupancy.presentation.request;

import jakarta.validation.constraints.Positive;

/**
 * 공실 알림 신청 요청.
 *
 * <p>참여자 추가와 달리 기수를 받는 것이 의도다. 저쪽은 대상의 멤버십을 열린 재실
 * 구간에서 도출할 수 있지만, 공실 알림은 재실을 요구하지 않아 도출할 근거가 없다.
 * 그리고 다기수 담당자는 같은 회의실에 기수마다 신청할 수 있어야 하므로(명세 04 §4)
 * 서버가 임의로 하나를 고르면 안 된다.</p>
 *
 * <p>{@code null}을 허용하는 것도 의도다. 활성 소속이 하나뿐인 대다수 사용자에게
 * 매번 기수를 요구할 이유가 없다 — 둘 이상일 때만 400으로 지정을 요구한다.</p>
 */
public record RequestVacancyAlertRequest(
        @Positive(message = "기수 ID는 양수여야 합니다.")
        Long cohortId
) {
}
