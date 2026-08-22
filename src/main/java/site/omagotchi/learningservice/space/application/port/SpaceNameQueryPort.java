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

    /**
     * 삭제되지 않은 공간의 이름을 읽는다.
     *
     * <p>소프트 삭제된 공간은 {@code Optional.empty()}다. 소비처가 이름 조회 실패를
     * "공간 {id}"로 대체하는 fallback을 이미 갖추고 있는데(예: {@code VacancyAlertDispatcher}),
     * 삭제된 공간의 옛 이름을 그대로 돌려주면 그 fallback이 걸리지 않아 사용자 알림에
     * 이미 사라진 방 이름이 그대로 노출된다.</p>
     */
    Optional<String> findName(Long spaceId);
}
