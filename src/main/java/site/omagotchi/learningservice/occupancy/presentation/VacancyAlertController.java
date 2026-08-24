package site.omagotchi.learningservice.occupancy.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.presentation.request.RequestVacancyAlertRequest;
import site.omagotchi.learningservice.occupancy.presentation.response.VacancyAlertResponse;

import java.util.List;
import java.util.UUID;

/**
 * 공실 알림 API (MR-02, MR-15, MR-17, MR-34).
 *
 * <p>신청만 공간 하위 경로이고 조회·취소는 최상위인 것이 의도다. 신청의 대상은 회의실이지만,
 * 목록과 취소의 대상은 <b>신청 그 자체</b>다 — 사용자는 "이 방의 내 신청"이 아니라
 * "내 신청 전부"를 보고 그중 하나를 고른다. 취소를 공간 하위에 두면 클라이언트가 취소하려고
 * 공간 식별자를 따로 들고 있어야 한다.</p>
 *
 * <p>에러 응답은 이 Class가 만들지 않는다. 서비스가 던진 {@code BusinessException}을
 * {@code GlobalExceptionHandler}가 받아 400/403/404/409로 옮긴다.</p>
 */
@RestController
@RequiredArgsConstructor
public class VacancyAlertController {

    private final VacancyAlertService vacancyAlertService;

    /**
     * 공실 알림 신청 (MR-02, MR-34).
     *
     * <p>타 기수가 점유 중인 회의실에도 신청할 수 있다 — 회의실은 공유 자원이다.</p>
     *
     * @return 201. 빈 방이거나 본인이 점유 중이면 400, 이미 대기 중인 신청이 있으면 409
     */
    @PostMapping("/api/v1/spaces/{space-id}/vacancy-alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public void request(
            @PathVariable("space-id") Long spaceId,
            @Valid @RequestBody(required = false) RequestVacancyAlertRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long cohortId = request == null ? null : request.cohortId();
        vacancyAlertService.request(spaceId, cohortId, requesterId(jwt));
    }

    /**
     * 내 대기 중 신청 목록 (MR-17 1단계).
     *
     * <p>소진된 신청은 담지 않는다. 취소할 수 있는 것만 보여야 목록에서 고른 항목이
     * 404로 실패하지 않는다.</p>
     */
    @GetMapping("/api/v1/vacancy-alerts/me")
    public List<VacancyAlertResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return vacancyAlertService.findMine(requesterId(jwt)).stream()
                .map(VacancyAlertResponse::from)
                .toList();
    }

    /**
     * 신청 취소 (MR-17).
     *
     * @return 204. 이미 발송됐거나 남의 신청이면 404 — 둘을 구분하지 않는다
     */
    @DeleteMapping("/api/v1/vacancy-alerts/{alert-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @PathVariable("alert-id") Long alertId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        vacancyAlertService.cancel(alertId, requesterId(jwt));
    }

    /**
     * Access JWT의 {@code sub}에서 요청자 계정을 읽는다.
     *
     * <p>{@code null} 검사를 두지 않는 것은 {@code OccupancyParticipantController}와 같은
     * 이유다 — 이 경로들은 {@code anyRequest().authenticated()}에 걸려 인증 없이 도달하지
     * 않으므로, 여기서 {@code null}이면 보안 설정이 뚫린 것이다.</p>
     */
    private static UUID requesterId(Jwt jwt) {
        return AuthenticatedUser.from(jwt).userId();
    }
}
