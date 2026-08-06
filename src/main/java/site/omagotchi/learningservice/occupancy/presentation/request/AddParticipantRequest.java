package site.omagotchi.learningservice.occupancy.presentation.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 참여자 추가 요청.
 *
 * <p>기수나 멤버십 식별자를 받지 않는 것이 의도다 (명세서 02). 대상의 멤버십은 열린
 * 재실 구간에서 도출하며, 요청으로 받으면 출근한 기수와 다른 기수로 참여시키는 경로가
 * 열린다 — 점유 시작이 기수 컨텍스트를 받지 않는 것과 같은 이유다.</p>
 */
public record AddParticipantRequest(
        @NotNull(message = "대상 사용자 ID는 필수입니다.")
        UUID targetUserId
) {
}
