package site.omagotchi.learningservice.chat.presentation.request;

/**
 * 사용자가 요청마다 선택할 수 있는 AI 모델
 * 값 이름이 그대로 요청 파라미터가 되므로(예: model=OLLAMA) 바꾸면 Browser 계약이 깨짐
 */
public enum ChatModelType {
    GEMINI,
    OLLAMA
}
