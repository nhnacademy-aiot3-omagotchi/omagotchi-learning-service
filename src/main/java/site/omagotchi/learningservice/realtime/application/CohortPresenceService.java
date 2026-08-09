package site.omagotchi.learningservice.realtime.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.realtime.config.PresenceProperties;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis에 cohort Presence 상태를 저장하고 STOMP topic으로 snapshot 변경을 발행한다.
 *
 * <p>Presence는 물리 출석과 별개의 ephemeral 상태이며, WebSocket session TTL과 multi-session set으로 관리한다.</p>
 */
@Service
@RequiredArgsConstructor
public class CohortPresenceService {

    private static final String USER_ID_FIELD = "userId";
    private static final String COHORT_ID_FIELD = "cohortId";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CohortAccessService cohortAccessService;
    private final PresenceProperties presenceProperties;

    /**
     * WebSocket CONNECT 성공 시 session hash, user session set, cohort online set을 갱신한다.
     */
    public void registerSession(String sessionId, AuthenticatedUser user) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        CohortMembership membership = cohortAccessService.requireCurrentActiveMembership(user.userId());
        String userId = user.userId().toString();
        String cohortId = membership.getCohortId().toString();
        removeFromPreviousCohortIfChanged(userId, cohortId);

        // session hash만 TTL을 갖고, 만료된 hash는 snapshot/cleanup 시 user session set에서 제거한다.
        redisTemplate.opsForHash().putAll(
                sessionKey(sessionId),
                Map.of(USER_ID_FIELD, userId, COHORT_ID_FIELD, cohortId)
        );
        redisTemplate.expire(sessionKey(sessionId), presenceProperties.sessionTtl());
        redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId);
        redisTemplate.opsForValue().set(userPresenceKey(userId), PresenceStatus.ONLINE.name());
        redisTemplate.opsForValue().set(userCohortKey(userId), cohortId);
        redisTemplate.opsForSet().add(cohortPresenceKey(cohortId), userId);
        broadcastSnapshot(membership.getCohortId());
    }

    public void heartbeat(String sessionId, AuthenticatedUser user) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Optional<SessionPresence> sessionPresence = findSession(sessionId);
        if (sessionPresence.isPresent()) {
            SessionPresence session = sessionPresence.get();
            if (!session.userId().equals(user.userId())) {
                throw new AccessDeniedException("WebSocket session does not belong to the authenticated user");
            }
            // client payload 없이 Redis에 저장된 cohort와 JWT 사용자를 다시 검증한 뒤 TTL만 연장한다.
            cohortAccessService.requireActiveMembershipId(session.cohortId(), user.userId());
            redisTemplate.expire(sessionKey(sessionId), presenceProperties.sessionTtl());
            redisTemplate.opsForSet().add(userSessionsKey(user.userId().toString()), sessionId);
            redisTemplate.opsForValue().set(userPresenceKey(user.userId().toString()), PresenceStatus.ONLINE.name());
            redisTemplate.opsForSet().add(cohortPresenceKey(session.cohortId().toString()), user.userId().toString());
            return;
        }

        registerSession(sessionId, user);
    }

    public void disconnectSession(String sessionId, UUID fallbackUserId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Optional<SessionPresence> sessionPresence = findSession(sessionId);
        if (sessionPresence.isPresent()) {
            SessionPresence session = sessionPresence.get();
            removeSession(sessionId, session.userId(), session.cohortId());
            return;
        }

        if (fallbackUserId != null) {
            removeFallbackSession(sessionId, fallbackUserId);
        }
    }

    public CohortPresenceSnapshot currentUserSnapshot(UUID userId) {
        CohortMembership membership = cohortAccessService.requireCurrentActiveMembership(userId);
        return snapshot(membership.getCohortId());
    }

    public CohortPresenceSnapshot snapshot(Long cohortId) {
        Set<String> userIds = redisTemplate.opsForSet().members(cohortPresenceKey(cohortId.toString()));
        if (userIds == null || userIds.isEmpty()) {
            return new CohortPresenceSnapshot(cohortId, java.util.List.of(), now());
        }

        var users = userIds.stream()
                .map(userId -> cleanupAndSnapshotUser(userId, cohortId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(snapshot -> snapshot.userId().toString()))
                .toList();
        return new CohortPresenceSnapshot(cohortId, users, now());
    }

    private Optional<PresenceUserSnapshot> cleanupAndSnapshotUser(String userId, Long cohortId) {
        if (!hasValidSession(userId)) {
            redisTemplate.opsForValue().set(userPresenceKey(userId), PresenceStatus.OFFLINE.name());
            redisTemplate.delete(userCohortKey(userId));
            redisTemplate.opsForSet().remove(cohortPresenceKey(cohortId.toString()), userId);
            return Optional.empty();
        }

        PresenceStatus status = Optional.ofNullable(redisTemplate.opsForValue().get(userPresenceKey(userId)))
                .map(PresenceStatus::valueOf)
                .orElse(PresenceStatus.ONLINE);
        return Optional.of(new PresenceUserSnapshot(UUID.fromString(userId), status));
    }

    private boolean hasValidSession(String userId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return false;
        }

        boolean validSessionFound = false;
        for (String sessionId : sessionIds) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(sessionId)))) {
                validSessionFound = true;
            } else {
                redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
            }
        }
        return validSessionFound;
    }

    private void removeSession(String sessionId, UUID userId, Long cohortId) {
        String userIdValue = userId.toString();
        redisTemplate.delete(sessionKey(sessionId));
        redisTemplate.opsForSet().remove(userSessionsKey(userIdValue), sessionId);

        if (!hasValidSession(userIdValue)) {
            redisTemplate.opsForValue().set(userPresenceKey(userIdValue), PresenceStatus.OFFLINE.name());
            redisTemplate.delete(userCohortKey(userIdValue));
            redisTemplate.opsForSet().remove(cohortPresenceKey(cohortId.toString()), userIdValue);
        }
        broadcastSnapshot(cohortId);
    }

    private void removeFallbackSession(String sessionId, UUID fallbackUserId) {
        String userId = fallbackUserId.toString();
        try {
            CohortMembership membership = cohortAccessService.requireCurrentActiveMembership(fallbackUserId);
            removeSession(sessionId, fallbackUserId, membership.getCohortId());
        } catch (BusinessException exception) {
            redisTemplate.delete(sessionKey(sessionId));
            redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
            if (!hasValidSession(userId)) {
                redisTemplate.opsForValue().set(userPresenceKey(userId), PresenceStatus.OFFLINE.name());
                redisTemplate.delete(userCohortKey(userId));
            }
        }
    }

    private void removeFromPreviousCohortIfChanged(String userId, String currentCohortId) {
        String previousCohortId = redisTemplate.opsForValue().get(userCohortKey(userId));
        if (previousCohortId == null || previousCohortId.equals(currentCohortId)) {
            return;
        }
        redisTemplate.opsForSet().remove(cohortPresenceKey(previousCohortId), userId);
        broadcastSnapshot(Long.valueOf(previousCohortId));
    }

    private Optional<SessionPresence> findSession(String sessionId) {
        Object userId = redisTemplate.opsForHash().get(sessionKey(sessionId), USER_ID_FIELD);
        Object cohortId = redisTemplate.opsForHash().get(sessionKey(sessionId), COHORT_ID_FIELD);
        if (userId == null || cohortId == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionPresence(UUID.fromString(userId.toString()), Long.valueOf(cohortId.toString())));
    }

    private void broadcastSnapshot(Long cohortId) {
        messagingTemplate.convertAndSend(RealtimeDestinations.cohortPresenceTopic(cohortId), snapshot(cohortId));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String sessionKey(String sessionId) {
        return "realtime:session:" + sessionId;
    }

    private String userSessionsKey(String userId) {
        return "presence:user:" + userId + ":sessions";
    }

    private String userPresenceKey(String userId) {
        return "presence:user:" + userId;
    }

    private String userCohortKey(String userId) {
        return "presence:user:" + userId + ":cohort";
    }

    private String cohortPresenceKey(String cohortId) {
        return "presence:cohort:" + cohortId;
    }

    private record SessionPresence(UUID userId, Long cohortId) {
        private SessionPresence {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(cohortId, "cohortId");
        }
    }
}
