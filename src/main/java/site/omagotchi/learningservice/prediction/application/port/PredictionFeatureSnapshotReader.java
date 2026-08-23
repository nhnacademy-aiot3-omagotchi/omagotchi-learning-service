package site.omagotchi.learningservice.prediction.application.port;

import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface PredictionFeatureSnapshotReader {

    PredictionFeatureSnapshot read(
            UUID userId,
            Long cohortId,
            Long cohortMembershipId,
            LocalDate baseDate,
            Instant observedAt
    );
}
