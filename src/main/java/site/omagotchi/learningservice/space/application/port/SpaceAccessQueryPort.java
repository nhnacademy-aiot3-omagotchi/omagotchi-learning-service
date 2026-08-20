package site.omagotchi.learningservice.space.application.port;

import site.omagotchi.learningservice.space.application.result.SpaceAccessView;

import java.util.Optional;

/**
 * 이용 가능 여부 판정에 필요한 값만 읽는 경계.
 *
 * <p>{@link SpaceRepository}와 나눠 둔 이유는 반환 형태가 다르기 때문이다. 저쪽은 상태를
 * 바꾸려고 {@code Space} 도메인 객체를 만들지만, 여기는 <b>엔티티를 만들지 않는 것 자체가
 * 계약</b>이다 — 아래 {@link #lock(Long)} 주석 참고.</p>
 */
public interface SpaceAccessQueryPort {

    /**
     * 락 없이 값을 읽는다.
     *
     * <p>{@link Optional}을 돌려주고 예외를 던지지 않는다. 같은 "공간 없음"이 소비처마다 다른
     * 오류 코드일 수 있어 판단은 호출부가 한다.</p>
     */
    Optional<SpaceAccessView> find(Long spaceId);

    /**
     * {@code spaces} 행 배타 락을 잡고 값을 읽는다. 반드시 트랜잭션 안에서 호출한다.
     *
     * <p>활성 조건을 쿼리에 넣지 않는 것도 의도다. 락을 잡은 뒤 {@code active}를 확인해야
     * "비활성화 커밋 직후 도착한 요청"을 정확히 400으로 잡는다 — 조건에 넣으면 그냥
     * "행 없음"으로 빠져 404가 된다.</p>
     */
    Optional<SpaceAccessView> lock(Long spaceId);
}
