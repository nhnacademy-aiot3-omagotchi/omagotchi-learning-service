package site.omagotchi.learningservice.space.application.result;

/**
 * 다른 Feature가 공간의 이용 가능 여부를 판정하는 데 필요한 사실.
 *
 * <p>점유 시작(MR-20, RM-13)과 참여자 정원 검증(MR-28)이 첫 소비처다. 저쪽은 공간의
 * 이름·비활성 사유·관리 주체 기수를 알 필요가 없으므로 판정에 쓰는 값만 담는다.</p>
 *
 * <p><b>{@code SpaceType}을 그대로 노출하지 않는 것이 의도다.</b> 열거형을 넘기면 상대 Feature가
 * {@code space}의 domain에 컴파일 의존을 갖게 되고, 유형이 늘어날 때마다 남의 코드가 흔들린다.
 * "회의실인가"는 공간이 스스로 답할 수 있는 질문이므로 여기서 boolean으로 좁힌다.</p>
 *
 * <p>{@code active}는 {@code status = ACTIVE}와 {@code deleted_at IS NULL}을 합친 값이다.
 * 둘을 따로 주면 소비처마다 조합 규칙을 복제하게 되고, 한쪽만 보고 판정하는 실수가 생긴다.</p>
 *
 * @param spaceId     조회한 공간
 * @param meetingRoom 회의실 유형인가. 선착순 점유의 대상 여부다 (MR-20)
 * @param active      지금 이용할 수 있는가. 비활성·삭제 공간은 {@code false} (RM-13)
 * @param capacity    최대 인원. 회의실 참여자 정원 검증에 쓴다 (MR-28)
 */
public record SpaceAccessView(
        Long spaceId,
        boolean meetingRoom,
        boolean active,
        int capacity
) {
}
