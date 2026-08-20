package site.omagotchi.learningservice.space.domain;

/**
 * 공간 속성 값 자체가 규칙을 어겼다 (이름 길이·공백, 유형 누락, 정원 범위).
 *
 * <p>정상 사용자 흐름에서도 나오는 거절이다. 호출자가 어떤 속성이 문제인지 구분해 외부 오류로
 * 옮겨야 하므로 {@link Attribute}를 함께 실어 보낸다 — 속성마다 예외 Class를 만들지 않는
 * 이유이기도 하다.</p>
 *
 * <p><b>{@code IllegalArgumentException}을 상속하지 않는 것이 의도다.</b> 그쪽은 호출 Code의
 * Programming 계약 위반을 뜻하고 일반 {@code 500}으로 흘러야 하지만, 여기 담기는 것은 사용자
 * 입력에 대한 거절이라 호출자가 구분해 {@code 400}으로 변환한다. 반대로 {@code cohortId}가
 * 비어 있는 것처럼 Application이 이미 확정해 넘겨야 하는 값의 위반은 계약 위반이라
 * {@code IllegalArgumentException}을 그대로 쓴다 — 두 실패는 같은 Type이면 안 된다.</p>
 *
 * <p>이 Type은 외부 오류 Code를 알지 못한다. {@link SpaceStateTransitionException}과 같은
 * 규약이다.</p>
 */
public final class SpaceValidationException extends RuntimeException {

    private final Attribute attribute;

    SpaceValidationException(Attribute attribute, String message) {
        super(message);
        this.attribute = attribute;
    }

    public Attribute attribute() {
        return attribute;
    }

    public enum Attribute {
        NAME,
        TYPE,
        CAPACITY
    }
}
