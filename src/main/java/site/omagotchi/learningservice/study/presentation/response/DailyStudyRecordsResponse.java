package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.DailyStudyRecordsResult;

import java.time.LocalDate;
import java.util.List;

public record DailyStudyRecordsResponse(
        LocalDate aggregationDate,
        long totalStudySeconds,
        List<StudyRecordResponse> records
) {

    public DailyStudyRecordsResponse {
        records = List.copyOf(records);
    }

    public static DailyStudyRecordsResponse from(DailyStudyRecordsResult result) {
        return new DailyStudyRecordsResponse(
                result.aggregationDate(),
                result.totalStudySeconds(),
                result.records().stream()
                        .map(StudyRecordResponse::from)
                        .toList()
        );
    }
}
