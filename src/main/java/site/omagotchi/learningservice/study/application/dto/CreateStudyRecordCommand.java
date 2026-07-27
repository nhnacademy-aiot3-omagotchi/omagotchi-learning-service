package site.omagotchi.learningservice.study.application.dto;

public record CreateStudyRecordCommand(
        String date,
        String startTime,
        String endTime
) {
}
