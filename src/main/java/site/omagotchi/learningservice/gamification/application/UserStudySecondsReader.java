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

/**
 * 학습 시간 퀘스트가 필요로 하는 공부시간 원본을 읽는다.
 *
 * <p>공부시간은 기수 소속에 귀속되지만, 퀘스트는 활성 소속이 없는 사용자에게도 발급된다.
 * 그래서 소속 조회 실패를 예외로 올리지 않고 비어 있는 결과로 바꾼다.
 * 여기서 예외를 올리면 지금 잘 보이던 퀘스트 화면이 통째로 실패한다.
 *
 * <p>조회 자체는 {@link StudySecondsReader} port에 위임하고, 이 클래스는 소속 확보와
 * 실패 처리라는 정책만 갖는다.
 */
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
            log.debug("활성 기수 소속이 없어 학습 시간 퀘스트 원본을 읽지 않는다. userId={}", userId);
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
}
