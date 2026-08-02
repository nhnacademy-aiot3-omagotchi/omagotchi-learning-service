package site.omagotchi.learningservice.study.application.result;

import java.time.LocalDate;
import java.util.List;

public record DailyStudyRecordsResult(
        LocalDate aggregationDate,
        long totalStudySeconds,
        List<StudyRecordResult> records
) {

    public DailyStudyRecordsResult {
        records = List.copyOf(records);
    }
}
