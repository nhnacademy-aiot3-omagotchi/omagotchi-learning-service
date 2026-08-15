package site.omagotchi.learningservice.ranking.application.port;

import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface StudyRankingRepository {

    StudyRankingRows findBoard(StudyRankingWindow window, int maxRank, Long cohortId);

    StudyRankingRows findBoardAndMember(
            StudyRankingWindow window,
            int maxRank,
            Long cohortId,
            Long cohortMembershipId
    );

    StudyRankingRows findMember(
            StudyRankingWindow window,
            Long cohortId,
            Long cohortMembershipId
    );

    record RankedStudyMember(
            Long cohortMembershipId,
            UUID userId,
            long rank,
            long studySeconds
    ) {
    }

    record StudyRankingRows(
            long rankedMemberCount,
            List<RankedStudyMember> leaders,
            Optional<RankedStudyMember> focusedMember
    ) {

        public StudyRankingRows {
            leaders = List.copyOf(leaders);
            Objects.requireNonNull(focusedMember, "focusedMember must not be null");
        }
    }
}
