package site.omagotchi.learningservice.occupancy.application.port;

import java.util.Optional;

/**
 * 공간 조회 경계. 점유 모듈이 {@code space} 파트에 의존하는 유일한 통로다.
 *
 * <p>읽기 전용인 것이 설계의 결과다. 점유·연장·반납·강제종료 어느 것도
 * {@code spaces}에 쓰기를 하지 않는다 — 공간의 사용 상태를 컬럼으로 저장하지 않고
 * 활성 점유 존재 여부로 매 조회 시 파생 계산하기로 했기 때문이다 (명세서 01, SSOT 원칙).</p>
 *
 * <p>구현({@code SpaceAccessReader})은 {@code space}의 공개 Application 계약인
 * {@code SpaceAccessService}에 위임한다. 이 Port가 유지되는 이유는 점유의 Use Case가
 * 남의 결과 Type을 직접 받지 않게 하기 위해서다.</p>
 */
public interface SpaceReader {

    /**
     * 락 없이 공간을 읽는다. 락 밖 사전 검증(MR-20, RM-13)에 쓴다.
     *
     * <p>{@code Optional}을 반환하고 예외를 던지지 않는 것이 의도다 —
     * 같은 "공간 없음"이 상황마다 다른 코드일 수 있어 판단은 호출부가 한다
     * ({@code MembershipReader}와 같은 규약).</p>
     */
    Optional<MeetingRoom> find(Long spaceId);

    /**
     * {@code spaces} 행 배타 락을 잡고 읽는다. 반드시 트랜잭션 안에서 호출한다.
     *
     * <p>활성 조건을 쿼리에 넣지 않는 것이 의도다. 락을 잡은 뒤
     * {@link MeetingRoom#active()}를 확인해야 "비활성화 커밋 직후 도착한 점유 요청"을
     * 정확히 400으로 잡는다. 조건에 넣으면 그냥 "행 없음"으로 빠져 404가 된다.</p>
     */
    Optional<MeetingRoom> lock(Long spaceId);

    /**
     * 점유 판정에 필요한 공간 정보.
     *
     * <p>{@code SpaceType} 열거형을 그대로 노출하지 않고 {@code meetingRoom} 불리언으로
     * 좁힌 것은 의도다 — 점유가 알아야 할 것은 "회의실인가" 하나뿐이고, 열거형을 받으면
     * {@code space} 파트의 domain에 컴파일 의존이 생긴다.</p>
     *
     * @param capacity 참여자 정원 검증(MR-28)용. 점유 시작에서는 쓰지 않고 #6에서 쓴다
     */
    record MeetingRoom(Long id, boolean meetingRoom, boolean active, int capacity){}

}
