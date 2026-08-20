package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventReceiptRepository;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("게이미피케이션 내부 이벤트 처리")
class GamificationEventProcessorTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STUDY_SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Mock
    private GamificationEventReceiptRepository eventReceiptRepository;

    @Mock
    private DailyQuestService dailyQuestService;

    @InjectMocks
    private GamificationEventProcessor eventProcessor;

    @Test
    @DisplayName("최초 출석 이벤트만 출석 퀘스트를 진행한다")
    void progressesAttendanceQuestForFirstEvent() {
        GamificationEventMessage event = new GamificationEventMessage(
                GamificationEventType.ATTENDANCE_CHECKED_IN, "10", USER_ID, OCCURRED_AT);
        given(eventReceiptRepository.claim(
                GamificationEventType.ATTENDANCE_CHECKED_IN,
                "10",
                USER_ID,
                OCCURRED_AT
        )).willReturn(true);

        eventProcessor.process(event);

        verify(dailyQuestService).handleAttendance(USER_ID);
    }

    @Test
    @DisplayName("이미 처리한 출석 이벤트는 퀘스트를 다시 진행하지 않는다")
    void ignoresDuplicateAttendanceEvent() {
        GamificationEventMessage event = new GamificationEventMessage(
                GamificationEventType.ATTENDANCE_CHECKED_IN, "10", USER_ID, OCCURRED_AT);
        given(eventReceiptRepository.claim(
                GamificationEventType.ATTENDANCE_CHECKED_IN,
                "10",
                USER_ID,
                OCCURRED_AT
        )).willReturn(false);

        eventProcessor.process(event);

        verifyNoInteractions(dailyQuestService);
    }

    @Test
    @DisplayName("최초 학습 완료 이벤트만 학습 퀘스트를 진행한다")
    void progressesStudyQuestForFirstEvent() {
        GamificationEventMessage event = new GamificationEventMessage(
                GamificationEventType.STUDY_COMPLETED,
                STUDY_SOURCE_ID.toString(),
                USER_ID,
                OCCURRED_AT);
        given(eventReceiptRepository.claim(
                GamificationEventType.STUDY_COMPLETED,
                STUDY_SOURCE_ID.toString(),
                USER_ID,
                OCCURRED_AT
        )).willReturn(true);

        eventProcessor.process(event);

        verify(dailyQuestService).handleStudyCompleted(USER_ID);
    }
}
