package site.omagotchi.learningservice.study.presentation.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import site.omagotchi.learningservice.study.application.dto.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.StudyRecordCommandService;
import site.omagotchi.learningservice.study.application.dto.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.StudyRecordQueryService;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.response.StudyRecordResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("학습 기록 API")
@ExtendWith(MockitoExtension.class)
class StudyRecordControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID COMMAND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final UUID STUDY_RECORD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final Long COHORT_ID = 10L;
    private static final Long EXPECTED_VERSION = 1L;
    private static final String DATE = "20000101";
    private static final String START_TIME = "1000";
    private static final String END_TIME = "1100";

    @Mock
    private StudyRecordCommandService studyRecordCommandService;

    @Mock
    private StudyRecordQueryService studyRecordQueryService;

    @InjectMocks
    private StudyRecordController studyRecordController;

    @Nested
    @DisplayName("단건 조회")
    class Get {

        @Test
        @DisplayName("정상 처리")
        void returnsStudyRecord() {
            StudyRecordResult result = studyRecordResult();
            given(studyRecordQueryService.getRecord(USER_ID, COHORT_ID, STUDY_RECORD_ID))
                    .willReturn(result);

            ResponseEntity<StudyRecordResponse> response = studyRecordController.get(
                    USER_ID,
                    COHORT_ID,
                    STUDY_RECORD_ID
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(StudyRecordResponse.from(result), response.getBody());
        }
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("정상 처리")
        void createsStudyRecord() {
            CreateStudyRecordRequest request = new CreateStudyRecordRequest(
                    DATE,
                    START_TIME,
                    END_TIME
            );
            CreateStudyRecordCommand command = request.toCommand();
            StudyRecordResult result = studyRecordResult();
            given(studyRecordCommandService.create(COMMAND_ID, USER_ID, COHORT_ID, command))
                    .willReturn(result);

            ResponseEntity<StudyRecordResponse> response = studyRecordController.create(
                    USER_ID,
                    COMMAND_ID,
                    COHORT_ID,
                    request
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(StudyRecordResponse.from(result), response.getBody());
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("정상 처리")
        void updatesStudyRecord() {
            UpdateStudyRecordRequest request = new UpdateStudyRecordRequest(
                    DATE,
                    START_TIME,
                    END_TIME,
                    EXPECTED_VERSION
            );
            UpdateStudyRecordCommand command = request.toCommand();
            StudyRecordResult result = studyRecordResult();
            given(studyRecordCommandService.update(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    STUDY_RECORD_ID,
                    command
            )).willReturn(result);

            ResponseEntity<StudyRecordResponse> response = studyRecordController.update(
                    USER_ID,
                    COMMAND_ID,
                    COHORT_ID,
                    STUDY_RECORD_ID,
                    request
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(StudyRecordResponse.from(result), response.getBody());
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("정상 처리")
        void deletesStudyRecord() {
            ResponseEntity<Void> response = studyRecordController.delete(
                    USER_ID,
                    COMMAND_ID,
                    EXPECTED_VERSION,
                    COHORT_ID,
                    STUDY_RECORD_ID
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            assertNull(response.getBody());
            verify(studyRecordCommandService).delete(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    STUDY_RECORD_ID,
                    EXPECTED_VERSION
            );
        }
    }

    private StudyRecordResult studyRecordResult() {
        return new StudyRecordResult(
                STUDY_RECORD_ID,
                LocalDate.of(2000, Month.JANUARY, 1),
                Instant.parse("2000-01-01T01:00:00Z"),
                Instant.parse("2000-01-01T02:00:00Z"),
                3_600L,
                EXPECTED_VERSION,
                Instant.parse("2000-01-01T02:00:01Z"),
                Instant.parse("2000-01-01T02:00:01Z")
        );
    }
}
