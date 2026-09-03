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

import org.mockito.InOrder;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

    /*
     * AI 추천 퀘스트는 예측 모델이 "학습 종료"를 신호로 학습되어 있어 완료 조건이
     * 학습 완료와 같다. 호출부가 빠지면 화면에서 AI 카드만 0/1 로 남고 보상 받기가
     * 나타나지 않는다. 실제로 그 상태로 한동안 방치됐던 적이 있어 여기서 묶어 둔다.
     */
    @Test
    @DisplayName("학습 완료 이벤트는 AI 추천 퀘스트도 함께 완료한다")
    void completesLlmQuestOnStudyCompleted() {
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

        InOrder order = inOrder(dailyQuestService);
        order.verify(dailyQuestService).handleStudyCompleted(USER_ID);
        order.verify(dailyQuestService).handleLlmQuestCompleted(USER_ID);
        verifyNoMoreInteractions(dailyQuestService);
    }

    @Test
    @DisplayName("출석 이벤트는 AI 추천 퀘스트를 건드리지 않는다")
    void doesNotCompleteLlmQuestOnAttendance() {
        GamificationEventMessage event = new GamificationEventMessage(
                GamificationEventType.ATTENDANCE_CHECKED_IN,
                STUDY_SOURCE_ID.toString(),
                USER_ID,
                OCCURRED_AT);
        given(eventReceiptRepository.claim(
                GamificationEventType.ATTENDANCE_CHECKED_IN,
                STUDY_SOURCE_ID.toString(),
                USER_ID,
                OCCURRED_AT
        )).willReturn(true);

        eventProcessor.process(event);

        verify(dailyQuestService).handleAttendance(USER_ID);
        verifyNoMoreInteractions(dailyQuestService);
    }
}
