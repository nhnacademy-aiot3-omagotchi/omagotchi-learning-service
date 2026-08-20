package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("멤버십별 확정 공부시간 조회")
class StudyRecordAggregationQueryServiceTest {

    private static final LocalDate START_DATE = LocalDate.parse("2000-01-03");
    private static final LocalDate END_DATE = LocalDate.parse("2000-01-07");

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    @InjectMocks
    private StudyRecordAggregationQueryService service;

    @Test
    @DisplayName("멤버십 목록과 기간을 한 번에 위임")
    void returnsConfirmedDurations() {
        List<Long> membershipIds = List.of(10L, 20L);
        List<MemberStudyDurationResult> expected = List.of(
                new MemberStudyDurationResult(10L, 7_200L),
                new MemberStudyDurationResult(20L, 3_600L)
        );
        given(studyRecordQueryRepository.findConfirmedDurations(
                membershipIds,
                START_DATE,
                END_DATE
        )).willReturn(expected);

        List<MemberStudyDurationResult> result = service.getConfirmedDurations(
                membershipIds,
                START_DATE,
                END_DATE
        );

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("멤버십 목록이 비어 있으면 DB를 조회하지 않음")
    void skipsQueryForEmptyMemberships() {
        assertEquals(
                List.of(),
                service.getConfirmedDurations(List.of(), START_DATE, END_DATE)
        );

        verify(studyRecordQueryRepository, never())
                .findConfirmedDurations(List.of(), START_DATE, END_DATE);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 요청 거부")
    void rejectsInvalidDateRange() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getConfirmedDurations(
                        List.of(10L),
                        END_DATE,
                        START_DATE
                )
        );

        assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }
}
