# Frontend 전달용: Learning Service 연동 구현 체크리스트

> 이 문서는 Frontend 담당자가 구현해야 하는 작업만 분리한 전달 문서입니다.
> 상세 request/response와 오류 코드는 아래 API 인수인계 문서를 기준으로 합니다.

상세 API 계약은
[`Frontend-Learning-API-Integration-Handoff.md`](Frontend-Learning-API-Integration-Handoff.md)를
기준으로 구현해 주세요. 과거의
`Cohort-Attendance-Gamification-user-presence-etc.md`는 현재 코드와 경로·응답이 달라 사용하지 않습니다.
Backend의 E2E 전 결정·구현 순서는
[`Learning-Pre-E2E-Work-Guide.md`](Learning-Pre-E2E-Work-Guide.md)를 함께 확인해 주세요.

## 목표

현재 Frontend의 `localStorage`, Mock, 임시 `/bff/v1` fallback을 실제 Learning Service와 연결합니다.

```text
Browser → Frontend BFF :8082 → Gateway :8080 → Learning Service :8084
```

Browser가 `/api/v1/**`를 직접 호출하지 않고, Frontend BFF가 HTTP Session의 Access Token을
Gateway 요청에 Bearer Token으로 전달해야 합니다.

## 현재 Frontend 상태

- 화면과 Browser API 진입점(`/bff/v1`)은 있으나 Learning Service용 BFF Controller, Client,
  DTO는 아직 없습니다.
- Profile, 닉네임, 캐릭터, 출결, Presence 등은 `localStorage`, `sessionStorage`, Mock 데이터가
  화면의 기준 데이터로 남아 있습니다.
- 따라서 Gateway 주소만 추가해서 바로 연결되는 상태는 아니며, 아래 BFF와 응답 매핑 작업이
  먼저 필요합니다.

현재 확인한 주요 수정 파일:

| Frontend 파일 | 필요한 작업 |
|---|---|
| `src/main/resources/static/js/api.js` | Profile/Gamification/Community API 추가, 기존 경로 수정, CSRF·multipart·204 처리 |
| `src/main/resources/static/js/username.js` | `sessionStorage` 저장을 닉네임 API 호출로 교체, 2~12자 정책 적용 |
| `src/main/frontend/character-selector/main.jsx` | 서버 캐릭터 목록 조회 및 `gameCharacterId`, `nickname`, `colorId` 전송 |
| `src/main/resources/static/js/attendanceState.js` | 로컬 기수 ID 제거, Profile의 승인 기수와 서버 출결 응답 사용 |
| `src/main/resources/static/js/home/presence.js` | Presence 상태·사용자 표시 DTO 매핑, 실시간 방식 교체 |
| `src/main/resources/static/js/manager/dashboard/**` | 관리자 출결 Enum 및 관리자 API 연결 |

## 구현 범위

1. Frontend Gateway HTTP Client
2. 현재 사용자 Profile
3. Cohort 가입 신청·상태
4. Attendance 조회·입실·퇴실
5. Community 목록·상세·CRUD·첨부파일
6. Gamification 캐릭터·홈·퀘스트·진행도
7. 사용자·관리자 Ranking
8. 관리자 Cohort·가입 신청·출결 관리
9. Presence REST Snapshot
10. Presence STOMP→SSE Bridge는 Gateway WebSocket route 합의 후 진행

## 필수 환경 설정

Frontend `.env.local`:

```properties
GATEWAY_SERVICE_BASE_URL=http://localhost:8080
```

Frontend `application.yaml`:

```yaml
spring:
  http:
    serviceclient:
      gateway-service:
        base-url: ${GATEWAY_SERVICE_BASE_URL}
```

`gateway-service`용 `@ImportHttpServices` 구성과 Learning API `@HttpExchange` 인터페이스를
추가해 주세요.

Frontend Java에 다음 구성도 필요합니다.

```text
learning/
├── infrastructure/LearningHttpService.java
├── infrastructure/LearningHttpServiceConfig.java
├── application/*BffService.java
└── presentation/*BffController.java
```

- `BrowserSessionTokens`에서 현재 Session의 Access Token을 조회합니다.
- Gateway 호출마다 `Authorization: Bearer <access-token>`을 설정합니다.
- Access Token 만료 시 재발급 또는 로그인 이동 정책을 Frontend 인증 흐름에 연결합니다.

## 1차 구현 매핑

| Browser BFF | Gateway/Learning |
|---|---|
| `GET /bff/v1/me/profile` | `GET /api/v1/user-profiles/me/profile` |
| `PATCH /bff/v1/me/nickname` | `PATCH /api/v1/user-profiles/me/nickname` |
| `POST /bff/v1/cohorts/applications` | `POST /api/v1/cohorts/applications` |
| `GET /bff/v1/cohorts/applications/me` | `GET /api/v1/cohorts/join-requests/me` |
| `GET /bff/v1/attendance/history` | `GET /api/v1/cohorts/{approvedCohortId}/attendance-records/me?from=&to=&page=&size=` |
| `GET /bff/v1/attendance/today` | 위 출결 목록에서 오늘 날짜 선택 |
| `POST /bff/v1/attendance/check-in` | `POST /api/v1/cohorts/{approvedCohortId}/attendance-records/check-in` |
| `POST /bff/v1/attendance/check-out` | `POST /api/v1/cohorts/{approvedCohortId}/attendance-records/check-out` |
| `GET /bff/v1/community/posts` | `GET /api/v1/community/posts` |
| `GET /bff/v1/gamification/home` | `GET /api/v1/gamification/home` |
| `GET /bff/v1/cohorts/{cohortId}/study-rankings` | 같은 `/api/v1` 경로 |
| `GET /bff/v1/presence` | `GET /api/v1/cohorts/me/presence` |

Attendance BFF는 Browser가 보낸 임의의 기수 ID를 신뢰하지 말고, Profile의
`approvedCohort.cohortId`를 사용해 downstream 경로를 만듭니다.

## 반드시 수정할 Frontend 계약 차이

### Attendance

Frontend prototype 필드:

```text
checkInAt, checkOutAt
```

실제 서버 필드:

```text
checkedInAt, checkedOutAt
```

Frontend 상태 모델을 실제 서버 필드명으로 바꿔 주세요. 날짜 필드도 Frontend의
`serviceDate` 대신 서버의 `attendanceDate`를 기준으로 사용합니다.

관리자 출결 상태도 아래와 같이 서버 Enum에 맞춥니다.

| 기존 Frontend | 서버 계약 |
|---|---|
| `NORMAL` | `PRESENT` |
| `EARLY_LEAVE` | `LEFT_EARLY` |
| 없음 | `LATE_LEFT_EARLY` |
| 없음 | `MISSING_CHECK_OUT` |
| `LATE`, `ABSENT` | 동일 |

관리자 상태 변경 요청에는 `nextStatus`, `reason`, 요청마다 고유한 `requestId`를 보냅니다.

### Character

현재 Frontend payload:

```json
{
  "characterId": "study",
  "colorId": "original"
}
```

실제 서버 payload:

```json
{
  "gameCharacterId": 1,
  "nickname": "오마",
  "colorId": "pistachio"
}
```

`GET /api/v1/gamification/characters`에서 숫자 `gameCharacterId`를 받은 뒤 사용해야 합니다.
목록의 `assetKey`가 기존 Frontend `characterId`와 대응하며 `colorId`도 Backend에 저장됩니다.

이미지는 문자열을 단순 결합하지 말고 Frontend의 `characterAssets.js`를 사용합니다. 특히
`original` PNG와 애니메이션 GIF는 파일 이름 규칙이 색상 PNG와 다르므로 `type`, `colorId`를
resolver에 넘겨 경로를 만듭니다.

### Ranking

예전 `GET /rankings/study?cohortId=...&baseDate=...` 계약을 사용하지 않습니다.

```http
GET /api/v1/cohorts/{cohortId}/study-rankings?period=WEEKLY&maxRank=100
```

응답에도 `period`, `baseDate`, `generatedAt`이 없습니다.

### Community Page

페이징 정보는 최상위가 아니라 `page` 안에 있습니다.

```json
{
  "items": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

첨부파일 요청은 `FormData`를 사용하고 `post` part를 `application/json`으로 보내야 합니다.

현재 `api.js`의 공통 요청 함수는 Body를 항상 JSON 직렬화하므로 `FormData`인 경우에는
직렬화하지 않고 `Content-Type` Header도 직접 설정하지 않도록 분기합니다.

첨부파일은 상세 응답의 ID로
`GET /api/v1/community/posts/{postId}/attachments/{attachmentId}`를 호출하고 byte stream을 relay합니다.

### Presence

- 기존 `/bff/v1/presence/lab`은 `/bff/v1/presence`로 바꿉니다.
- 서버 상태 `ONLINE`, `AWAY`, `OFFLINE`을 화면 상태에 매핑합니다.
- Snapshot의 `nickname`, `currentCharacter.type`, `colorId`, `assetKey`를 사용자 표시와 이미지
  resolver에 연결합니다. 공간 정원 정보는 Presence 계약에 포함되지 않습니다.
- 기존 Browser `EventSource`는 Learning Service의 STOMP `/ws`와 직접 호환되지 않습니다.
  Gateway WebSocket route와 BFF의 STOMP→SSE Bridge가 준비되기 전에는 REST Snapshot만 연결합니다.

## 인증과 보안 요구사항

- Access Token은 `BrowserSessionTokens`에서 가져옵니다.
- Browser로 Token을 반환하지 않습니다.
- Token을 `localStorage`, `sessionStorage`, JavaScript 전역에 넣지 않습니다.
- Gateway outbound 요청에는 `Authorization: Bearer ...`를 한 번만 넣습니다.
- Browser의 BFF 쓰기 요청에는 `X-CSRF-TOKEN`을 추가합니다.
- 임의 `X-User-Id`, `X-Global-Role` Header를 만들지 않습니다.
- `401`, `403`, `409`, `500`을 `null`이나 빈 성공으로 바꾸지 않습니다.
- `204 No Content`는 JSON parsing하지 않습니다.

현재 Frontend는 Spring Security CSRF가 활성화되어 있으므로 먼저 CSRF Token을 HTML meta 또는
전용 BFF endpoint로 Browser에 제공하고, `api.js`가 쓰기 요청에 해당 Header를 자동 추가하게 합니다.

## 화면 분기

Profile 응답에서 다음 값은 nullable입니다.

```text
approvedCohort
currentCharacter
nickname
```

- `approvedCohort=null`: 기수 가입/승인 대기 화면
- `currentCharacter=null`: 캐릭터 선택 화면
- 둘 다 존재: Home 및 Attendance API 호출

`currentCharacter`가 존재하면 `type`, `colorId`, `assetKey`는 항상 존재합니다.

## 이번 연동에서 막히는 Backend 계약

아래는 Frontend가 임의로 채우지 말고 Backend 담당자와 합의해 주세요.

1. 기수 멤버 응답에 이름·이메일이 없음
2. `/cohorts/{cohortId}/audit-logs` API가 없음
3. Gateway에 `/ws` route가 없어 Presence 실시간 연결 불가

위 항목은 Frontend에서 임의 DTO나 가짜 값을 만들어 해결하지 않습니다. REST Snapshot처럼 일부만
연결 가능한 기능은 지원 범위를 UI에 표시하고, 나머지는 계약 결정 후 연결합니다.

## 구현 순서

1. Gateway HTTP Client + Bearer Token Relay
2. Profile BFF 및 nullable 화면 분기
3. Attendance BFF 및 `localStorage` 제거
4. Gamification 캐릭터·Home·Quest
5. Community JSON CRUD
6. Community multipart
7. Ranking
8. 관리자 Cohort/Attendance
9. Presence REST Snapshot
10. 실시간 Presence 별도 합의

## Frontend 작업 체크리스트

### 공통 BFF

- [ ] Gateway base URL과 `gateway-service` HTTP Client를 등록한다.
- [ ] Session Access Token을 Gateway Bearer Header로 전달한다.
- [ ] Browser 쓰기 요청에 CSRF Header를 자동으로 추가한다.
- [ ] `204`, JSON, multipart 응답을 각각 올바르게 처리한다.
- [ ] downstream 오류의 HTTP Status와 `code`, `message`를 유지한다.
- [ ] Prototype의 `404 → null` fallback을 제거한다.

### 사용자 기능

- [ ] Profile을 최초 Bootstrap API로 호출하고 nullable 화면 분기를 적용한다.
- [ ] 닉네임 저장소를 서버로 변경하고 2~12자 및 오류 코드를 화면에 연결한다.
- [ ] 캐릭터 목록을 서버에서 받고 대표 캐릭터 요청 계약을 변경한다.
- [ ] Cohort 가입 코드 신청과 내 신청 상태를 서버 데이터로 바꾼다.
- [ ] 출결의 승인 기수 ID, 필드명, 날짜, 상태를 서버 계약에 맞춘다.
- [ ] Gamification Home, 일일 퀘스트, 보상 수령, 진행도를 연결한다.
- [ ] Community 목록·상세·작성·수정·삭제와 multipart를 연결한다.
- [ ] 사용자 Ranking을 새 Cohort 경로로 연결한다.
- [ ] Presence REST Snapshot을 연결한다.

### 관리자 기능

- [ ] Cohort/가입 신청/멤버/가입 코드 BFF를 구현한다.
- [ ] 날짜별 출결 조회와 수동 상태 변경을 구현한다.
- [ ] 관리자 출결 Enum을 서버 Enum으로 교체한다.
- [ ] 관리자 Ranking 및 공지 고정 기능을 연결한다.

### Mock 제거 및 검증

- [ ] `localStorage`와 `sessionStorage`를 서버 데이터의 Source of Truth로 사용하지 않는다.
- [ ] `[API-REPLACE]`, Mock endpoint, 임시 dashboard store 의존성을 제거한다.
- [ ] Frontend 정적 번들을 다시 빌드한다.
- [ ] BFF 통합 테스트에서 Bearer Relay, CSRF, 오류 전달을 검증한다.
- [ ] Gateway와 Learning Service를 켜고 핵심 사용자 흐름 E2E를 검증한다.

## 완료 조건

- Frontend 통합 테스트에서 BFF가 Gateway에 Bearer Token을 전달한다.
- Browser Network 탭에 Access Token이 노출되지 않는다.
- Profile, 가입 신청, 입실, 퇴실, Community, Gamification, Ranking이 Mock 없이 동작한다.
- 새로고침 후에도 서버 데이터가 유지되고 `localStorage`를 Source of Truth로 사용하지 않는다.
- `401`, `403`, `404`, `409`, `500` UI 상태를 구분한다.
- Gateway `8080`, Learning `8084`를 통한 E2E 테스트가 통과한다.
- 상세 계약 문서의 “Frontend 완료 체크리스트”를 모두 확인한다.
