package site.omagotchi.learningservice.study.presentation.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.study.application.LearningReportQueryService;
import site.omagotchi.learningservice.study.application.result.LearningReportResult;
import site.omagotchi.learningservice.study.presentation.response.LearningReportToolResponse;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class LearningReportTools implements AiToolProvider {

    private final LearningReportQueryService learningReportQueryService;

    @Tool(description = """
            사용자의 기간 학습 리포트 재료를 한 번에 조회합니다.
            "이번 주 리포트 만들어줘", "이번 주 공부 정리해줘", "지난주랑 비교해서 어때?" 처럼 한 기간의 학습을 종합 정리해 달라는 요청에 사용하세요.
            오늘·최근 기간의 학습 시간만 묻는 단일 질문에는 학습 시간 요약 도구를, 상위권과의 비교만 묻는 질문에는 상위권 학습 패턴 조회 도구를 사용하세요.
            
            ### periodDays 파라미터 작성 규칙
            - 사용자가 기간을 말했을 때만 넣으세요 (예: "한 달 리포트" → 30).
            - 기간을 말하지 않으면 넣지 마세요. 리포트 기본값 7일(주간)로 조회합니다.
            - 1~90 범위만 허용됩니다.
            
            ### 응답 구조
            - thisPeriod.myPattern: 이번 기간의 내 패턴 (몰입 밀도 focusDensityPercent 포함)
            - thisPeriod의 top으로 시작하는 값들: 같은 기수 상위권(총 공부 시간 기준)의 익명 평균
            - previousTotalStudyMinutes, previousStudyDayCount: 직전 같은 길이 구간의 나
            
            ### 리포트 작성 규칙
            - 다음 순서로 짧게 작성하세요:
              ① 이번 기간 요약 한 줄
              ② 잘한 점 하나
              ③ 직전 기간과 비교한 변화 (늘었는지 줄었는지, 대략 몇 % 인지)
              ④ 상위권과 가장 차이 나는 것 하나 (공부한 날 기준 일평균 학습량과 몰입 밀도를 함께 보고 고르세요)
              ⑤ 다음 기간의 수행목표 하나.
            - 수행목표는 "합격하기" 같은 결과가 아니라 "매일 9시에 90분 블록 시작"처럼 실행할 행동으로 제안하세요.
            - thisPeriod의 status가 INSUFFICIENT_SAMPLE이면 상위권 비교(④)는 빼고 작성하고, myPattern의 status가 NO_DATA면 기록이 없다고 알린 뒤 시작을 권유하세요.
            - 응답에 없는 값은 지어내지 마세요.
            """)
    public LearningReportToolResponse getLearningReport(
            @ToolParam(required = false,
                    description = "리포트 기간(일). 1~90. 사용자가 기간을 말하지 않으면 생략한다")
            Integer periodDays,
            ToolContext toolContext
    ) {
        UUID userId = (UUID) toolContext.getContext().get("userId");
        // 명세(로깅과 개인정보): userId는 로그에 남기지 않는다
        log.info("[LearningReportTools] 학습 리포트 조회 - periodDays = {}", periodDays);

        LearningReportResult result = learningReportQueryService.getReport(userId, periodDays);
        return LearningReportToolResponse.from(result);
    }
}
