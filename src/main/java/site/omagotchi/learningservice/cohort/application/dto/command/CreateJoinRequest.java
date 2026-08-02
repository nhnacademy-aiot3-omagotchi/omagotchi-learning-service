package site.omagotchi.learningservice.cohort.application.dto.command;

/**
 * 가입 코드 기반 기수 참가 신청 요청
 */
public record CreateJoinRequest(
        String joinCode,
        String code
) {

    public String joinCode() {
        return joinCode != null ? joinCode : code;
    }
}
