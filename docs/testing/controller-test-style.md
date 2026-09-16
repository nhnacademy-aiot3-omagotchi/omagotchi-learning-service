# 컨트롤러 테스트 작성 기준

새로 만드는 컨트롤러 테스트와 REST Docs 테스트에 적용한다. 기존 테스트는 변경하는
범위에서 맞추며, 형식 정리를 위해 요청·응답·검증·문서 ID를 바꾸지 않는다.

## 자동 포맷과 줄바꿈

기본 들여쓰기는 `google-java-format 1.36.1`의 AOSP 스타일을 참고한다. 전체 포맷은 초안
정리에만 사용한다. 최종 단계에서는 아래의 MockMvc 체인 형식을 맞춘 뒤
`--fix-imports-only`로 import만 정리한다.

```bash
GJF_JAR=/path/to/google-java-format-1.36.1-all-deps.jar

# 초안의 들여쓰기와 메서드 체인 정리
java -jar "$GJF_JAR" --aosp --skip-reflowing-long-strings --replace path/to/NewControllerTest.java

# 최종 코드에서 사용하지 않는 import 제거와 import 순서 정리
java -jar "$GJF_JAR" --fix-imports-only --replace path/to/NewControllerTest.java
```

- 들여쓰기는 공백 4칸을 사용한다. 메서드는 `@Test`, `@DisplayName`을 각각 별도 줄에 쓴다.
- `@Autowired`, `@MockitoBean` 등 필드 어노테이션과 선언은 각각 별도 줄에 쓴다.
- 어노테이션이 붙은 필드 선언끼리는 빈 줄 하나로 구분한다.
- 클래스는 `@WebMvcTest`, 개별 설정 어노테이션, `@LearningRestDocsTest` 순서로 작성한다.
- 보안·JWT·프로필·REST Docs 공통 설정은 `@LearningRestDocsTest`로 재사용한다.
- 와일드카드 import를 쓰지 않고 한 타입 또는 정적 멤버마다 import 한 줄을 사용한다.
- `perform()`과 첫 요청 빌더, `andDo()`와 `document()`는 같은 줄에서 시작한다.
- 단일 설명자는 `pathParameters(parameterWithName(...))`처럼 같은 줄에 둔다. 요청 빌더의
  설정이 여러 개이거나 문서 필드가 여러 개일 때만 내부 체인을 줄바꿈한다.
- 문자열을 쪼개는 옵션은 끈다. 문서 설명·JSON fixture·URL 값을 형식 정리로 바꾸지 않는다.
- 사용하지 않는 import는 `--fix-imports-only`로 제거한다.

## 메서드의 DisplayName

모든 `@Test`·`@ParameterizedTest` 메서드에 `@DisplayName`을 작성한다.
클래스의 `@DisplayName`으로 대신하지 않는다. 대상과 조건·결과를 짧은 한국어 명사형으로 쓴다.

| 권장 | 피할 표현 |
| --- | --- |
| `공간 목록 조회` | `공간 목록을 정상적으로 조회해야 한다` |
| `세션 없는 요청 거부` | `인증되지 않은 요청이 거절되는지 테스트` |
| `이름 누락 시 400 응답` | `누락된 이름을 가진 요청에 대한 검증 수행` |
| `계정 탈퇴 후 세션 만료` | `계정 탈퇴 동작의 성공적인 문서화` |

`검증`, `테스트`, `문서화` 같은 목적 설명을 반복하지 않는다. HTTP 상태와 API·JWT·CSRF처럼
필요한 기술 이름은 그대로 사용한다. 서로 다른 조건에는 구별되는 이름을 붙인다.

## Given / When / Then

각 테스트 본문에 Given과 실행·검증 단계를 작성한다. 설명은 실제 코드와 일치해야 한다.

- `// Given: ...` — 입력, 세션, 서비스 응답 준비. 별도 mock 준비가 없으면 요청 조건을 설명한다.
- 단순 동기 요청은 `// When & Then` 아래에서 `mockMvc.perform()`과 검증·문서 체인을 바로
  이어 쓴다. `ResultActions` 지역 변수를 만들지 않는다.
- SSE처럼 실행과 최종 검증이 실제로 분리되는 경우에만 `// When: ...`, `// Then: ...`을
  나눈다. 이때도 중간 결과는 필요한 `MvcResult`로 바로 받는다.
- 상태 검증을 `document()`보다 먼저 둔다.

공통 준비가 `@BeforeEach`에 있어도 메서드에는 해당 시나리오의 Given 설명을 남긴다.
SSE 등 비동기 요청은 최초 요청과 async dispatch 순서를 유지한다. 비동기 시작 여부 등
다음 실행의 전제 조건은 When 단계에서 확인할 수 있으며, 최종 응답 검증은 Then에 둔다.
헬퍼 메서드나 `@BeforeEach`에는 형식만 맞추기 위한 DisplayName·세 단계 주석을 붙이지 않는다.

```java
@WebMvcTest(TeamController.class)
@LearningRestDocsTest
class TeamDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    // 나머지 Controller 의존성과 오류 로깅 경계는 테스트 대상에 맞게 선언한다.

    @Test
    @DisplayName("내 팀 목록 조회")
    void getsMyTeams() throws Exception {
        // Given: 사용자 JWT와 빈 팀 목록
        UUID userId = UUID.fromString(TestJwtKeyConfig.USER_ID);
        String authorization = "Bearer " + TestJwtKeyConfig.issue("USER");
        given(teamService.getMyTeams(userId)).willReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/teams/me")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"))
                .andDo(document("teams/get-my-teams"));
        verify(teamService).getMyTeams(userId);
    }
}
```

## MVC 구성과 검증 보존

`@WebMvcTest`가 제공하는 MockMvc를 주입받고, 테스트에서 다시 빌드하지 않는다.
실제 보안·MVC·예외 처리 설정을 유지하며 서비스와 외부 호출 경계를 격리한다.
Frontend는 인증 세션과 변경 요청의 CSRF를 명시한다. Learning은 실제 테스트 JWT의
주체·역할을 서비스 fixture와 맞추고 내부 API는 해당 Basic 인증 설정을 사용한다.
무인증·CSRF 누락 시나리오에 성공 요청의 인증을 자동 주입하지 않는다.

형식 변경 후 테스트 수·이름·요청·검증·문서 ID를 비교하고 대상 테스트와 문서 생성을 확인한다.
