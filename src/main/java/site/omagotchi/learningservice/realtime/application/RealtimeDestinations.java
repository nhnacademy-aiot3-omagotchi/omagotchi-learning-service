package site.omagotchi.learningservice.realtime.application;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket/STOMP destination 규칙을 한곳에 모아 구독 인가와 발행 대상이 같은 문자열 계약을 사용하게 한다.
 */
public final class RealtimeDestinations {

    public static final String WEBSOCKET_ENDPOINT = "/ws";
    public static final String APPLICATION_PREFIX = "/app";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";
    public static final String USER_PREFIX = "/user";
    public static final String USER_NOTIFICATIONS_QUEUE = "/user/queue/notifications";
    public static final String PRESENCE_HEARTBEAT = "/app/presence/heartbeat";

    private static final Pattern COHORT_PRESENCE_TOPIC =
            Pattern.compile("^/topic/cohorts/(\\d+)/presence$");

    private RealtimeDestinations() {
    }

    public static String cohortPresenceTopic(Long cohortId) {
        return "/topic/cohorts/" + cohortId + "/presence";
    }

    public static Optional<Long> cohortIdFromPresenceTopic(String destination) {
        if (destination == null) {
            return Optional.empty();
        }

        Matcher matcher = COHORT_PRESENCE_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
