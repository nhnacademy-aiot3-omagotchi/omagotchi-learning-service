package site.omagotchi.learningservice.space.domain;

/**
 * 공간의 현재 상태에서 허용되지 않는 변경을 시도했다.
 *
 * <p>정상 사용자 흐름에서도 나오는 거절이다. 호출자가 사유를 구분해 외부 오류로 옮겨야 하므로
 * {@link Rule}을 함께 실어 보낸다 — 사유마다 예외 Class를 만들지 않는 이유이기도 하다.</p>
 *
 * <p><b>{@code IllegalStateException}을 쓰지 않는 것이 의도다.</b> 그쪽은 호출 계약·내부 상태
 * 위반을 뜻하고 일반 {@code 500}으로 흘러야 하지만, 여기 담기는 것은 사용자가 실제로 마주치는
 * 거절이라 호출자가 구분해 변환해야 한다.</p>
 *
 * <p>이 Type은 외부 오류 Code를 알지 못한다. 어떤 상태로 응답할지는 이 예외를 받는 쪽이 정한다.</p>
 */
public final class SpaceStateTransitionException extends RuntimeException {

    private final Rule violated;

    SpaceStateTransitionException(Rule violated, String message) {
        super(message);
        this.violated = violated;
    }

    /** 어떤 규칙을 어겼는가. 호출자가 외부 오류로 옮기는 기준이다. */
    public Rule violated() {
        return violated;
    }

    /**
     * 상태가 막는 변경의 종류 (RM-14).
     *
     * <p>둘 다 "활성 공간은 이용 중일 수 있다"는 같은 사실에서 나온다. 정원을 줄이거나 유형을
     * 바꾸는 순간 이미 그 공간을 쓰는 사람·점유와 어긋날 수 있어, 비활성 상태로 한정해 그
     * 경합을 원천 소멸시킨다.</p>
     */
    public enum Rule {

        /** 활성 공간의 유형 변경. */
        ACTIVE_TYPE_CHANGE,

        /** 활성 공간의 정원 축소. 늘리거나 유지하는 것은 허용된다. */
        ACTIVE_CAPACITY_REDUCTION
    }
}
