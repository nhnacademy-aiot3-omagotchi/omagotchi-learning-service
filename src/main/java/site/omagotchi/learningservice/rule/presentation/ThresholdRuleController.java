package site.omagotchi.learningservice.rule.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.rule.application.ThresholdRuleService;
import site.omagotchi.learningservice.rule.application.result.ApplySpaceThresholdResult;
import site.omagotchi.learningservice.rule.application.result.SpaceThresholdResult;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;
import site.omagotchi.learningservice.rule.presentation.request.ApplySpaceThresholdRequest;
import site.omagotchi.learningservice.rule.presentation.request.CreateThresholdRuleRequest;
import site.omagotchi.learningservice.rule.presentation.request.UpdateThresholdRuleRequest;
import site.omagotchi.learningservice.rule.presentation.response.ApplySpaceThresholdResponse;
import site.omagotchi.learningservice.rule.presentation.response.CreateThresholdRuleResponse;
import site.omagotchi.learningservice.rule.presentation.response.SpaceThresholdResponse;
import site.omagotchi.learningservice.rule.presentation.response.ThresholdRuleResponse;
import site.omagotchi.learningservice.rule.presentation.response.UpdateThresholdRuleResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/threshold-rules")
public class ThresholdRuleController {

    private final ThresholdRuleService thresholdRuleService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateThresholdRuleResponse create(
            @Valid @RequestBody CreateThresholdRuleRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        Long ruleId = thresholdRuleService.create(request.toCommand(user.userId(), requestId));

        return new CreateThresholdRuleResponse(ruleId);
    }

    @PatchMapping("/{rule-id}")
    public UpdateThresholdRuleResponse update(
            @PathVariable("rule-id") Long ruleId,
            @Valid @RequestBody UpdateThresholdRuleRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        UpdateThresholdRuleResult result = thresholdRuleService.update(
                request.toCommand(ruleId, user.userId(), requestId)
        );

        return new UpdateThresholdRuleResponse(result.changed(), result.ruleVersion());
    }

    @GetMapping
    public List<ThresholdRuleResponse> findAll() {
        return thresholdRuleService.readAll().stream()
                .map(ThresholdRuleResponse::from)
                .toList();
    }

    /** 공간별 현재 임계치. 화면이 이걸로 입력 폼을 그린다 */
    @GetMapping("/spaces")
    public List<SpaceThresholdResponse> findBySpaces(){
        List<SpaceThresholdResponse> responses = new ArrayList<>();

        for(SpaceThresholdResult result : thresholdRuleService.findAllBySpace()){
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
    @PatchMapping("/spaces/{space-id}")
    public ApplySpaceThresholdResponse applyToSpace(
            @PathVariable("space-id") Long spaceId,
            @Valid @RequestBody ApplySpaceThresholdRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId){

        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        ApplySpaceThresholdResult result = thresholdRuleService.applyToSpace(
                request.toCommand(spaceId, user.userId(), requestId)
        );

        return ApplySpaceThresholdResponse.from(result);
    }

}
