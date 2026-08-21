package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TeamStudyRankingQueryService {

    private final CohortAccessService cohortAccessService;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final CurrentTeamMembershipQueryService currentTeamMembershipQueryService;
    private final StudyRecordAggregationQueryService studyRecordAggregationQueryService;
    private final Clock clock;

    // 오늘 확정 기록 + 실행 중 타이머를 팀 별로 합산하여 순위 출력
    public TodayStudyRankingResult<TeamStudyRankingViewResult> getTodayTeamView(
            UUID userId,
            Long cohortId,
            StudyRankingQuery query
    ) {
        Long membershipId = cohortAccessService.requireActiveStudentMembershipId(
                cohortId,
                userId
        );

        Instant calculatedAt = clock.instant();
        int maxRank = query.resolveMaxRank();
        TeamRankingInput input = currentTeamInput(cohortId);

        List<MemberCurrentStudyDurationResult> durations = input.membershipIds().isEmpty()
                ? List.of()
                : studyRecordAggregationQueryService.getCurrentDurations(
                        input.membershipIds(),
                        calculatedAt
                );

        TeamStudyRankingViewResult ranking = rank(
                input.teamMemberships(),
                durations.stream()
                        .map(duration -> new TeamRankingDuration(
                                duration.cohortMembershipId(),
                                duration.studySeconds()
                        ))
                        .toList(),
                maxRank,
                membershipId
        );

        return new TodayStudyRankingResult<>(
                AggregationDateTime.aggregationDate(calculatedAt),
                calculatedAt,
                ranking
        );
    }

    // 종료된 집계일의 확정 기록만 현재 팀별로 합산해 요청 기간의 순위를 조회한다.
    public HistoricalStudyRankingResult<TeamStudyRankingViewResult> getHistoricalTeamView(
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

        int maxRank = query.resolveMaxRank();
        TeamRankingInput input = currentTeamInput(cohortId);

        List<MemberStudyDurationResult> durations = window.includedThroughDate()
                .filter(ignored -> !input.membershipIds().isEmpty())
                .map(endDate -> studyRecordAggregationQueryService.getConfirmedDurations(
                        input.membershipIds(),
                        window.startDate(),
                        endDate
                ))
                .orElseGet(List::of);

        TeamStudyRankingViewResult ranking = rank(
                input.teamMemberships(),
                durations.stream()
                        .map(duration -> new TeamRankingDuration(
                                duration.cohortMembershipId(),
                                duration.studySeconds()
                        ))
                        .toList(),
                maxRank,
                membershipId
        );

        return new HistoricalStudyRankingResult<>(
                window.startDate(),
                window.includedThroughDate(),
                ranking
        );
    }

    // 요청 기간을 현재 집계일 기준으로 확정 기록만 포함하는 조회 구간으로 변환한다.
    private StudyRankingWindow resolveWindow(StudyRankingPeriodSelection period) {
        if (period == null) {
            throw new IllegalArgumentException("period가 null입니다.");
        }

        LocalDate currentAggregationDate =
                AggregationDateTime.aggregationDate(clock.instant());
        return period.resolve(currentAggregationDate);
    }

    // 활성 학생을 현재 팀 소속과 연결해 팀별 합산에 사용할 후보만 구성한다.
    private TeamRankingInput currentTeamInput(Long cohortId) {
        List<CohortMembershipView> memberships = cohortMembershipQueryService
                .findActiveStudentMemberships(cohortId);
        List<CurrentTeamMembershipView> teamMemberships = currentTeamMembershipQueryService
                .findCurrentMemberships(cohortId, membershipIds(memberships));
        return new TeamRankingInput(
                teamMemberships,
                teamMemberships.stream()
                        .map(CurrentTeamMembershipView::cohortMembershipId)
                        .distinct()
                        .toList()
        );
    }

    // 멤버별 공부시간을 팀별로 합산하고 상위 팀과 요청자의 현재 팀 순위를 함께 선택한다.
    private TeamStudyRankingViewResult rank(
            List<CurrentTeamMembershipView> teamMemberships,
            List<TeamRankingDuration> durations,
            int maxRank,
            Long focusedMembershipId
    ) {
        Map<Long, Long> durationByMembershipId = durations.stream()
                .collect(Collectors.toMap(
                        TeamRankingDuration::cohortMembershipId,
                        TeamRankingDuration::studySeconds,
                        Math::addExact
                ));
        Map<Long, UnrankedTeam> teamsById = new HashMap<>();
        teamMemberships.forEach(membership -> teamsById.compute(
                membership.teamId(),
                (teamId, current) -> addTeamDuration(
                        current,
                        membership,
                        durationByMembershipId.getOrDefault(
                                membership.cohortMembershipId(),
                                0L
                        )
                )
        ));

        List<CompetitionRanking.Ranked<UnrankedTeam>> rankedTeams =
                CompetitionRanking.rank(
                        teamsById.values(),
                        UnrankedTeam::studySeconds,
                        Comparator.comparing(UnrankedTeam::teamId)
                );
        List<TeamStudyRankingEntryResult> rankedEntries = rankedTeams.stream()
                .map(this::entryResult)
                .toList();
        List<TeamStudyRankingEntryResult> leaders = rankedEntries.stream()
                .filter(entry -> entry.rank() <= maxRank)
                .toList();
        Optional<Long> focusedTeamId = teamMemberships.stream()
                .filter(membership -> Objects.equals(
                        membership.cohortMembershipId(),
                        focusedMembershipId
                ))
                .map(CurrentTeamMembershipView::teamId)
                .findFirst();
        Optional<TeamStudyRankingEntryResult> focusedTeam = focusedTeamId.flatMap(teamId ->
                rankedEntries.stream()
                        .filter(entry -> Objects.equals(entry.teamId(), teamId))
                        .findFirst()
        );

        return new TeamStudyRankingViewResult(
                new TeamStudyRankingBoardResult(rankedEntries.size(), leaders),
                new MyTeamStudyRankingResult(focusedTeam)
        );
    }

    // 같은 팀에 속한 멤버의 공부시간을 오버플로 검사와 함께 누적한다.
    private UnrankedTeam addTeamDuration(
            UnrankedTeam current,
            CurrentTeamMembershipView membership,
            long studySeconds
    ) {
        if (current == null) {
            return new UnrankedTeam(
                    membership.teamId(),
                    membership.teamName(),
                    studySeconds
            );
        }
        return new UnrankedTeam(
                current.teamId(),
                current.teamName(),
                Math.addExact(current.studySeconds(), studySeconds)
        );
    }

    // 내부 팀 순위 행을 Application 결과 형식으로 변환한다.
    private TeamStudyRankingEntryResult entryResult(
            CompetitionRanking.Ranked<UnrankedTeam> ranked
    ) {
        return new TeamStudyRankingEntryResult(
                ranked.rank(),
                ranked.value().teamId(),
                ranked.value().teamName(),
                ranked.value().studySeconds()
        );
    }

    // 기수 소속 뷰에서 배치 조회에 사용할 membership 식별자만 추출한다.
    private List<Long> membershipIds(List<CohortMembershipView> memberships) {
        return memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
    }

    private record TeamRankingInput(
            List<CurrentTeamMembershipView> teamMemberships,
            List<Long> membershipIds
    ) {

        // 외부 리스트 변경이 계산 입력에 영향을 주지 않도록 방어적 복사한다.
        private TeamRankingInput {
            teamMemberships = List.copyOf(teamMemberships);
            membershipIds = List.copyOf(membershipIds);
        }
    }

    private record TeamRankingDuration(
            Long cohortMembershipId,
            long studySeconds
    ) {
    }

    private record UnrankedTeam(
            Long teamId,
            String teamName,
            long studySeconds
    ) {
    }
}
