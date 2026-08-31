package site.omagotchi.learningservice.study.application.result;

public record LearningReportResult(
        int periodDays,
        long previousTotalStudyMinutes,     // 직전 기간(같은 길이의 바로 앞 구간) 총 공부 분
        int previousStudyDayCount,          // 직전 기간 학습일 수
        TopLearnerPatternResult thisPeriod  // 이번 기간: 내 패턴 + 상위권 비교 (상태 포함)
) {
}
