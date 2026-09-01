package site.omagotchi.learningservice.attendance.application.port;

/** 체크아웃 전에 활성 점유 참여 여부를 확인하는 조회 경계. */
public interface AttendanceActiveMeetingPort {

    boolean existsActiveParticipation(Long cohortMembershipId);
}
