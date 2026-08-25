package site.omagotchi.learningservice.weather.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.weather.application.WeatherQueryService;
import site.omagotchi.learningservice.weather.application.result.WeatherQueryResult;
import site.omagotchi.learningservice.weather.presentation.response.WeatherToolResponse;

@Component
@Slf4j
@RequiredArgsConstructor
public class WeatherTools implements AiToolProvider {

    private final WeatherQueryService weatherQueryService;

    @Tool(description = """
            지역명으로 날씨 예보를 조회합니다.
            "광주 동구 날씨 알려줘", "내일 서울 날씨 어때" 처럼 날씨가 궁금할 때 사용하세요.
            
            ### 지원 범위
            - 오늘부터 최대 5일 이내의 예보만 제공합니다.
            - forecasts에 담긴 날짜 범위를 벗어난 시점(예: "다음 주", "일주일 후", "이번 달 말")을 물어보면,
              있는 값 중 아무거나 골라 답하지 말고, "그렇게 먼 미래의 예보는 아직 지원하지 않습니다"라고 사용자에게 안내하세요.
            
            ### region 파라미터 작성 규칙
            - 지역명만 넣으세요 (예: "광주 동구", "서울", "충장동").
            - "오늘", "내일" 같은 날짜·시간 표현은 절대 region에 넣지 마세요.
              응답에 여러 날짜의 예보가 전부 포함되니, 그중 사용자가 물어본 날짜를 직접 찾아 답변하세요.
            
            ### 응답 해석 규칙
            - status가 "NOT_FOUND"면: 지역명을 못 찾은 것입니다. 사용자에게 지역명을 다시 확인해 달라고 요청하세요.
            - status가 "AMBIGUOUS"면: 지역명이 여러 곳에 해당합니다. candidateRegionNames 목록을 사용자에게 보여주고
              어느 지역인지 되물으세요. 절대 임의로 하나를 골라 답하지 마세요.
            - status가 "FOUND"면: forecasts에 여러 날짜·시각의 예보가 담겨 있습니다.
              사용자가 물어본 날짜에 해당하는 항목을 찾아 답변하세요. 없으면 위 "지원 범위" 규칙을 따르세요.
            """)
    public WeatherToolResponse getWeather(
            @ToolParam(description = "지역명 (예: 광주 동구, 서울, 충장동) (날짜, 시간 표현은 포함하지 않는다)") String region
    ) {
        log.info("[WeatherTools] 날씨 조회 - region = {}", region);

        WeatherQueryResult weatherQueryResult = this.weatherQueryService.query(region);
        return WeatherToolResponse.from(weatherQueryResult);
    }
}
