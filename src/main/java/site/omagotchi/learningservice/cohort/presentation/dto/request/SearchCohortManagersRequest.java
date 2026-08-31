package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.cohort.application.CohortManagerLookupService;

import java.util.List;
import java.util.UUID;

/**
 * 사용자 묶음의 기수 운영 권한 일괄 조회 요청이다.
 *
 * <p>Identity의 관리자 목록 한 페이지에 대응하므로 상한을 Identity의 페이지 크기 상한과
 * 같은 100으로 맞춘다.</p>
 */
public record SearchCohortManagersRequest(
        @NotEmpty(message = "userIds는 비어 있을 수 없습니다.")
        @Size(
                max = CohortManagerLookupService.USER_IDS_MAX,
                message = "userIds는 한 번에 "
                        + CohortManagerLookupService.USER_IDS_MAX + "개까지 조회할 수 있습니다."
        )
        List<@NotNull(message = "userId는 null일 수 없습니다.") UUID> userIds
) {
}
