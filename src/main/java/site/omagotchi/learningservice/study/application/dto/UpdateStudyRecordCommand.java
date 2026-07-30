package site.omagotchi.learningservice.study.application.dto;

public record UpdateStudyRecordCommand(
        String date,
        String startTime,
        String endTime,
        Long expectedVersion
) {
}
