package site.omagotchi.learningservice.space.application.port;

import java.util.Optional;

/**
 * 다른 Feature가 공간 이름만 읽는 경계.
 *
 * <p>{@link SpaceAccessQueryPort}와 나눠 둔 이유는 목적이 다르기 때문이다. 저쪽은 이용
 * 가능 여부 판정에 쓰이고 "이름을 담지 않는 것"이 그 계약의 일부다({@code SpaceAccessView}
 * javadoc 참고). 이름이 필요한 소비처(알림 문구)가 생겼다고 그 계약에 필드를 끼워 넣으면
 * 판정 전용이라는 원래 목적이 흐려진다.</p>
 */
public interface SpaceNameQueryPort {

    Optional<String> findName(Long spaceId);
}
