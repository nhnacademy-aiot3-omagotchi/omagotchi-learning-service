package site.omagotchi.learningservice.team.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * cohortId는 nullable이다 (RM-28).
 * 활성 기수가 1개면 UI에서 선택 단계가 생략되므로 null로 온다.
 */
public record CreateTeamRequest (
        Long cohortId,

        @NotBlank
        @Size(max = 30)
        String name
)
{
}
