package site.omagotchi.learningservice.attendance.application.result;

import java.time.Instant;

/**
 * 다른 Feature에 노출하는 "지금 재실 중" 정보.
 *
 * <p>Java 표준 Type만 담는다. {@code PresenceState}를 그대로 내보내면 받는 쪽이 출결
 * 파트의 domain에 컴파일 의존이 생기고, 무엇보다 <b>재실 판정을 받는 쪽이 다시 하게 된다</b> —
 * 그 판정은 출결 파트의 책임이므로 이 레코드는 "재실인 구간"만 담아 돌려준다
 * (10-backend-code-structure "공개 Application 계약").</p>
 *
 * <p>{@code spaceId}를 담지 않는 것은 의도다. 점유하려는 방과 현재 재실 공간이 같아야
 * 한다는 규칙은 명세에 없으며(참여자의 위치가 다른 공간이어도 정상으로 취급한다,
 * 명세서 02), 쓰지 않는 값을 계약에 두지 않는다.</p>
 *
 * @param attendanceId       조회에서 선택한 출결 기록. 후속 체류 전환의 잠금 대상이다
 * @param cohortMembershipId 이 재실이 속한 기수 소속. 점유자·참여자 멤버십의 출처다
 * @param startedAt          구간 시작 시각. 열린 구간이 여럿일 때 최신 판정에 쓴다
 */
public record OpenPresenceView(
        Long attendanceId,
        Long cohortMembershipId,
        Instant startedAt
) {
}
