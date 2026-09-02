package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.gamification.application.port.StudyProgressionQueryRepository;
import site.omagotchi.learningservice.gamification.application.port.UserDailyQuestQueryRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.util.DateTimeProvider;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GamificationProgressionServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long COHORT_ID = 7L;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private StudyProgressionQueryRepository studyProgressionQueryRepository;

    @Mock
    private UserDailyQuestQueryRepository userDailyQuestQueryRepository;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private GamificationProgressionService gamificationProgressionService;

    @Test
    @DisplayName("소속하지 않은 기수의 진행도는 조회하지 않는다")
    void rejectsProgressionForNonMemberCohort() {
        // 미소속 요청이 학습 시간 0으로 200을 받으면 같은 화면의 랭킹·출석과 응답이 갈린다.
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));

        assertThatThrownBy(() -> gamificationProgressionService.getProgression(
                USER_ID,
                COHORT_ID,
                LocalDate.of(2026, 8, 21)
        )).isInstanceOf(BusinessException.class);

        // 권한 판정 전에 하위 조회가 실행되면 안 된다.
        verify(studyProgressionQueryRepository, never())
                .getDailyStudySeconds(any(), any(), any());
    }
}
