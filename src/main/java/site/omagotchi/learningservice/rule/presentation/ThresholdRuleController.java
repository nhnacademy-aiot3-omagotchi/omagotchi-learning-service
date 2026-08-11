package site.omagotchi.learningservice.rule.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.rule.application.ThresholdRuleService;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;
import site.omagotchi.learningservice.rule.presentation.request.CreateThresholdRuleRequest;
import site.omagotchi.learningservice.rule.presentation.request.UpdateThresholdRuleRequest;
import site.omagotchi.learningservice.rule.presentation.response.CreateThresholdRuleResponse;
import site.omagotchi.learningservice.rule.presentation.response.ThresholdRuleResponse;
import site.omagotchi.learningservice.rule.presentation.response.UpdateThresholdRuleResponse;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/threshold-rule")
public class ThresholdRuleController {

    private final ThresholdRuleService thresholdRuleService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateThresholdRuleResponse create(
            @Valid @RequestBody CreateThresholdRuleRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId){

        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        Long ruleId = thresholdRuleService.create(request.toCommand(user.userId(), requestId));

        return new CreateThresholdRuleResponse(ruleId);
    }

    @PatchMapping("/{rule-id}")
    public UpdateThresholdRuleResponse update(
            @PathVariable Long ruleId,
            @Valid @RequestBody UpdateThresholdRuleRequest request,
            JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId){

        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        UpdateThresholdRuleResult result = thresholdRuleService.update(
                request.toCommand(ruleId, user.userId(), requestId)
        );

        return new UpdateThresholdRuleResponse(result.changed(), result.ruleVersion());
    }

    @GetMapping
    public List<ThresholdRuleResponse> findAll(){
        List<ThresholdRuleResponse> responses = new ArrayList<>();
        List<ThresholdRule> thresholdRules = thresholdRuleService.readAll();

        for(ThresholdRule  thresholdRule : thresholdRules){
            responses.add(ThresholdRuleResponse.from(thresholdRule));
        }

        return responses;
    }


}
