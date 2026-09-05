package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.gamification.application.port.StudySecondsReader;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserStudySecondsReader {

    private final CohortAccessService cohortAccessService;
    private final StudySecondsReader studySecondsReader;

    public Optional<Long> findActiveCohortId(UUID userId) {
        try {
            return Optional.of(cohortAccessService.requireCurrentActiveMembership(userId).getCohortId());
        } catch (BusinessException exception) {
            log.debug(
                    "활성 기수 소속이 없어 학습 시간 퀘스트 원본을 읽지 않습니다. "
                            + "사용자(userIdMasked)={}",
                    maskUserId(userId)
            );
            return Optional.empty();
        }
    }

    public long dailyStudySeconds(UUID userId, Long cohortId, LocalDate aggregationDate) {
        return studySecondsReader.dailyStudySeconds(userId, cohortId, aggregationDate);
    }

    /**
     * 최근 7 등원일 평균(초). 등원일이 없으면 0이다.
     */
    public long recentAttendedAverageSeconds(UUID userId, Long cohortId, LocalDate aggregationDate) {
        return studySecondsReader.recentAttendedAverageSeconds(userId, cohortId, aggregationDate);
    }

    /**
     * 해당 집계일 이전에 확정 학습 기록이 하나라도 있는지. 없으면 콜드스타트다.
     */
    public boolean hasStudyRecordBefore(UUID userId, Long cohortId, LocalDate aggregationDate) {
        return studySecondsReader.hasStudyRecordBefore(userId, cohortId, aggregationDate);
    }

    private String maskUserId(UUID userId) {
        if (userId == null) {
            return "none";
        }
        return userId.toString().substring(0, 8);
    }
}
