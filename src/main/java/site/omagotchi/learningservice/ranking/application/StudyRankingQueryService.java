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
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;
import site.omagotchi.learningservice.ranking.application.result.*;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberCurrentStudyDurationResult;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
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

    public TodayStudyRankingResult<MemberStudyRankingViewResult> getTodayMemberView(
            UUID userId,
            Long cohortId,
            StudyRankingQuery query
    ) {
        Long membershipId = cohortAccessService.requireActiveStudentMembershipId(
                cohortId,
                userId
        );
        Instant calculatedAt = clock.instant();
        StudyRankingRows rows = findTodayRankingRows(
                cohortId,
                calculatedAt,
                query.resolveMaxRank(),
                membershipId,
                true
        );
        return new TodayStudyRankingResult<>(
                AggregationDateTime.aggregationDate(calculatedAt),
                calculatedAt,
                memberViewResult(rows)
        );
    }

    public HistoricalStudyRankingResult<MemberStudyRankingViewResult> getHistoricalMemberView(
            UUID userId,
            Long cohortId,
            StudyRankingPeriodSelection period,
            StudyRankingQuery query
    ) {
        Long membershipId = cohortAccessService.requireActiveStudentMembershipId(
                cohortId,
                userId
        );
        StudyRankingWindow window = resolveWindow(period);
        StudyRankingRows rows = findHistoricalRankingRows(
                cohortId,
                window,
                query.resolveMaxRank(),
                membershipId,
                true
        );
        return new HistoricalStudyRankingResult<>(
                window.startDate(),
                window.includedThroughDate(),
                memberViewResult(rows)
        );
    }

    private StudyRankingWindow resolveWindow(StudyRankingPeriodSelection period) {
        if (period == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        Instant calculatedAt = clock.instant();
        LocalDate currentAggregationDate = AggregationDateTime.aggregationDate(calculatedAt);
        return period.resolve(currentAggregationDate);
    }

    private StudyRankingRows findTodayRankingRows(
            Long cohortId,
            Instant calculatedAt,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        List<CohortMembershipView> memberships = cohortMembershipQueryService
                .findActiveStudentMemberships(cohortId);
        List<MemberCurrentStudyDurationResult> durations = studyRecordAggregationQueryService
                .getCurrentDurations(membershipIds(memberships), calculatedAt);
        return rankingRows(
                memberships,
                durations.stream()
                        .map(duration -> new RankingDuration(
                                duration.cohortMembershipId(),
                                duration.studySeconds(),
                                duration.timerRunning()
                        ))
                        .toList(),
                maxRank,
                focusedMembershipId,
                includeLeaders
        );
    }

    private StudyRankingRows findHistoricalRankingRows(
            Long cohortId,
            StudyRankingWindow window,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        List<CohortMembershipView> memberships = cohortMembershipQueryService
                .findActiveStudentMemberships(cohortId);
        List<MemberStudyDurationResult> durations = window.includedThroughDate()
                .map(endDate -> studyRecordAggregationQueryService.getConfirmedDurations(
                        membershipIds(memberships),
                        window.startDate(),
                        endDate
                ))
                .orElseGet(List::of);
        return rankingRows(
                memberships,
                durations.stream()
                        .map(duration -> new RankingDuration(
                                duration.cohortMembershipId(),
                                duration.studySeconds(),
                                false
                        ))
                        .toList(),
                maxRank,
                focusedMembershipId,
                includeLeaders
        );
    }

    private StudyRankingRows rankingRows(
            List<CohortMembershipView> memberships,
            List<RankingDuration> durations,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
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
            List<RankingDuration> durations
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
                    member.studySeconds(),
                    member.timerRunning()
            ));
        }
        return List.copyOf(rankedMembers);
    }

    private Optional<UnrankedStudyMember> toUnrankedMember(
            RankingDuration duration,
            Map<Long, CohortMembershipView> membershipById
    ) {
        return Optional.ofNullable(membershipById.get(duration.cohortMembershipId()))
                .map(membership -> new UnrankedStudyMember(
                        membership.membershipId(),
                        membership.userId(),
                        duration.studySeconds(),
                        duration.timerRunning()
                ));
    }

    private MemberStudyRankingViewResult memberViewResult(StudyRankingRows rows) {
        Map<UUID, String> displayNames = findDisplayNames(rows);
        return new MemberStudyRankingViewResult(
                boardResult(rows, displayNames),
                mineResult(rows, displayNames)
        );
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
                row.studySeconds(),
                row.timerRunning()
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

    private List<Long> membershipIds(List<CohortMembershipView> memberships) {
        return memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
    }

    private record RankingDuration(
            Long cohortMembershipId,
            long studySeconds,
            boolean timerRunning
    ) {
    }

    private record UnrankedStudyMember(
            Long cohortMembershipId,
            UUID userId,
            long studySeconds,
            boolean timerRunning
    ) {
    }

    private record RankedStudyMember(
            Long cohortMembershipId,
            UUID userId,
            long rank,
            long studySeconds,
            boolean timerRunning
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
