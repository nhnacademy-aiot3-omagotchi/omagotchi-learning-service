package site.omagotchi.learningservice.study.application.command;

public record CreateStudyRecordCommand(
        Long cohortId,
        String date,
        String startTime,
        String endTime
) {
}
