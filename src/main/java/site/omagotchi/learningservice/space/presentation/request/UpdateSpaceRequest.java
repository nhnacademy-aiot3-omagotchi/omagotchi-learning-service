package site.omagotchi.learningservice.space.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.space.domain.SpaceType;

public record UpdateSpaceRequest(

        @NotBlank(
                message = "공간 이름은 필수입니다."
        )
        @Size(
                max = 50,
                message = "공간 이름은 50자를 초과할 수 없습니다."
        )
        String name,

        @NotNull(
                message = "공간 유형은 필수입니다."
        )
        SpaceType type,

        @NotNull(
                message = "공간 최대 인원은 필수입니다."
        )
        @Positive(
                message = "공간 최대 인원은 1명 이상이어야 합니다."
        )
        Integer capacity
) {
}