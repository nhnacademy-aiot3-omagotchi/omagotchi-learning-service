package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriod;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudyRankingQueryService {

    private final CohortAccessService cohortAccessService;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final StudyRecordAggregationQueryService studyRecordAggregationQueryService;
    private final CharacterGrowthService characterGrowthService;
    private final Clock clock;

    public MemberStudyRankingViewResult getMemberView(
            UUID userId,
            Long cohortId,
            StudyRankingQuery query
    ) {
        Long membershipId = cohortAccessService.requireActiveStudentMembershipId(
                cohortId,
                userId
        );
        StudyRankingWindow window = resolveWindow(query.period());
        StudyRankingRows rows = findRankingRows(
                cohortId,
                window,
                query.resolveMaxRank(),
                membershipId,
                true
        );
        Map<UUID, String> displayNames = findDisplayNames(rows);
        return new MemberStudyRankingViewResult(
                boardResult(rows, displayNames),
                mineResult(rows, displayNames)
        );
    }

    public MyStudyRankingResult getMine(
            UUID userId,
            Long cohortId,
            StudyRankingPeriod period
    ) {
        Long membershipId = cohortAccessService.requireActiveStudentMembershipId(
                cohortId,
                userId
        );
        StudyRankingRows rows = findRankingRows(
                cohortId,
                resolveWindow(period),
                0,
                membershipId,
                false
        );
        return mineResult(rows, findDisplayNames(rows));
    }

    public StudyRankingBoardResult getManagerBoard(
            UUID userId,
            Long cohortId,
            StudyRankingQuery query
    ) {
        cohortAccessService.requireManager(cohortId, userId);
        StudyRankingRows rows = findRankingRows(
                cohortId,
                resolveWindow(query.period()),
                query.resolveMaxRank(),
                null,
                true
        );
        return boardResult(rows, findDisplayNames(rows));
    }

    private StudyRankingWindow resolveWindow(StudyRankingPeriod period) {
        Instant calculatedAt = clock.instant();
        return StudyRankingWindow.resolve(period, calculatedAt);
    }

    private StudyRankingRows findRankingRows(
            Long cohortId,
            StudyRankingWindow window,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        List<CohortMembershipView> memberships = cohortMembershipQueryService
                .findActiveStudentMemberships(cohortId);
        List<Long> membershipIds = memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();

        // TODO: 현재 집계일을 포함하면 study의 실행 중 timer_runs 공개 조회 결과를 합산한다.
        List<MemberStudyDurationResult> durations = studyRecordAggregationQueryService
                .getConfirmedDurations(
                        membershipIds,
                        window.startDate(),
                        window.endDate()
                );

        List<RankedStudyMember> rankedMembers = rank(memberships, durations);
        List<RankedStudyMember> leaders = includeLeaders
                ? rankedMembers.stream()
                        .filter(member -> member.rank() <= maxRank)
                        .toList()
                : List.of();
        Optional<RankedStudyMember> focusedMember = rankedMembers.stream()
                .filter(member -> Objects.equals(
                        member.cohortMembershipId(),
                        focusedMembershipId
                ))
                .findFirst();

        return new StudyRankingRows(
                rankedMembers.size(),
                leaders,
                focusedMember
        );
    }

    private List<RankedStudyMember> rank(
            List<CohortMembershipView> memberships,
            List<MemberStudyDurationResult> durations
    ) {
        Map<Long, CohortMembershipView> membershipById = memberships.stream()
                .collect(Collectors.toMap(
                        CohortMembershipView::membershipId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        List<UnrankedStudyMember> sortedMembers = durations.stream()
                .filter(duration -> duration.studySeconds() > 0L)
                .map(duration -> toUnrankedMember(duration, membershipById))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingLong(UnrankedStudyMember::studySeconds)
                        .reversed()
                        .thenComparing(UnrankedStudyMember::cohortMembershipId))
                .toList();

        List<RankedStudyMember> rankedMembers = new ArrayList<>(sortedMembers.size());
        Long previousStudySeconds = null;
        long rank = 0L;
        for (int index = 0; index < sortedMembers.size(); index++) {
            UnrankedStudyMember member = sortedMembers.get(index);
            if (!Objects.equals(previousStudySeconds, member.studySeconds())) {
                rank = index + 1L;
                previousStudySeconds = member.studySeconds();
            }
            rankedMembers.add(new RankedStudyMember(
                    member.cohortMembershipId(),
                    member.userId(),
                    rank,
                    member.studySeconds()
            ));
        }
        return List.copyOf(rankedMembers);
    }

    private Optional<UnrankedStudyMember> toUnrankedMember(
            MemberStudyDurationResult duration,
            Map<Long, CohortMembershipView> membershipById
    ) {
        return Optional.ofNullable(membershipById.get(duration.cohortMembershipId()))
                .map(membership -> new UnrankedStudyMember(
                        membership.membershipId(),
                        membership.userId(),
                        duration.studySeconds()
                ));
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

    private record UnrankedStudyMember(
            Long cohortMembershipId,
            UUID userId,
            long studySeconds
    ) {
    }

    private record RankedStudyMember(
            Long cohortMembershipId,
            UUID userId,
            long rank,
            long studySeconds
    ) {
    }

    private record StudyRankingRows(
            long rankedMemberCount,
            List<RankedStudyMember> leaders,
            Optional<RankedStudyMember> focusedMember
    ) {

        private StudyRankingRows {
            leaders = List.copyOf(leaders);
            Objects.requireNonNull(focusedMember, "focusedMember must not be null");
        }
    }
}
