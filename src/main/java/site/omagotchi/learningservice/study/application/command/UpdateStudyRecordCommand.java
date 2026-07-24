package site.omagotchi.learningservice.study.application.command;

public record UpdateStudyRecordCommand(
        String date,
        String startTime,
        String endTime,
        Long expectedVersion
) {
}
