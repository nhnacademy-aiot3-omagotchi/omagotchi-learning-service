package site.omagotchi.learningservice.prediction.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record StudyTimePredictionRequest(
        // targetDate 1일 전 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyLag1,
        // targetDate 2일 전 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyLag2,
        // targetDate 3일 전 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyLag3,
        // targetDate 전날까지 최근 7일의 달력 기준 평균 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double study7dMean,
        // targetDate 전날까지 최근 30일의 달력 기준 평균 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double study30dMean,
        // 첫 기록일부터 targetDate 전날까지의 평균 공부시간(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double studyAllMean,
        // targetDate 전날까지 최근 7일 공부시간의 표준편차(h)
        @NotNull @DecimalMin("0.0") @DecimalMax("11.5") Double study7dStd,

        // 최근 7일 평균과 최근 30일 평균의 차이({@code study7dMean - study30dMean})
        @NotNull @DecimalMin("-11.5") @DecimalMax("11.5") Double trend7To30,
        // targetDate 1일 전과 2일 전 공부시간의 차이({@code studyLag1 - studyLag2})
        @NotNull @DecimalMin("-11.5") @DecimalMax("11.5") Double studyDiff1d,

        // 최근 7일 평일 중 확정 StudyRecord가 존재하는 비율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double att7d,
        // 최근 30일 평일 중 확정 StudyRecord가 존재하는 비율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double att30d,
        // 전체 소속 평일 중 확정 StudyRecord가 존재하는 비율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double attAll,
        // 최근 7개 달력일 중 확정 StudyRecord가 존재하는 날짜 수
        @NotNull @DecimalMin("0.0") @DecimalMax("7.0") Double attendDays7d,
        // targetDate 전날에 확정 StudyRecord가 없으면 요일과 무관하게 1이다
        @NotNull @Min(0) @Max(1) Integer noshowYesterday,

        // 최근 7일 확정 학습 평일의 지각·조퇴율. 학습일이 없으면 null
        @DecimalMin("0.0") @DecimalMax("1.0") Double late7d,
        // 최근 30일 확정 학습 평일의 지각·조퇴율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double late30d,
        // 전체 확정 학습 평일의 지각·조퇴율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double lateAll,
        // 최근 7일 확정 학습 평일 중 MISSING_CHECK_OUT 또는 PENDING인 일수
        @NotNull @DecimalMin("0.0") @DecimalMax("7.0") Double forgot7d,

        // targetDate 전날 확정 학습일의 입실 시각(min). 메타데이터가 없으면 null
        @DecimalMin("420.0") @DecimalMax("830.0") Double entryLag1Min,
        // 최근 7일 확정 학습일의 평균 입실 시각(min). 메타데이터가 없으면 null
        @DecimalMin("420.0") @DecimalMax("830.0") Double entry7dMeanMin,

        // 요청 시점의 최신 대표 캐릭터 레벨
        @NotNull @Min(1) @Max(30) Integer level,
        // 누적 퀘스트 완료 수. 시간이 지나며 증가하므로 상한이 없다
        @NotNull @PositiveOrZero Long questsTotal,
        // 연속 퀘스트 완료 일수. 시간이 지나며 증가하므로 상한이 없다
        @NotNull @PositiveOrZero Long questStreak,
        // 최근 7일 퀘스트 완료율
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double questRate7d,

        // 모델 기준 내일인 targetDate가 평일이면 1, 주말이면 0
        @NotNull @Min(0) @Max(1) Integer tomorrowIsWeekday,
        // targetDate가 화요일이면 1. 월요일이면 모든 요일 원-핫 값이 0이다
        @NotNull @Min(0) @Max(1) Integer tomorrowDow1,
        // targetDate가 수요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow2,
        // targetDate가 목요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow3,
        // targetDate가 금요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow4,
        // targetDate가 토요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow5,
        // targetDate가 일요일이면 1
        @NotNull @Min(0) @Max(1) Integer tomorrowDow6,
        // 첫 기록일로부터 지난 일수. 시간이 지나며 증가하므로 상한이 없다
        @NotNull @PositiveOrZero Long daysSinceStart
) {

    /**
     * Python 요청 계약과 동일하게 화요일~일요일은 최대 하나만 선택하고,
     * 주말 원-핫과 평일 여부가 모순되지 않게 한다.
     */
    @JsonIgnore
    @AssertTrue(message = "내일 요일 원-핫 값과 평일 여부가 일치해야 합니다.")
    public boolean isTomorrowDayOfWeekConsistent() {
        if (tomorrowIsWeekday == null
                || tomorrowDow1 == null
                || tomorrowDow2 == null
                || tomorrowDow3 == null
                || tomorrowDow4 == null
                || tomorrowDow5 == null
                || tomorrowDow6 == null) {
            // null 자체는 각 필드의 @NotNull 제약이 보고하고 조합 검증은 중복 오류를 만들지 않는다.
            return true;
        }
        int selectedDays = tomorrowDow1 + tomorrowDow2 + tomorrowDow3
                + tomorrowDow4 + tomorrowDow5 + tomorrowDow6;
        if (selectedDays > 1) {
            return false;
        }
        int expectedWeekday = tomorrowDow5 == 1 || tomorrowDow6 == 1 ? 0 : 1;
        return tomorrowIsWeekday == expectedWeekday;
    }
}
