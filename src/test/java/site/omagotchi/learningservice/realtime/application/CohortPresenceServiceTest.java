package site.omagotchi.learningservice.realtime.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.global.auth.GlobalRole;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Redis cohort Presence 서비스")
class CohortPresenceServiceTest {

    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");
    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long COHORT_ID = 7L;
    private static final String SESSION_ID = "session-1";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final CohortAccessService cohortAccessService = mock(CohortAccessService.class);
    private final PresenceUserProfileQuery presenceUserProfileQuery = mock(PresenceUserProfileQuery.class);
    private final CohortPresenceService service = new CohortPresenceService(
            redisTemplate,
            messagingTemplate,
            cohortAccessService,
            new PresenceProperties(Duration.ofSeconds(60)),
            presenceUserProfileQuery
    );

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(presenceUserProfileQuery.findByUserIds(anyCollection())).willReturn(Map.of());
    }

    @Test
    @DisplayName("CONNECT 세션을 Redis hash/set/value에 등록하고 TTL을 설정한다")
    void registersSessionWithTtl() {
        // Given
        given(cohortAccessService.requireCurrentActiveMembership(USER_ID)).willReturn(activeMembership());
        given(setOperations.members("presence:cohort:7")).willReturn(Set.of(USER_ID.toString()));
        given(setOperations.members("presence:user:" + USER_ID + ":sessions")).willReturn(Set.of(SESSION_ID));
        given(redisTemplate.hasKey("realtime:session:" + SESSION_ID)).willReturn(true);
        given(valueOperations.get("presence:user:" + USER_ID)).willReturn("ONLINE");

        // When
        service.registerSession(SESSION_ID, user());

        // Then
        verify(hashOperations).putAll(
                eq("realtime:session:" + SESSION_ID),
                eq(Map.of("userId", USER_ID.toString(), "cohortId", COHORT_ID.toString()))
        );
        verify(redisTemplate).expire("realtime:session:" + SESSION_ID, Duration.ofSeconds(60));
        verify(setOperations).add("presence:user:" + USER_ID + ":sessions", SESSION_ID);
        verify(valueOperations).set("presence:user:" + USER_ID, "ONLINE");
        verify(setOperations).add("presence:cohort:7", USER_ID.toString());
        verify(messagingTemplate).convertAndSend(eq(RealtimeDestinations.cohortPresenceTopic(COHORT_ID)), any(CohortPresenceSnapshot.class));
    }

    @Test
    @DisplayName("여러 세션 중 하나가 종료되어도 사용자를 OFFLINE으로 만들지 않는다")
    void keepsUserOnlineWhenAnotherSessionRemains() {
        // Given
        given(hashOperations.get("realtime:session:" + SESSION_ID, "userId")).willReturn(USER_ID.toString());
        given(hashOperations.get("realtime:session:" + SESSION_ID, "cohortId")).willReturn(COHORT_ID.toString());
        given(setOperations.members("presence:user:" + USER_ID + ":sessions")).willReturn(Set.of("session-2"));
        given(redisTemplate.hasKey("realtime:session:session-2")).willReturn(true);
        given(setOperations.members("presence:cohort:7")).willReturn(Set.of(USER_ID.toString()));
        given(valueOperations.get("presence:user:" + USER_ID)).willReturn("ONLINE");

        // When
        service.disconnectSession(SESSION_ID, null);

        // Then
        verify(redisTemplate).delete("realtime:session:" + SESSION_ID);
        verify(setOperations).remove("presence:user:" + USER_ID + ":sessions", SESSION_ID);
        verify(valueOperations, never()).set("presence:user:" + USER_ID, "OFFLINE");
        verify(setOperations, never()).remove("presence:cohort:7", USER_ID.toString());
    }

    @Test
    @DisplayName("heartbeat는 서버 저장 session의 TTL을 갱신하고 snapshot을 재방송하지 않는다")
    void heartbeatRefreshesSessionTtl() {
        // Given
        given(hashOperations.get("realtime:session:" + SESSION_ID, "userId")).willReturn(USER_ID.toString());
        given(hashOperations.get("realtime:session:" + SESSION_ID, "cohortId")).willReturn(COHORT_ID.toString());
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID)).willReturn(15L);

        // When
        service.heartbeat(SESSION_ID, user());

        // Then
        verify(redisTemplate).expire("realtime:session:" + SESSION_ID, Duration.ofSeconds(60));
        verify(setOperations).add("presence:user:" + USER_ID + ":sessions", SESSION_ID);
        verify(valueOperations).set("presence:user:" + USER_ID, "ONLINE");
        verify(setOperations).add("presence:cohort:7", USER_ID.toString());
        verify(messagingTemplate, never()).convertAndSend(eq(RealtimeDestinations.cohortPresenceTopic(COHORT_ID)), any(CohortPresenceSnapshot.class));
    }

    @Test
    @DisplayName("마지막 유효 세션이 종료되면 사용자를 OFFLINE으로 만들고 cohort set에서 제거한다")
    void marksOfflineWhenLastSessionDisconnects() {
        // Given
        given(hashOperations.get("realtime:session:" + SESSION_ID, "userId")).willReturn(USER_ID.toString());
        given(hashOperations.get("realtime:session:" + SESSION_ID, "cohortId")).willReturn(COHORT_ID.toString());
        given(setOperations.members("presence:user:" + USER_ID + ":sessions")).willReturn(Set.of());
        given(setOperations.members("presence:cohort:7")).willReturn(Set.of());

        // When
        service.disconnectSession(SESSION_ID, null);

        // Then
        verify(valueOperations).set("presence:user:" + USER_ID, "OFFLINE");
        verify(setOperations).remove("presence:cohort:7", USER_ID.toString());
    }

    @Test
    @DisplayName("snapshot 조회 시 만료된 세션을 정리하고 온라인 목록에서 제외한다")
    void cleansExpiredSessionsWhenSnapshotIsRequested() {
        // Given
        given(setOperations.members("presence:cohort:7")).willReturn(Set.of(USER_ID.toString()));
        given(setOperations.members("presence:user:" + USER_ID + ":sessions")).willReturn(Set.of("expired-session"));
        given(redisTemplate.hasKey("realtime:session:expired-session")).willReturn(false);

        // When
        CohortPresenceSnapshot snapshot = service.snapshot(COHORT_ID);

        // Then
        then(snapshot.users()).isEmpty();
        verify(setOperations).remove("presence:user:" + USER_ID + ":sessions", "expired-session");
        verify(valueOperations).set("presence:user:" + USER_ID, "OFFLINE");
        verify(setOperations).remove("presence:cohort:7", USER_ID.toString());
    }

    @Test
    @DisplayName("snapshot에 닉네임과 대표 캐릭터 정보를 포함한다")
    void enrichesSnapshotWithNicknameAndCharacter() {
        given(setOperations.members("presence:cohort:7")).willReturn(Set.of(USER_ID.toString()));
        given(setOperations.members("presence:user:" + USER_ID + ":sessions")).willReturn(Set.of(SESSION_ID));
        given(redisTemplate.hasKey("realtime:session:" + SESSION_ID)).willReturn(true);
        given(valueOperations.get("presence:user:" + USER_ID)).willReturn("ONLINE");
        given(presenceUserProfileQuery.findByUserIds(java.util.List.of(USER_ID))).willReturn(Map.of(
                USER_ID,
                new PresenceUserProfile(
                        "오마",
                        new PresenceCharacterSnapshot("night", "pistachio", "night/pistachio")
                )
        ));

        CohortPresenceSnapshot snapshot = service.snapshot(COHORT_ID);

        then(snapshot.users()).containsExactly(new PresenceUserSnapshot(
                USER_ID,
                "오마",
                new PresenceCharacterSnapshot("night", "pistachio", "night/pistachio"),
                PresenceStatus.ONLINE
        ));
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(USER_ID, GlobalRole.USER);
    }

    private CohortMembership activeMembership() {
        return CohortMembership.activeManager(COHORT_ID, USER_ID, ADMIN_ID);
    }
}
