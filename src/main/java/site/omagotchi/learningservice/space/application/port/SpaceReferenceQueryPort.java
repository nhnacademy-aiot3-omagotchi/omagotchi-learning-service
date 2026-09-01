package site.omagotchi.learningservice.space.application.port;

/**
 * 공간 삭제 전에 그 공간을 참조하는 자원이 남아 있는지 묻는 경계.
 *
 * <p>구현은 참조하는 Feature가 제공한다. Space가 Sensor를 직접 부르면 패키지 순환이 생긴다
 * — Sensor는 이미 {@link SpaceCohortQueryPort}로 Space를 읽고 있다.</p>
 *
 * <p><b>왜 필요한가.</b> 공간이 소프트 삭제되면 그 공간의 센서는 어느 기수에도 속하지 않게
 * 된다. 그 상태에서는 목록에도 안 나오고, 수정·비활성화는 기수 검사에 걸리며, device_eui가
 * 기본키라 재등록도 막힌다. 매니저 인계가 유일한 출구인데 애초에 만들지 않는 편이 낫다.</p>
 */
public interface SpaceReferenceQueryPort {

    /** 해당 공간에 배치된 센서 수. 활성·비활성을 가리지 않는다 — 둘 다 미아가 된다. */
    long countSensors(Long spaceId);
}
