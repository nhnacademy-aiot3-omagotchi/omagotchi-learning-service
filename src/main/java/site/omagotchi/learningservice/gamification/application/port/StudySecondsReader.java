package site.omagotchi.learningservice.gamification.application.port;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 학습 시간 퀘스트가 필요로 하는 공부시간 조회 계약.
 *
 * <p>application이 저장 기술을 알지 않도록 port로 분리한다. 구현은 infrastructure가 갖는다.
 */
public interface StudySecondsReader {

    /**
     * 해당 집계일에 쌓인 공부시간(초).
     */
    long dailyStudySeconds(UUID userId, Long cohortId, LocalDate aggregationDate);

    /**
     * 최근 7 등원일의 하루 공부시간 평균(초). 등원일이 없으면 0이다.
     */
    long recentAttendedAverageSeconds(UUID userId, Long cohortId, LocalDate aggregationDate);

    /**
     * 해당 집계일 이전에 확정된 StudyRecord가 하나라도 있는지.
     * 예측 피처가 보는 범위(featureDate = 집계일 전날까지)와 같은 범위를 본다.
     */
    boolean hasStudyRecordBefore(UUID userId, Long cohortId, LocalDate aggregationDate);
}
