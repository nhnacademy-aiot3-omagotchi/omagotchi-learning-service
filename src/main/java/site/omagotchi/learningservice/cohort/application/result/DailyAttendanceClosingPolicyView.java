package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 일일 미퇴실 마감에 필요한 ACTIVE 소속의 출결 정책.
 *
 * <p>출결은 기수 domain 객체를 직접 받지 않고 Java 표준 타입으로만
 * 마감 시각을 계산한다. 마감 시각은 저장된 출결일의 {@code scheduledEndTime}에
 * {@link #CLOSING_GRACE}를 더한 값이다.</p>
 */
public record DailyAttendanceClosingPolicyView(
        Long membershipId,
        String timezone,
        LocalTime scheduledEndTime
) {

    /** 예정 종료 이후 퇴실을 기다려 주는 시간. */
    private static final Duration CLOSING_GRACE = Duration.ofHours(3);

    public DailyAttendanceClosingPolicyView {
        Objects.requireNonNull(membershipId, "소속 ID는 필수입니다.");
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("timezone은 필수입니다.");
        }
        Objects.requireNonNull(scheduledEndTime, "예정 종료 시각은 필수입니다.");
    }

    public static DailyAttendanceClosingPolicyView from(
            CohortMembership membership,
            CohortAttendancePolicy policy
    ) {
        return new DailyAttendanceClosingPolicyView(
                membership.getId(),
                policy.getTimezone(),
                policy.getScheduledEndTime()
        );
    }

    /** 예정 종료 시각에 유예를 더한 마감 시각. */
    public Instant closingAt(LocalDate attendanceDate) {
        return Objects.requireNonNull(attendanceDate, "출결일은 필수입니다.")
                .atTime(scheduledEndTime)
                .atZone(ZoneId.of(timezone))
                .toInstant()
                .plus(CLOSING_GRACE);
    }
}
