package site.omagotchi.learningservice.prediction.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StudyTimePredictionRequest(
        // 오늘 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyLag1,
        // 어제 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyLag2,
        // 그제 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyLag3,
        // 최근 7일의 달력 기준 평균 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double study7dMean,
        // 최근 30일의 달력 기준 평균 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double study30dMean,
        // 첫 기록일 이후 전체 기간의 평균 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyAllMean,
        // 최근 7일 공부시간의 표준편차(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double study7dStd,

        // 최근 7일 평균과 최근 30일 평균의 차이({@code study7dMean - study30dMean})
        @NotNull @DecimalMin("-11.5") @DecimalMax("11.5") Double trend7To30,
        // 오늘과 어제 공부시간의 차이({@code studyLag1 - studyLag2})
        @NotNull @DecimalMin("-11.5") @DecimalMax("11.5") Double studyDiff1d,

        // 최근 7일의 평일 등원율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double att7d,
        // 최근 30일의 평일 등원율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double att30d,
        // 전체 기간의 평일 등원율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double attAll,
        // 최근 7일 중 등원한 일수
        @NotNull @DecimalMin("0.0") @DecimalMax("7.0") Double attendDays7d,
        // 오늘 미등원 여부. 필드 이름과 달리 어제가 아니며, 미등원이면 1이다
        @NotNull @Min(0) @Max(1) Integer noshowYesterday,

        // 최근 7일 지각률. 평일 등원 기록이 없으면 null
        @DecimalMin("0.0") @DecimalMax("1.0") Double late7d,
        // 최근 30일 지각률
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double late30d,
        // 전체 기간 지각률
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double lateAll,
        // 최근 7일 중 퇴실 태그를 남기지 않은 일수
        @NotNull @DecimalMin("0.0") @DecimalMax("7.0") Double forgot7d,

        // 오늘 입실 시각을 자정부터 지난 분으로 표현한 값. 미등원일이면 null
        @DecimalMin("420.0") @DecimalMax("830.0") Double entryLag1Min,
        // 최근 7일 평균 입실 시각(min). 등원 기록이 없으면 null
        @DecimalMin("420.0") @DecimalMax("830.0") Double entry7dMeanMin,

        // 사용자의 현재 레벨
        @NotNull @Min(1) @Max(30) Integer level,
        // 누적 퀘스트 완료 수. 시간이 지나며 증가하므로 상한이 없다
        @NotNull @PositiveOrZero Long questsTotal,
        // 연속 퀘스트 완료 일수. 시간이 지나며 증가하므로 상한이 없다
        @NotNull @PositiveOrZero Long questStreak,
        // 최근 7일 퀘스트 완료율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double questRate7d,

        // 내일이 평일이면 1, 주말이면 0
        @NotNull @Min(0) @Max(1) Integer tomorrowIsWeekday,
        // 내일이 화요일이면 1. 월요일이면 모든 요일 원-핫 값이 0이다
        @NotNull @Min(0) @Max(1) Integer tomorrowDow1,
        // 내일이 수요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow2,
        // 내일이 목요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow3,
        // 내일이 금요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow4,
        // 내일이 토요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow5,
        // 내일이 일요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow6,
        // 첫 기록일로부터 지난 일수. 시간이 지나며 증가하므로 상한이 없다
        @NotNull @PositiveOrZero Long daysSinceStart
) {
}
