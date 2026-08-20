package site.omagotchi.learningservice.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;
import site.omagotchi.learningservice.gamification.domain.CharacterAppearance;
import site.omagotchi.learningservice.gamification.domain.CharacterNicknameValidator;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.GameCharacterRepository;
import site.omagotchi.learningservice.gamification.infrastructure.LevelPolicyRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.StudyProfileSummaryResult;
import site.omagotchi.learningservice.user.application.result.ApprovedCohortResult;
import site.omagotchi.learningservice.user.application.result.CurrentCharacterResult;
import site.omagotchi.learningservice.user.application.result.UserNicknameResult;
import site.omagotchi.learningservice.user.application.result.UserProfileResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 대표 UserCharacter를 기준으로 닉네임과 게임화 상태를 조합하고,
 * 학습/출석/승인 기수 요약을 집계하는 애플리케이션 서비스다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final CohortMembershipRepository cohortMembershipRepository;
    private final CohortRepository cohortRepository;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserCharacterRepository userCharacterRepository;
    private final GameCharacterRepository gameCharacterRepository;
    private final LevelPolicyRepository levelPolicyRepository;
    private final CharacterGrowthService characterGrowthService;
    private final DateTimeProvider dateTimeProvider;

    /**
     * 현재 사용자 프로필에 필요한 데이터를 기존 도메인에서 모아 DTO 결과로 반환한다.
     */
    public UserProfileResult getMyProfile(UUID userId) {
        Optional<CohortMembership> approvedMembership = findApprovedMembership(userId);
        StudyProfileSummaryResult studySummary = approvedMembership
                .map(CohortMembership::getId)
                .map(studyRecordQueryRepository::summarizeActiveRecords)
                .orElse(new StudyProfileSummaryResult(0L, 0L));
        int attendanceStreakDays = approvedMembership
                .map(CohortMembership::getId)
                .map(this::calculateAttendanceStreakDays)
                .orElse(0);

        Optional<UserCharacter> representativeCharacter =
                userCharacterRepository.findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(userId);
        CurrentCharacterResult currentCharacter = representativeCharacter
                .map(this::toCurrentCharacterResult)
                .orElse(null);
        String nickname = representativeCharacter
                .map(UserCharacter::getNickname)
                .orElse(null);

        return new UserProfileResult(
                nickname,
                studySummary.totalStudySeconds(),
                studySummary.completedSessionCount(),
                attendanceStreakDays,
                approvedMembership.flatMap(this::toApprovedCohortResult).orElse(null),
                currentCharacter
        );
    }

    /**
     * 닉네임은 별도 설정 테이블 없이 대표 UserCharacter.nickname에 저장한다.
     */
    @Transactional
    public UserNicknameResult updateNickname(UUID userId, String nickname) {
        String normalizedNickname = normalizeNickname(nickname);
        UserCharacter character = characterGrowthService.requireRepresentativeCharacter(userId);
        if (userCharacterRepository.existsByNicknameIgnoreCaseAndRepresentativeTrueAndIdNot(
                normalizedNickname,
                character.getId()
        )) {
            throw new BusinessException(UserProfileErrorCode.DUPLICATE_NICKNAME);
        }
        character.updateNickname(normalizedNickname);
        return new UserNicknameResult(character.getNickname());
    }

    private Optional<CohortMembership> findApprovedMembership(UUID userId) {
        return cohortMembershipRepository.findFirstByUserIdAndStatusAndEndedAtIsNullOrderByRequestedAtDesc(
                userId,
                CohortMembershipStatus.ACTIVE
        );
    }

    private Optional<ApprovedCohortResult> toApprovedCohortResult(CohortMembership membership) {
        return cohortRepository.findById(membership.getCohortId())
                .map(cohort -> ApprovedCohortResult.from(membership, cohort));
    }

    private CurrentCharacterResult toCurrentCharacterResult(UserCharacter character) {
        List<LevelPolicy> policies = levelPolicyRepository.findByLevelLessThanEqualOrderByLevelAsc(30);
        if (policies.isEmpty()) {
            throw new BusinessException(GamificationErrorCode.LEVEL_POLICY_NOT_FOUND);
        }

        var levelState = character.levelState(policies);
        GameCharacter gameCharacter = gameCharacterRepository.findById(character.getGameCharacterId())
                .orElseThrow(() -> new BusinessException(GamificationErrorCode.GAME_CHARACTER_NOT_FOUND));

        return new CurrentCharacterResult(
                character.getNickname(),
                levelState.level(),
                levelState.currentLevelXp(),
                levelState.nextLevelRequiredXp(),
                gameCharacter.getName(),
                gameCharacter.getAssetKey(),
                character.getColorId(),
                CharacterAppearance.assetKey(gameCharacter.getAssetKey(), character.getColorId())
        );
    }

    private int calculateAttendanceStreakDays(Long cohortMembershipId) {
        LocalDate today = dateTimeProvider.currentAggregationDate();
        List<LocalDate> attendedDates = attendanceRecordRepository.findDistinctAttendedDatesOnOrBefore(
                cohortMembershipId,
                today
        );
        if (attendedDates.isEmpty()) {
            return 0;
        }

        Set<LocalDate> attendedDateSet = attendedDates.stream().collect(Collectors.toUnmodifiableSet());
        LocalDate cursor = attendedDateSet.contains(today) ? today : today.minusDays(1);
        int streakDays = 0;
        while (attendedDateSet.contains(cursor)) {
            streakDays++;
            cursor = cursor.minusDays(1);
        }
        return streakDays;
    }

    private String normalizeNickname(String nickname) {
        try {
            return CharacterNicknameValidator.normalize(nickname);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(UserProfileErrorCode.INVALID_NICKNAME);
        }
    }
}
