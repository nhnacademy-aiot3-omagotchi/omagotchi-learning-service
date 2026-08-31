package site.omagotchi.learningservice.study.application;

import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;
import site.omagotchi.learningservice.study.domain.TimerRun;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class TimerStudyRecordFactory {

    public List<StudyRecord> createFrom(TimerRun timerRun) {
        Instant startedAt = timerRun.getStartedAt();
        Instant endedAt = timerRun.getEndedAt();
        long measuredSeconds = timerRun.getMeasuredSeconds();

        return StudyTimePolicy.findCrossedAggregationBoundary(startedAt, endedAt)
                .map(boundary -> createSplitRecords(timerRun, boundary))
                .orElseGet(() -> createRecord(
                        timerRun.getCohortMembershipId(),
                        startedAt,
                        endedAt,
                        measuredSeconds
                ).stream().toList());
    }

    private List<StudyRecord> createSplitRecords(TimerRun timerRun, Instant boundary) {
        long firstChunkSeconds = Duration.between(timerRun.getStartedAt(), boundary).getSeconds();
        long secondChunkSeconds = timerRun.getMeasuredSeconds() - firstChunkSeconds;
        List<StudyRecord> studyRecords = new ArrayList<>(2);

        createRecord(
                timerRun.getCohortMembershipId(),
                timerRun.getStartedAt(),
                boundary,
                firstChunkSeconds
        ).ifPresent(studyRecords::add);
        createRecord(
                timerRun.getCohortMembershipId(),
                boundary,
                timerRun.getEndedAt(),
                secondChunkSeconds
        ).ifPresent(studyRecords::add);

        return List.copyOf(studyRecords);
    }

    private Optional<StudyRecord> createRecord(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            long studySeconds
    ) {
        if (!startTime.isBefore(endTime) || studySeconds <= 0L) {
            return Optional.empty();
        }

        return Optional.of(StudyRecord.create(
                cohortMembershipId,
                startTime,
                endTime,
                studySeconds
        ));
    }
}
