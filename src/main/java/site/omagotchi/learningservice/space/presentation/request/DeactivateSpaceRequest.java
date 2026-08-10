package site.omagotchi.learningservice.space.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeactivateSpaceRequest(
        @NotBlank(message = "비활성 사유는 필수입니다.")
        @Size(max = 200, message = "비활성 사유는 200자를 초과할 수 없습니다.")
        String inactiveReason
) {
}
