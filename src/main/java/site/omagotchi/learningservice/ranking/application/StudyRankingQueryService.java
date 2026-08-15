package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository.RankedStudyMember;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository.StudyRankingRows;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriod;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRankingQueryService {

    private final CohortAccessService cohortAccessService;
    private final StudyRankingRepository studyRankingRepository;
    private final CharacterGrowthService characterGrowthService;
    private final Clock clock;

    public MemberStudyRankingViewResult getMemberView(
            UUID userId,
            Long cohortId,
            StudyRankingQuery query
    ) {
        CohortMembership membership = requireActiveStudent(
                cohortId,
                userId
        );
        Instant calculatedAt = clock.instant();
        StudyRankingWindow window = StudyRankingWindow.resolve(
                query.period(),
                calculatedAt
        );
        int maxRank = query.resolveMaxRank();
        StudyRankingRows rows = studyRankingRepository.findBoardAndMember(
                window,
                maxRank,
                cohortId,
                membership.getId()
        );
        Map<UUID, String> displayNames = findDisplayNames(rows);
        StudyRankingBoardResult board = boardResult(
                rows,
                displayNames
        );
        MyStudyRankingResult mine = mineResult(rows, displayNames);
        return new MemberStudyRankingViewResult(board, mine);
    }

    public MyStudyRankingResult getMine(
            UUID userId,
            Long cohortId,
            StudyRankingPeriod period
    ) {
        CohortMembership membership = requireActiveStudent(
                cohortId,
                userId
        );
        Instant calculatedAt = clock.instant();
        StudyRankingWindow window = StudyRankingWindow.resolve(
                period,
                calculatedAt
        );
        StudyRankingRows rows = studyRankingRepository.findMember(
                window,
                cohortId,
                membership.getId()
        );
        return mineResult(rows, findDisplayNames(rows));
    }

    public StudyRankingBoardResult getManagerBoard(
            UUID userId,
            Long cohortId,
            StudyRankingQuery query
    ) {
        cohortAccessService.requireManager(cohortId, userId);
        Instant calculatedAt = clock.instant();
        StudyRankingWindow window = StudyRankingWindow.resolve(
                query.period(),
                calculatedAt
        );
        int maxRank = query.resolveMaxRank();
        StudyRankingRows rows = studyRankingRepository.findBoard(
                window,
                maxRank,
                cohortId
        );
        return boardResult(rows, findDisplayNames(rows));
    }

    private StudyRankingBoardResult boardResult(
            StudyRankingRows rows,
            Map<UUID, String> displayNames
    ) {
        List<StudyRankingEntryResult> entries = rows.leaders().stream()
                .map(row -> entryResult(row, displayNames))
                .toList();
        return new StudyRankingBoardResult(
                rows.rankedMemberCount(),
                entries
        );
    }

    private MyStudyRankingResult mineResult(
            StudyRankingRows rows,
            Map<UUID, String> displayNames
    ) {
        Optional<StudyRankingEntryResult> ranking = rows.focusedMember()
                .map(row -> entryResult(row, displayNames));
        return new MyStudyRankingResult(
                rows.rankedMemberCount(),
                ranking
        );
    }

    private StudyRankingEntryResult entryResult(
            RankedStudyMember row,
            Map<UUID, String> displayNames
    ) {
        return new StudyRankingEntryResult(
                row.rank(),
                displayNames.get(row.userId()),
                row.studySeconds()
        );
    }

    private Map<UUID, String> findDisplayNames(StudyRankingRows rows) {
        Collection<UUID> userIds = new LinkedHashSet<>();
        rows.leaders().stream()
                .map(RankedStudyMember::userId)
                .forEach(userIds::add);
        rows.focusedMember()
                .map(RankedStudyMember::userId)
                .ifPresent(userIds::add);
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return characterGrowthService.findRepresentativeCharacters(userIds)
                .stream()
                .collect(Collectors.toMap(
                        RepresentativeCharacterResult::userId,
                        RepresentativeCharacterResult::displayName,
                        (first, ignored) -> first
                ));
    }

    private CohortMembership requireActiveStudent(Long cohortId, UUID userId) {
        CohortMembership membership = cohortAccessService.requireActiveMembership(
                cohortId,
                userId
        );
        if (membership.getRole() != CohortMembershipRole.STUDENT) {
            throw new BusinessException(CohortErrorCode.COHORT_ACCESS_DENIED);
        }
        return membership;
    }
}
