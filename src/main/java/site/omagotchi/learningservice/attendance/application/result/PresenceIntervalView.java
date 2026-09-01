package site.omagotchi.learningservice.attendance.application.result;

import site.omagotchi.learningservice.attendance.domain.PresenceState;

import java.time.Instant;

/**
 * 다른 Feature에 노출하는 "언제 어느 공간에 있었는가"의 한 구간.
 *
 * <p>{@link OpenPresenceView}가 "지금 안에 있는가"만 답하는 것과 달리, 이 레코드는 지나간
 * 구간을 그대로 돌려준다. 학습 기록에 공간을 붙이려면 세션 시각과 겹치는 구간을 찾아야
 * 하는데, 그 판단에는 시작·종료 시각과 공간이 모두 필요하기 때문이다.</p>
 *
 * <p>Java 표준 Type만 담는다 — {@code state}를 {@link PresenceState}로 내보내면 받는 쪽이
 * 출결 파트의 domain에 컴파일 의존이 생긴다. 무엇보다 <b>재실 판정은 출결 파트의 책임</b>이라,
 * 조회 단계에서 이미 자리를 비운 구간({@code AWAY})을 걸러 돌려준다. 받는 쪽이 상태값을
 * 보고 다시 판정할 일은 없고, 이 값은 "회의 중이었는지" 같은 참고용이다.</p>
 *
 * @param spaceId   그 구간에 있던 공간. 공간이 기록되지 않은 구간이면 {@code null}이다
 * @param state     구간의 상태 이름. 재실 판정에 다시 쓰지 않는다
 * @param startedAt 구간 시작 시각
 * @param endedAt   구간 종료 시각. 아직 진행 중이면 {@code null}이다
 */
public record PresenceIntervalView(
        Long spaceId,
        String state,
        Instant startedAt,
        Instant endedAt
) {

    /**
     * 조회가 도메인 enum을 그대로 넘길 수 있도록 둔 생성자.
     *
     * <p>바깥으로 나가는 값은 문자열이므로 이 생성자를 거쳐도 노출 범위는 그대로다.</p>
     */
    public PresenceIntervalView(
            Long spaceId,
            PresenceState state,
            Instant startedAt,
            Instant endedAt
    ) {
        this(spaceId, state == null ? null : state.name(), startedAt, endedAt);
    }
}
