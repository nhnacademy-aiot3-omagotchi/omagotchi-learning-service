package site.omagotchi.learningservice.team.application.result;

/** 팀원 후보가 현재 팀에 추가될 수 있는지를 설명하는 조회 상태. */
public enum TeamMemberCandidateStatus {
    AVAILABLE,
    ALREADY_IN_THIS_TEAM,
    IN_ANOTHER_TEAM
}
