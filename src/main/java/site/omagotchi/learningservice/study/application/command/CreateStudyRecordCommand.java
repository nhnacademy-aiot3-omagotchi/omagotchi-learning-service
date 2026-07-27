package site.omagotchi.learningservice.study.application.command;

public record CreateStudyRecordCommand(
        String date,
        String startTime,
        String endTime
) {
}
