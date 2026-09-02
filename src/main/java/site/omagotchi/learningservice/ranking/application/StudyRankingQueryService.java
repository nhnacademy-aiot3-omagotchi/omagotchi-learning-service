package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.GamificationProgressionService;
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
import site.omagotchi.learningservice.team.application.CurrentTeamMembershipQueryService;
import site.omagotchi.learningservice.team.application.result.CurrentTeamMembershipView;

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
    private final GamificationProgressionService gamificationProgressionService;
    private final CurrentTeamMembershipQueryService currentTeamMembershipQueryService;
    private final Clock clock;

    // 기수의 활성 학생 전체를 대상으로 오늘 실시간 개인 공부 순위를 조회한다.
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

    // 기수의 활성 학생 전체를 대상으로 종료된 집계일의 개인 공부 순위를 조회한다.
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

    // 특정 팀의 현재 구성원만 대상으로 오늘 실시간 개인 공부 순위를 조회한다.
    public TodayStudyRankingResult<MemberStudyRankingViewResult> getTodayTeamMemberView(
            UUID userId,
            Long cohortId,
            Long teamId,
            StudyRankingQuery query
    ) {
        // TODO: 팀 내부 랭킹을 팀원 전용으로 제한할 때 요청 membership의 teamId 소속을 검증한다.
        Long membershipId = cohortAccessService.requireActiveStudentMembershipId(
                cohortId,
                userId
        );
        Instant calculatedAt = clock.instant();
        int maxRank = query.resolveMaxRank();
        List<CohortMembershipView> memberships = findCurrentTeamMembers(cohortId, teamId);
        StudyRankingRows rows = findTodayRankingRows(
                memberships,
                calculatedAt,
                maxRank,
                membershipId,
                true
        );
        return new TodayStudyRankingResult<>(
                AggregationDateTime.aggregationDate(calculatedAt),
                calculatedAt,
                memberViewResult(rows)
        );
    }

    // 특정 팀의 현재 구성원만 대상으로 종료된 집계일의 개인 공부 순위를 조회한다.
    public HistoricalStudyRankingResult<MemberStudyRankingViewResult> getHistoricalTeamMemberView(
            UUID userId,
            Long cohortId,
            Long teamId,
            StudyRankingPeriodSelection period,
            StudyRankingQuery query
    ) {
        // TODO: 팀 내부 랭킹을 팀원 전용으로 제한할 때 요청 membership의 teamId 소속을 검증한다.
        Long membershipId = cohortAccessService.requireActiveStudentMembershipId(
                cohortId,
                userId
        );
        StudyRankingWindow window = resolveWindow(period);
        int maxRank = query.resolveMaxRank();
        List<CohortMembershipView> memberships = findCurrentTeamMembers(cohortId, teamId);
        StudyRankingRows rows = findHistoricalRankingRows(
                memberships,
                window,
                maxRank,
                membershipId,
                true
        );
        return new HistoricalStudyRankingResult<>(
                window.startDate(),
                window.includedThroughDate(),
                memberViewResult(rows)
        );
    }

    // 요청 기간을 현재 집계일 기준으로 확정 기록만 포함하는 조회 구간으로 변환한다.
    private StudyRankingWindow resolveWindow(StudyRankingPeriodSelection period) {
        if (period == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        Instant calculatedAt = clock.instant();
        LocalDate currentAggregationDate = AggregationDateTime.aggregationDate(calculatedAt);
        return period.resolve(currentAggregationDate);
    }

    // 기수의 활성 학생 후보를 조회한 뒤 오늘 공부시간 계산 흐름에 전달한다.
    private StudyRankingRows findTodayRankingRows(
            Long cohortId,
            Instant calculatedAt,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        List<CohortMembershipView> memberships = cohortMembershipQueryService
                .findActiveStudentMemberships(cohortId);
        return findTodayRankingRows(
                memberships,
                calculatedAt,
                maxRank,
                focusedMembershipId,
                includeLeaders
        );
    }

    // 주어진 후보의 확정 기록과 실행 중 타이머를 합산해 오늘 순위 행을 만든다.
    private StudyRankingRows findTodayRankingRows(
            List<CohortMembershipView> memberships,
            Instant calculatedAt,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        if (memberships.isEmpty()) {
            return rankingRows(
                    memberships,
                    List.of(),
                    maxRank,
                    focusedMembershipId,
                    includeLeaders
            );
        }
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

    // 기수의 활성 학생 후보를 조회한 뒤 과거 공부시간 계산 흐름에 전달한다.
    private StudyRankingRows findHistoricalRankingRows(
            Long cohortId,
            StudyRankingWindow window,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        List<CohortMembershipView> memberships = cohortMembershipQueryService
                .findActiveStudentMemberships(cohortId);
        return findHistoricalRankingRows(
                memberships,
                window,
                maxRank,
                focusedMembershipId,
                includeLeaders
        );
    }

    // 주어진 후보의 종료된 집계일 확정 기록만 합산해 과거 순위 행을 만든다.
    private StudyRankingRows findHistoricalRankingRows(
            List<CohortMembershipView> memberships,
            StudyRankingWindow window,
            int maxRank,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        if (memberships.isEmpty()) {
            return rankingRows(
                    memberships,
                    List.of(),
                    maxRank,
                    focusedMembershipId,
                    includeLeaders
            );
        }
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

    // 전체 순위에서 노출 상위권과 요청자 자신의 순위 행을 각각 선택한다.
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

    // membership과 공부시간을 결합해 양수 시간의 competition ranking을 계산한다.
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
        List<UnrankedStudyMember> unrankedMembers = durations.stream()
                .map(duration -> toUnrankedMember(duration, membershipById))
                .flatMap(Optional::stream)
                .toList();
        return CompetitionRanking.rank(
                        unrankedMembers,
                        UnrankedStudyMember::studySeconds,
                        Comparator.comparing(UnrankedStudyMember::cohortMembershipId)
                ).stream()
                .map(ranked -> new RankedStudyMember(
                        ranked.value().cohortMembershipId(),
                        ranked.value().userId(),
                        ranked.rank(),
                        ranked.value().studySeconds(),
                        ranked.value().timerRunning()
                ))
                .toList();
    }

    // 공부시간 결과를 같은 membership의 사용자 정보와 결합할 수 있을 때만 후보로 만든다.
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

    // 계산된 순위 행에 표시명과 캐릭터 외형을 결합해 목록과 내 순위 결과를 함께 조립한다.
    private MemberStudyRankingViewResult memberViewResult(StudyRankingRows rows) {
        RankingProfiles profiles = findProfiles(rows);
        return new MemberStudyRankingViewResult(
                boardResult(rows, profiles),
                mineResult(rows, profiles)
        );
    }

    // 노출 대상으로 선택된 상위 순위 행을 개인 랭킹 목록 결과로 변환한다.
    private StudyRankingBoardResult boardResult(
            StudyRankingRows rows,
            RankingProfiles profiles
    ) {
        List<StudyRankingEntryResult> entries = rows.leaders().stream()
                .map(row -> entryResult(row, profiles))
                .toList();
        return new StudyRankingBoardResult(
                rows.rankedMemberCount(),
                entries
        );
    }

    // 전체 순위 수와 요청자의 선택적 순위 행을 내 랭킹 결과로 변환한다.
    private MyStudyRankingResult mineResult(
            StudyRankingRows rows,
            RankingProfiles profiles
    ) {
        Optional<StudyRankingEntryResult> ranking = rows.focusedMember()
                .map(row -> entryResult(row, profiles));
        return new MyStudyRankingResult(
                rows.rankedMemberCount(),
                ranking
        );
    }

    // 내부 개인 순위 행에 대표 캐릭터 표시명과 외형, 출석 스트릭을 붙여 Application 결과로 변환한다.
    private StudyRankingEntryResult entryResult(
            RankedStudyMember row,
            RankingProfiles profiles
    ) {
        // 대표 캐릭터가 없어도 순위에서 빼지 않는다. 이름과 외형만 비운 채로 내려보낸다.
        RepresentativeCharacterResult character = profiles.charactersByUserId().get(row.userId());
        return new StudyRankingEntryResult(
                row.rank(),
                character == null ? null : character.displayName(),
                row.studySeconds(),
                row.timerRunning(),
                character == null ? null : character.characterType(),
                character == null ? null : character.colorId(),
                profiles.streakDaysByUserId().getOrDefault(row.userId(), 0)
        );
    }

    // 상위권과 요청자에게 필요한 대표 캐릭터와 출석 스트릭만 각각 한 번에 조회한다.
    private RankingProfiles findProfiles(StudyRankingRows rows) {
        Collection<UUID> userIds = new LinkedHashSet<>();
        rows.leaders().stream()
                .map(RankedStudyMember::userId)
                .forEach(userIds::add);
        rows.focusedMember()
                .map(RankedStudyMember::userId)
                .ifPresent(userIds::add);
        if (userIds.isEmpty()) {
            return RankingProfiles.empty();
        }
        Map<UUID, RepresentativeCharacterResult> charactersByUserId = characterGrowthService
                .findRepresentativeCharacters(userIds)
                .stream()
                .collect(Collectors.toMap(
                        RepresentativeCharacterResult::userId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        return new RankingProfiles(
                charactersByUserId,
                gamificationProgressionService.findWeekdayStreakDays(userIds)
        );
    }

    // 순위 행에 붙일 사용자별 부가 정보를 한 묶음으로 옮긴다. 인자 목록이 길어지는 것을 막는다.
    private record RankingProfiles(
            Map<UUID, RepresentativeCharacterResult> charactersByUserId,
            Map<UUID, Integer> streakDaysByUserId
    ) {

        private static RankingProfiles empty() {
            return new RankingProfiles(Map.of(), Map.of());
        }

        private RankingProfiles {
            charactersByUserId = Map.copyOf(charactersByUserId);
            streakDaysByUserId = Map.copyOf(streakDaysByUserId);
        }
    }

    // 기수 소속 뷰에서 공부시간 배치 조회에 사용할 membership 식별자만 추출한다.
    private List<Long> membershipIds(List<CohortMembershipView> memberships) {
        return memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
    }

    // 기수의 활성 학생 중 요청한 활성 팀에 현재 소속된 학생만 후보로 남긴다.
    private List<CohortMembershipView> findCurrentTeamMembers(Long cohortId, Long teamId) {
        List<CohortMembershipView> memberships = cohortMembershipQueryService
                .findActiveStudentMemberships(cohortId);
        Set<Long> currentTeamMembershipIds = currentTeamMembershipQueryService
                .findCurrentMemberships(cohortId, membershipIds(memberships))
                .stream()
                .filter(membership -> Objects.equals(membership.teamId(), teamId))
                .map(CurrentTeamMembershipView::cohortMembershipId)
                .collect(Collectors.toUnmodifiableSet());
        return memberships.stream()
                .filter(membership -> currentTeamMembershipIds.contains(
                        membership.membershipId()
                ))
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

        // 외부 리스트 변경이 계산된 순위 행에 영향을 주지 않도록 방어적 복사한다.
        private StudyRankingRows {
            leaders = List.copyOf(leaders);
            Objects.requireNonNull(focusedMember, "focusedMember must not be null");
        }
    }
}
