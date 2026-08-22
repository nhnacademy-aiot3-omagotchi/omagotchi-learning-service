package site.omagotchi.learningservice.user.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.GameCharacterRepository;
import site.omagotchi.learningservice.gamification.infrastructure.LevelPolicyRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.StudyProfileSummaryResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("내 프로필 서비스")
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long COHORT_ID = 10L;
    private static final Long MEMBERSHIP_ID = 20L;

    @Mock
    private CohortMembershipRepository cohortMembershipRepository;

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private GameCharacterRepository gameCharacterRepository;

    @Mock
    private LevelPolicyRepository levelPolicyRepository;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    @DisplayName("승인 기수, 학습 집계, 출석 스트릭, 대표 캐릭터를 조회한다")
    void returnsProfileSummary() {
        CohortMembership membership = activeMembership();
        Cohort cohort = cohort();
        UserCharacter character = representativeCharacter("오마");
        GameCharacter gameCharacter = gameCharacter();

        given(cohortMembershipRepository.findFirstByUserIdAndStatusAndEndedAtIsNullOrderByRequestedAtDesc(
                USER_ID,
                CohortMembershipStatus.ACTIVE
        )).willReturn(Optional.of(membership));
        given(studyRecordQueryRepository.summarizeActiveRecords(MEMBERSHIP_ID))
                .willReturn(new StudyProfileSummaryResult(7_200L, 2L));
        given(dateTimeProvider.currentAggregationDate()).willReturn(LocalDate.of(2026, 8, 8));
        given(attendanceRecordRepository.findDistinctAttendedDatesOnOrBefore(
                MEMBERSHIP_ID,
                LocalDate.of(2026, 8, 8)
        )).willReturn(List.of(
                LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 6),
                LocalDate.of(2026, 8, 4)
        ));
        given(userCharacterRepository.findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(USER_ID))
                .willReturn(Optional.of(character));
        given(levelPolicyRepository.findByLevelLessThanEqualOrderByLevelAsc(30))
                .willReturn(List.of(
                        LevelPolicy.create(1, 0),
                        LevelPolicy.create(2, 100),
                        LevelPolicy.create(3, 400)
                ));
        given(gameCharacterRepository.findById(1L)).willReturn(Optional.of(gameCharacter));
        given(cohortRepository.findById(COHORT_ID)).willReturn(Optional.of(cohort));

        var result = userProfileService.getMyProfile(USER_ID);

        assertAll(
                () -> assertEquals("오마", result.nickname()),
                () -> assertEquals(7_200L, result.totalStudySeconds()),
                () -> assertEquals(2L, result.completedSessionCount()),
                () -> assertEquals(2, result.attendanceStreakDays()),
                () -> assertEquals(COHORT_ID, result.approvedCohort().cohortId()),
                () -> assertEquals("백엔드 1기", result.approvedCohort().name()),
                () -> assertEquals("야간반", result.currentCharacter().name()),
                () -> assertEquals(2, result.currentCharacter().level()),
                () -> assertEquals(150L, result.currentCharacter().currentExp()),
                () -> assertEquals(300L, result.currentCharacter().requiredExp()),
                () -> assertEquals("night", result.currentCharacter().type()),
                () -> assertEquals("pistachio", result.currentCharacter().colorId()),
                () -> assertEquals("night/pistachio", result.currentCharacter().assetKey())
        );
    }

    @Test
    @DisplayName("프로필 구성 요소가 없으면 숫자 0과 null을 반환한다")
    void returnsDefaultsWhenOptionalDataDoesNotExist() {
        given(cohortMembershipRepository.findFirstByUserIdAndStatusAndEndedAtIsNullOrderByRequestedAtDesc(
                USER_ID,
                CohortMembershipStatus.ACTIVE
        )).willReturn(Optional.empty());
        given(userCharacterRepository.findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(USER_ID))
                .willReturn(Optional.empty());

        var result = userProfileService.getMyProfile(USER_ID);

        assertAll(
                () -> assertNull(result.nickname()),
                () -> assertEquals(0L, result.totalStudySeconds()),
                () -> assertEquals(0L, result.completedSessionCount()),
                () -> assertEquals(0, result.attendanceStreakDays()),
                () -> assertNull(result.approvedCohort()),
                () -> assertNull(result.currentCharacter())
        );
        verifyNoInteractions(studyRecordQueryRepository, attendanceRecordRepository);
    }

    /**
     * 닉네임 규칙(정규화·중복·금칙어)의 소유자는 Gamification이다.
     * 이 Feature는 위임과 응답 계약 변환만 책임지므로 그것만 검증한다.
     * 규칙 자체의 검증은 CharacterGrowthServiceTest에 있다.
     */
    @Test
    @DisplayName("닉네임 변경을 Gamification에 위임하고 결과를 응답 계약으로 감싼다")
    void delegatesNicknameChangeToGamification() {
        given(characterGrowthService.changeRepresentativeNickname(USER_ID, "  새이름  "))
                .willReturn("새이름");

        var result = userProfileService.updateNickname(USER_ID, "  새이름  ");

        assertEquals("새이름", result.nickname());
        verify(characterGrowthService).changeRepresentativeNickname(USER_ID, "  새이름  ");
    }

    @Test
    @DisplayName("Gamification이 던진 닉네임 오류를 그대로 전달한다")
    void propagatesNicknameErrorFromGamification() {
        given(characterGrowthService.changeRepresentativeNickname(USER_ID, "새이름"))
                .willThrow(new BusinessException(GamificationErrorCode.DUPLICATE_NICKNAME));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userProfileService.updateNickname(USER_ID, "새이름")
        );

        assertSame(GamificationErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
    }

    private CohortMembership activeMembership() {
        CohortMembership membership = CohortMembership.activeManager(COHORT_ID, USER_ID, USER_ID);
        ReflectionTestUtils.setField(membership, "id", MEMBERSHIP_ID);
        ReflectionTestUtils.setField(membership, "role", CohortMembershipRole.STUDENT);
        return membership;
    }

    private Cohort cohort() {
        Cohort cohort = Cohort.create(
                "백엔드 1기",
                "설명",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                USER_ID
        );
        ReflectionTestUtils.setField(cohort, "id", COHORT_ID);
        ReflectionTestUtils.setField(cohort, "status", CohortStatus.ACTIVE);
        ReflectionTestUtils.setField(cohort, "createdAt", OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        ReflectionTestUtils.setField(cohort, "updatedAt", OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        return cohort;
    }

    private UserCharacter representativeCharacter(String nickname) {
        UserCharacter character = UserCharacter.representative(USER_ID, 1L, nickname, "pistachio");
        ReflectionTestUtils.setField(character, "id", 30L);
        ReflectionTestUtils.setField(character, "totalXp", 250L);
        ReflectionTestUtils.setField(character, "level", 2);
        ReflectionTestUtils.setField(character, "advancementStage", AdvancementStage.BASE);
        return character;
    }

    private GameCharacter gameCharacter() {
        GameCharacter gameCharacter = GameCharacter.create("NIGHT_CLASS", "야간반", "기본 캐릭터", "night");
        ReflectionTestUtils.setField(gameCharacter, "id", 1L);
        return gameCharacter;
    }
}
