# Frontend 전달용: Learning Service 연동 작업 요청서

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

## 1차 구현 매핑

| Browser BFF | Gateway/Learning |
|---|---|
| `GET /bff/v1/me/profile` | `GET /api/v1/user-profiles/me/profile` |
| `PATCH /bff/v1/me/nickname` | `PATCH /api/v1/user-profiles/me/nickname` |
| `POST /bff/v1/cohorts/applications` | `POST /api/v1/cohorts/applications` |
| `GET /bff/v1/cohorts/applications/me` | `GET /api/v1/cohorts/join-requests/me` |
| `GET /bff/v1/attendance/history` | `GET /api/v1/cohorts/{approvedCohortId}/attendance-records/me` |
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

Frontend 상태 모델을 실제 서버 필드명으로 바꿔 주세요.

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
Profile의 이미지 경로는 `/images/characters/${currentCharacter.assetKey}.png`입니다.

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

## 인증과 보안 요구사항

- Access Token은 `BrowserSessionTokens`에서 가져옵니다.
- Browser로 Token을 반환하지 않습니다.
- Token을 `localStorage`, `sessionStorage`, JavaScript 전역에 넣지 않습니다.
- Gateway outbound 요청에는 `Authorization: Bearer ...`를 한 번만 넣습니다.
- Browser의 BFF 쓰기 요청에는 `X-CSRF-TOKEN`을 추가합니다.
- 임의 `X-User-Id`, `X-Global-Role` Header를 만들지 않습니다.
- `401`, `403`, `409`, `500`을 `null`이나 빈 성공으로 바꾸지 않습니다.
- `204 No Content`는 JSON parsing하지 않습니다.

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

1. Presence 응답에 이름·닉네임·캐릭터 이미지가 없음
2. 기수 멤버 응답에 이름·이메일이 없음
3. 커뮤니티 첨부파일 다운로드 URL/API가 없음
4. `/cohorts/{cohortId}/audit-logs` API가 없음
5. Gateway에 `/ws` route가 없어 Presence 실시간 연결 불가

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

## 완료 조건

- Frontend 통합 테스트에서 BFF가 Gateway에 Bearer Token을 전달한다.
- Browser Network 탭에 Access Token이 노출되지 않는다.
- Profile, 가입 신청, 입실, 퇴실, Community, Gamification, Ranking이 Mock 없이 동작한다.
- 새로고침 후에도 서버 데이터가 유지되고 `localStorage`를 Source of Truth로 사용하지 않는다.
- `401`, `403`, `404`, `409`, `500` UI 상태를 구분한다.
- Gateway `8080`, Learning `8084`를 통한 E2E 테스트가 통과한다.
- 상세 계약 문서의 “Frontend 완료 체크리스트”를 모두 확인한다.
