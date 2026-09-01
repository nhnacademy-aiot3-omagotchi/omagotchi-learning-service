package site.omagotchi.learningservice.sensor.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.sensor.application.ThresholdRuleService;
import site.omagotchi.learningservice.sensor.presentation.response.ThresholdRuleResponse;

import java.util.List;

// Rule Engine의 초기 적재·누락 보정용 임계치 기준 조회 경계
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/threshold-rules")
public class InternalThresholdRuleController {

    private final ThresholdRuleService thresholdRuleService;

    @GetMapping
    public List<ThresholdRuleResponse> findAll() {
        return thresholdRuleService.readAllForRuleEngine().stream()
                .map(ThresholdRuleResponse::from)
                .toList();
    }
}
