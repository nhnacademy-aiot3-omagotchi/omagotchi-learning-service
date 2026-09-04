package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 기수 관리자 출결 목록의 한 행.
 *
 * <p>{@link AttendanceRecordResponse}와 나눠 둔 이유는 대상이 다르기 때문이다. 저쪽은
 * 요청자 자신의 기록이라 "누구인가"가 이미 정해져 있고, 그래서 소속 식별자를 내보내지
 * 않는 것이 계약이다. 여기는 남의 기록을 여럿 늘어놓는 화면이라 <b>행과 구성원을 이을
 * 열쇠가 없으면 목록이 성립하지 않는다</b> — 실제로 이 셋이 빠져 있는 동안 관리자
 * 화면은 "출결 기록 #43"밖에 그리지 못했다.</p>
 *
 * <p>한 응답에 몰아넣지 않는 것이 요점이다. 그렇게 하면 관리자에게 필요한 식별자가
 * 본인 조회 응답으로도 함께 새어 나간다.</p>
 *
 * <p>{@code userId}·{@code nickname}은 {@code null}일 수 있다. 표시 이름은 대표 캐릭터가
 * 있어야 생기는 부가 정보이므로, 없다고 행을 빼지 않고 화면이 대체 표기를 고르게 둔다.</p>
 */
public record AdminAttendanceRecordResponse(
        Long id,
        Long cohortMembershipId,
        UUID userId,
        String nickname,
        LocalDate attendanceDate,
        AttendanceStatus autoStatus,
        AttendanceStatus finalStatus,
        Instant checkedInAt,
        Instant checkedOutAt,
        Integer lateMinutes,
        Integer earlyLeaveMinutes,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminAttendanceRecordResponse from(AttendanceRecordResult result) {
        return new AdminAttendanceRecordResponse(
                result.id(),
                result.cohortMembershipId(),
                result.userId(),
                result.nickname(),
                result.attendanceDate(),
                result.autoStatus(),
                result.finalStatus(),
                result.checkedInAt(),
                result.checkedOutAt(),
                result.lateMinutes(),
                result.earlyLeaveMinutes(),
                result.version(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
