package site.omagotchi.learningservice.sensor.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.sensor.application.ThresholdRuleService;
import site.omagotchi.learningservice.sensor.application.result.ApplySpaceThresholdResult;
import site.omagotchi.learningservice.sensor.application.result.SpaceThresholdResult;
import site.omagotchi.learningservice.sensor.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.sensor.presentation.request.ApplySpaceThresholdRequest;
import site.omagotchi.learningservice.sensor.presentation.request.CreateThresholdRuleRequest;
import site.omagotchi.learningservice.sensor.presentation.request.UpdateThresholdRuleRequest;
import site.omagotchi.learningservice.sensor.presentation.response.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/threshold-rules")
public class ThresholdRuleController {

    private final ThresholdRuleService thresholdRuleService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateThresholdRuleResponse create(
            @PathVariable Long cohortId,
            @Valid @RequestBody CreateThresholdRuleRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        Long ruleId = thresholdRuleService.create(
                cohortId,
                user.userId(),
                requestId,
                request.toCommand()
        );

        return new CreateThresholdRuleResponse(ruleId);
    }

    @PatchMapping("/{ruleId}")
    public UpdateThresholdRuleResponse update(
            @PathVariable Long cohortId,
            @PathVariable Long ruleId,
            @Valid @RequestBody UpdateThresholdRuleRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        UpdateThresholdRuleResult result = thresholdRuleService.update(
                cohortId,
                user.userId(),
                requestId,
                ruleId,
                request.toCommand()
        );

        return new UpdateThresholdRuleResponse(result.changed(), result.ruleVersion());
    }

    @GetMapping
    public List<ThresholdRuleResponse> findAll(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return thresholdRuleService.findAllByCohort(cohortId, user.userId()).stream()
                .map(ThresholdRuleResponse::from)
                .toList();
    }

    /** 공간별 현재 임계치. 화면이 이걸로 입력 폼을 그린다 */
    @GetMapping("/spaces")
    public List<SpaceThresholdResponse> findBySpaces(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        List<SpaceThresholdResponse> responses = new ArrayList<>();

        for (SpaceThresholdResult result : thresholdRuleService.findAllBySpace(
                cohortId,
                user.userId()
        )) {
            responses.add(SpaceThresholdResponse.from(result));
        }

        return responses;
    }

    /**
     * 공간 안 모든 기기의 임계치를 한 번에 맞춘다.
     *
     * <p>경로가 두 세그먼트라 PATCH /{rule-id} 와 충돌하지 않는다 —
     * 리터럴 /spaces 가 변수보다 먼저 매칭된다.</p>
     */
    @PatchMapping("/spaces/{spaceId}")
    public ApplySpaceThresholdResponse applyToSpace(
            @PathVariable Long cohortId,
            @PathVariable Long spaceId,
            @Valid @RequestBody ApplySpaceThresholdRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {

        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        ApplySpaceThresholdResult result = thresholdRuleService.applyToSpace(
                cohortId,
                user.userId(),
                requestId,
                spaceId,
                request.toCommand()
        );

        return ApplySpaceThresholdResponse.from(result);
    }

}
