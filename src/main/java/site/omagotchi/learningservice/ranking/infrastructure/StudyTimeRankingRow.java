package site.omagotchi.learningservice.ranking.infrastructure;

import java.util.UUID;

public interface StudyTimeRankingRow {

    UUID getUserId();

    long getStudySeconds();
}
