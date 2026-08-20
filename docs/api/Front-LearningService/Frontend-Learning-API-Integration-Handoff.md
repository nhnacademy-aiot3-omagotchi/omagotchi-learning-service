# Frontend ↔ Learning Service API 연동 인수인계

- 기준 브랜치: `learning-service/dev`
- 기준 커밋: `9d10eb9`
- 기준일: `2026-08-20`
- 범위: `user profile`, `cohort`, `attendance`, `community`, `gamification`, `ranking`, `presence`
- 호출 경계: Browser → Frontend BFF → Gateway → Learning Service

이 문서는 Frontend 팀이 실제 연동에 사용하는 기준 문서다. 과거 문서인
`Cohort-Attendance-Gamification-user-presence-etc.md`의 경로와 예시 중 현재 코드와
다른 내용은 이 문서를 우선한다. 최종 계약 근거는 Learning Service의 Controller,
request/response DTO와 자동화된 Controller Test다.

## 1. 먼저 합의된 호출 구조

```text
Browser
  └─ Cookie + CSRF → Frontend BFF http://localhost:8082/bff/v1/**
       └─ Authorization: Bearer <access-token>
          → Gateway http://localhost:8080/api/v1/**
             └─ 원본 /api/v1 경로 유지
                → Learning Service http://localhost:8084/api/v1/**
```

브라우저가 Gateway나 Learning Service를 직접 호출하지 않는다.

- Access Token은 Frontend의 Redis HTTP Session에만 보관한다.
- Access Token을 JavaScript, HTML, `localStorage`, `sessionStorage`에 노출하지 않는다.
- Browser는 Frontend와 same-origin인 `/bff/v1/**`만 호출한다.
- Frontend BFF가 현재 HTTP Session의 Access Token을 Bearer Header로 Gateway에 전달한다.
- Gateway는 Cookie, `X-User-Id`, `X-Global-Role`을 downstream에 전달하지 않는다.
- Learning Service의 사용자 식별자는 JWT의 `sub` UUID만 사용한다.

## 2. 로컬 주소와 필수 실행 순서

| 구성 요소 | 로컬 주소 | 용도 |
|---|---|---|
| Frontend | `http://localhost:8082` | Page, Browser Session, BFF |
| Gateway | `http://localhost:8080` | JWT 검증, API Routing |
| Identity Service | `http://localhost:8083` | Login, Access/Refresh Token |
| Learning Service | `http://localhost:8084` | 이 문서의 Domain API |
| PostgreSQL | `localhost:5432` | Learning 영속 데이터 |
| Redis | `localhost:6379` | Frontend Session, Learning Presence |
| RabbitMQ | `localhost:5672` | Rule 품질 이벤트 |

권장 실행 순서:

1. PostgreSQL, Redis, RabbitMQ
2. Identity Service
3. Learning Service
4. Gateway
5. Frontend

로컬 Profile에서는 Eureka를 실행하지 않는다. Gateway와 각 서비스는 고정 URI를 사용한다.

Frontend `.env.local`에 다음 설정을 추가한다.

```properties
GATEWAY_SERVICE_BASE_URL=http://localhost:8080
```

Frontend `application.yaml`에는 Identity와 별도로 Gateway HTTP Service Group을 둔다.

```yaml
spring:
  http:
    serviceclient:
      gateway-service:
        base-url: ${GATEWAY_SERVICE_BASE_URL}
```

## 3. Frontend BFF 구현 규칙

### 3.1 Outbound HTTP Client

Frontend에 다음 구성을 추가한다.

- `LearningHttpService`: 실제 `/api/v1/**` 계약을 선언하는 `@HttpExchange` 인터페이스
- `LearningHttpServiceConfig`: `gateway-service` HTTP Service Group 등록
- Domain별 BFF Service 또는 Adapter: Access Token 전달, 응답 검증, 에러 변환
- Domain별 `/bff/v1/**` Controller: Browser용 계약 제공

권장 패키지 예시:

```text
site.omagotchi.frontend.learning
├── application
├── infrastructure
│   ├── LearningHttpService.java
│   └── LearningHttpServiceConfig.java
└── presentation
    ├── ProfileBffController.java
    ├── AttendanceBffController.java
    ├── CommunityBffController.java
    ├── GamificationBffController.java
    └── PresenceBffController.java
```

각 BFF 요청에서 `BrowserSessionTokens.find(request)`로 Token Bundle을 가져오고,
outbound 요청에 아래 Header를 추가한다.

```http
Authorization: Bearer <BrowserSessionTokenBundle.accessToken>
Accept: application/json
```

Access Token을 Controller 응답이나 로그에 기록하지 않는다.

### 3.2 Browser CSRF

Frontend는 Cookie 기반 Session을 사용하고 Spring Security CSRF가 활성화되어 있다.
따라서 Browser의 `POST`, `PUT`, `PATCH`, `DELETE` BFF 요청은 CSRF Header를 보내야 한다.

```http
X-CSRF-TOKEN: <page 또는 CSRF endpoint에서 받은 token>
```

CSRF를 전체 비활성화하지 않는다. 누락하면 Gateway에 도달하기 전에 Frontend가 `403`을 반환한다.

### 3.3 에러 전달

Learning Service의 공통 에러 Body에는 `status` 필드가 없다.

```json
{
  "code": "COHORT_NOT_FOUND",
  "message": "기수를 찾을 수 없습니다.",
  "path": "/api/v1/cohorts/999",
  "requestId": null
}
```

Frontend BFF는 downstream HTTP Status와 `code`, `message`를 유지하되 `path`는 Browser가
호출한 `/bff/v1/**` 경로로 바꾸거나, 별도 `downstreamPath` 필드 없이 그대로 숨기는 방식을
사용한다. HTML 오류로 바꾸지 않는다.

주요 Status:

| Status | 의미 |
|---|---|
| `400` | 잘못된 JSON, Enum, 날짜, Validation 실패 |
| `401` | Access Token 누락·만료·검증 실패 |
| `403` | 시스템 관리자·기수 관리자·기수 멤버 권한 부족 |
| `404` | 대상 기수·가입 코드·출결·게시글·캐릭터 없음 |
| `409` | 중복 가입, 중복 입실·퇴실, 이미 받은 보상 등 상태 충돌 |
| `500` | 서버 또는 첨부파일 저장 오류 |

Prototype용 `404 → null` fallback은 연동 완료 후 제거한다. `401`, `403`, `409`, `500`을
성공이나 빈 데이터로 처리하지 않는다.

## 4. 공통 데이터 형식

| Java 타입 | JSON 예시 |
|---|---|
| `LocalDate` | `2026-08-20` |
| `LocalTime` | `09:00:00` |
| `Instant` | `2026-08-20T00:10:00Z` |
| `OffsetDateTime` | `2026-08-20T09:10:00+09:00` |
| UUID | `00000000-0000-0000-0000-000000000001` |

`Instant`는 UTC로 내려오므로 화면 표시 시 Browser timezone으로 변환한다.

## 5. Browser BFF ↔ Gateway 매핑 요약

아래 BFF 경로는 Frontend 팀 권장 계약이다. Gateway 경로는 현재 Learning Service의 실제 계약이다.

### 5.1 사용자 화면

| Browser → Frontend BFF | Frontend BFF → Gateway | 비고 |
|---|---|---|
| `GET /bff/v1/me/profile` | `GET /api/v1/user-profiles/me/profile` | 초기 화면 Bootstrap |
| `PATCH /bff/v1/me/nickname` | `PATCH /api/v1/user-profiles/me/nickname` | `{ "nickname": "오마" }` |
| `GET /bff/v1/cohorts` | `GET /api/v1/cohorts` | 기수 목록 |
| `GET /bff/v1/cohorts/applications/me` | `GET /api/v1/cohorts/join-requests/me` | 내 신청/소속 목록 |
| `POST /bff/v1/cohorts/applications` | `POST /api/v1/cohorts/applications` | 가입 코드 신청 |
| `GET /bff/v1/attendance/history` | `GET /api/v1/cohorts/{approvedCohortId}/attendance-records/me` | BFF가 기수 ID 결정 |
| `GET /bff/v1/attendance/today` | 같은 `/attendance-records/me` 응답에서 오늘 기록 선택 | 전용 downstream API 없음 |
| `POST /bff/v1/attendance/check-in` | `POST /api/v1/cohorts/{approvedCohortId}/attendance-records/check-in` | BFF가 기수 ID 결정 |
| `POST /bff/v1/attendance/check-out` | `POST /api/v1/cohorts/{approvedCohortId}/attendance-records/check-out` | BFF가 기수 ID 결정 |
| `GET /bff/v1/community/posts` | `GET /api/v1/community/posts` | Query String 유지 |
| `GET /bff/v1/community/posts/{postId}` | `GET /api/v1/community/posts/{postId}` | 상세 |
| `POST /bff/v1/community/posts` | `POST /api/v1/community/posts` | JSON 또는 multipart |
| `PATCH /bff/v1/community/posts/{postId}` | `PATCH /api/v1/community/posts/{postId}` | JSON 또는 multipart |
| `DELETE /bff/v1/community/posts/{postId}` | `DELETE /api/v1/community/posts/{postId}` | `204` |
| `GET /bff/v1/gamification/characters` | `GET /api/v1/gamification/characters` | 캐릭터 마스터 |
| `POST /bff/v1/gamification/characters/representative` | 같은 경로의 `/api/v1` | 최초 대표 캐릭터 생성 |
| `GET /bff/v1/gamification/home` | `GET /api/v1/gamification/home` | 성장 + 일일 퀘스트 |
| `GET /bff/v1/gamification/quests/daily` | 같은 경로의 `/api/v1` | 일일 퀘스트 |
| `POST /bff/v1/gamification/quests/{id}/claim` | 같은 경로의 `/api/v1` | 보상 수령 |
| `GET /bff/v1/gamification/progression` | `GET /api/v1/gamification/progression` | `cohortId`, `aggregationDate` |
| `GET /bff/v1/cohorts/{cohortId}/study-rankings` | 같은 경로의 `/api/v1` | 학생용 목록 + 내 순위 |
| `GET /bff/v1/cohorts/{cohortId}/study-rankings/me` | 같은 경로의 `/api/v1` | 내 순위만 |
| `GET /bff/v1/presence` | `GET /api/v1/cohorts/me/presence` | 초기 Snapshot |

### 5.2 관리자 화면

| Browser → Frontend BFF | Frontend BFF → Gateway | 권한 |
|---|---|---|
| `POST /bff/v1/admin/cohorts` | `POST /api/v1/cohorts` | `SYSTEM_ADMIN` |
| `PATCH /bff/v1/admin/cohorts/{cohortId}` | `PATCH /api/v1/cohorts/{cohortId}` | 기수 `MANAGER` |
| `PATCH /bff/v1/admin/cohorts/{cohortId}/status` | `PATCH /api/v1/cohorts/{cohortId}/status` | `SYSTEM_ADMIN` |
| `GET /bff/v1/admin/cohorts/{cohortId}/members` | `GET /api/v1/cohorts/{cohortId}/members` | 기수 `MANAGER` |
| `GET /bff/v1/admin/cohorts/{cohortId}/applications` | `GET /api/v1/cohorts/{cohortId}/join-requests` | 기수 `MANAGER` |
| `POST /bff/v1/admin/cohorts/{cohortId}/managers` | `POST /api/v1/cohorts/{cohortId}/managers` | `SYSTEM_ADMIN` |
| `PATCH /bff/v1/admin/cohorts/{cohortId}/members/{userId}/role` | `PATCH /api/v1/cohorts/{cohortId}/members/{userId}/role` | 관리자 역할이 관여하면 `SYSTEM_ADMIN`, 그 외 기수 `MANAGER` |
| `PATCH /bff/v1/admin/memberships/{id}/approve` | `PATCH /api/v1/cohort-memberships/{id}/approve` | 관리자 역할 승인 시 `SYSTEM_ADMIN` |
| `PATCH /bff/v1/admin/memberships/{id}/reject` | `PATCH /api/v1/cohort-memberships/{id}/reject` | 기수 `MANAGER` |
| `GET /bff/v1/admin/cohorts/{cohortId}/join-code` | `GET /api/v1/cohorts/{cohortId}/join-code` | 기수 `MANAGER` |
| `POST /bff/v1/admin/cohorts/{cohortId}/join-code` | `POST /api/v1/cohorts/{cohortId}/join-code` | 기수 `MANAGER` |
| `PATCH /bff/v1/admin/cohorts/{cohortId}/join-code/revoke` | 같은 경로의 `/api/v1` | 기수 `MANAGER` |
| `GET /bff/v1/admin/cohorts/{cohortId}/attendance-policy` | 같은 경로의 `/api/v1` | 기수 `MANAGER` |
| `PUT /bff/v1/admin/cohorts/{cohortId}/attendance-policy` | 같은 경로의 `/api/v1` | 기수 `MANAGER` |
| `GET /bff/v1/admin/cohorts/{cohortId}/attendance-records?date=...` | `GET /api/v1/cohorts/{cohortId}/attendance-records?date=...` | 기수 `MANAGER` |
| `PATCH /bff/v1/admin/cohorts/{cohortId}/attendance-records/{id}/status` | 같은 경로의 `/api/v1` | 기수 `MANAGER`, 응답 `204` |
| `GET /bff/v1/admin/cohorts/{cohortId}/study-rankings` | `GET /api/v1/cohorts/{cohortId}/study-rankings/management` | 기수 `MANAGER` |
| `PATCH /bff/v1/admin/community/posts/{postId}/pin` | `PATCH /api/v1/community/posts/{postId}/pin` | `SYSTEM_ADMIN` |

현재 `GET /api/v1/cohorts/{cohortId}/audit-logs` API는 없다. Frontend에서 호출하지 않는다.

## 6. User Profile 계약

### 6.1 내 프로필

```http
GET /api/v1/user-profiles/me/profile
```

응답 `200 OK`:

```json
{
  "nickname": "오마",
  "totalStudySeconds": 14400,
  "completedSessionCount": 3,
  "attendanceStreakDays": 5,
  "approvedCohort": {
    "cohortId": 1,
    "name": "NHN Academy 1기",
    "startDate": "2026-08-01",
    "endDate": "2026-12-31",
    "cohortStatus": "ACTIVE",
    "role": "STUDENT",
    "membershipStatus": "ACTIVE"
  },
  "currentCharacter": {
    "nickname": "오마",
    "level": 3,
    "currentExp": 120,
    "requiredExp": 300,
    "name": "야간반",
    "type": "night",
    "colorId": "pistachio",
    "assetKey": "night/pistachio"
  }
}
```

처리 주의:

- 가입 승인 전에는 `approvedCohort=null`이다.
- 캐릭터 생성 전에는 `nickname=null`, `currentCharacter=null`이다.
- `type`은 Frontend의 `characterId`, `colorId`는 선택 색상, `assetKey`는 확장자를 제외한
  `/images/characters/**` 상대 키다.
- Frontend 정적 PNG 경로는 `/images/characters/${assetKey}.png`로 만든다.
- 출결 BFF는 `approvedCohort.cohortId`가 없으면 downstream을 호출하지 않고 가입 안내 상태를 반환한다.

### 6.2 닉네임 변경

```http
PATCH /api/v1/user-profiles/me/nickname
Content-Type: application/json
```

```json
{
  "nickname": "새이름"
}
```

닉네임은 trim 후 2~12자이며, 대표 캐릭터가 없으면 `404 REPRESENTATIVE_CHARACTER_NOT_FOUND`다.

## 7. Cohort 계약

### 7.1 기본 정보

```http
POST  /api/v1/cohorts
GET   /api/v1/cohorts
GET   /api/v1/cohorts/{cohortId}
PATCH /api/v1/cohorts/{cohortId}
PATCH /api/v1/cohorts/{cohortId}/status
```

생성·수정 요청:

```json
{
  "name": "NHN Academy 1기",
  "description": "학습 기수",
  "startDate": "2026-08-01",
  "endDate": "2026-12-31"
}
```

상태 변경:

```json
{
  "status": "ACTIVE"
}
```

기수 응답:

```json
{
  "id": 1,
  "name": "NHN Academy 1기",
  "description": "학습 기수",
  "startDate": "2026-08-01",
  "endDate": "2026-12-31",
  "status": "ACTIVE",
  "createdByUserId": "00000000-0000-0000-0000-000000000001",
  "createdAt": "2026-08-20T09:00:00+09:00",
  "updatedAt": "2026-08-20T09:00:00+09:00"
}
```

### 7.2 가입 코드·신청·소속

```http
GET   /api/v1/cohorts/{cohortId}/join-code
POST  /api/v1/cohorts/{cohortId}/join-code
PATCH /api/v1/cohorts/{cohortId}/join-code/revoke
POST  /api/v1/cohorts/join-requests
POST  /api/v1/cohorts/applications
GET   /api/v1/cohorts/join-requests/me
GET   /api/v1/cohorts/{cohortId}/join-requests
GET   /api/v1/cohorts/{cohortId}/members
PATCH /api/v1/cohort-memberships/{membershipId}/approve
PATCH /api/v1/cohort-memberships/{membershipId}/reject
POST  /api/v1/cohorts/{cohortId}/managers
PATCH /api/v1/cohorts/{cohortId}/members/{memberUserId}/role
```

`POST /join-requests`와 `POST /applications`는 현재 같은 동작의 alias다. Frontend는
`/applications` 하나만 사용한다.

가입 신청 요청은 `joinCode`를 표준 필드로 사용한다. `code`도 호환되지만 신규 코드는 사용하지 않는다.

```json
{
  "joinCode": "ABC123"
}
```

가입 코드 발급:

```json
{
  "expiresAt": "2026-08-31T23:59:59+09:00"
}
```

발급 `POST` 응답에만 원문 `code`가 포함된다. 이후 `GET` 응답에는 원문 코드가 없다.

승인·역할 변경:

```json
{
  "role": "STUDENT"
}
```

관리자 직접 지정:

```json
{
  "userId": "00000000-0000-0000-0000-000000000002"
}
```

거절:

```json
{
  "reason": "대상자가 아닙니다."
}
```

소속 응답:

```json
{
  "id": 10,
  "cohortId": 1,
  "userId": "00000000-0000-0000-0000-000000000002",
  "role": "STUDENT",
  "status": "ACTIVE",
  "requestedAt": "2026-08-20T09:00:00+09:00",
  "processedAt": "2026-08-20T09:10:00+09:00",
  "processedByUserId": "00000000-0000-0000-0000-000000000001",
  "rejectionReason": null,
  "endedAt": null
}
```

### 7.3 출결 정책

```http
GET /api/v1/cohorts/{cohortId}/attendance-policy
PUT /api/v1/cohorts/{cohortId}/attendance-policy
```

```json
{
  "timezone": "Asia/Seoul",
  "scheduledStartTime": "09:00:00",
  "scheduledEndTime": "18:00:00",
  "absenceCutoffTime": "10:00:00",
  "allowedAwayMinutes": 30
}
```

`absenceCutoffTime`은 nullable이고 `allowedAwayMinutes`는 0 이상이다.

## 8. Attendance 계약

```http
POST  /api/v1/cohorts/{cohortId}/attendance-records/check-in
POST  /api/v1/cohorts/{cohortId}/attendance-records/check-out
GET   /api/v1/cohorts/{cohortId}/attendance-records/me
GET   /api/v1/cohorts/{cohortId}/attendance-records?date=2026-08-20
PATCH /api/v1/cohorts/{cohortId}/attendance-records/{attendance-id}/status
```

- `/me`: 날짜 내림차순 배열
- `?date=`: 관리자용 배열. 서버 내부에서는 `cohortMembershipId` 오름차순으로 안정 정렬하지만
  이 내부 식별자는 HTTP 응답에 노출하지 않는다.
- check-in/check-out: `AttendanceRecordResponse` 단건
- 관리자 상태 변경: `204 No Content`

응답:

```json
{
  "id": 10,
  "attendanceDate": "2026-08-20",
  "autoStatus": "LATE",
  "finalStatus": "PRESENT",
  "checkedInAt": "2026-08-20T00:10:00Z",
  "checkedOutAt": "2026-08-20T09:00:00Z",
  "lateMinutes": 10,
  "earlyLeaveMinutes": 0,
  "version": 2,
  "createdAt": "2026-08-20T00:10:00Z",
  "updatedAt": "2026-08-20T09:05:00Z"
}
```

현재 Frontend prototype은 `checkInAt`, `checkOutAt`을 사용하지만 서버 필드는
`checkedInAt`, `checkedOutAt`이다. Frontend 상태 모델을 서버 필드명으로 통일한다.

관리자 상태 변경:

```json
{
  "nextStatus": "PRESENT",
  "reason": "관리자 수동 정정",
  "requestId": "manual-20260820-001"
}
```

`requestId`는 빈 문자열이면 안 되며 Frontend에서 요청마다 고유하게 생성한다.

## 9. Community 계약

```http
GET    /api/v1/community/posts?page=0&size=20&type=NOTICE&search=공지
GET    /api/v1/community/posts/{postId}
POST   /api/v1/community/posts
PATCH  /api/v1/community/posts/{postId}
DELETE /api/v1/community/posts/{postId}
PATCH  /api/v1/community/posts/{postId}/pin
```

목록 기본값은 `page=0`, `size=20`이며 `size` 범위는 1~100이다. 정렬은 고정글 우선,
그다음 생성 시각·ID 내림차순이다.

생성 JSON:

```json
{
  "type": "FREE",
  "title": "질문 있습니다",
  "content": "내용입니다.",
  "scope": "COHORT",
  "cohortId": 1
}
```

권한 규칙:

- `FREE`: `COHORT` 범위만 가능하며 해당 기수 ACTIVE 멤버가 작성한다.
- `NOTICE`: 기수 `MANAGER`/`MENTOR`가 `COHORT` 공지를 작성할 수 있다.
- `SYSTEM_ADMIN`: `GLOBAL` 공지 또는 기수 공지를 작성할 수 있다.
- `FREE` 수정·삭제: 작성자만 가능하다.
- 공지 수정·삭제: 해당 권한자만 가능하다.
- 고정 여부 변경: `SYSTEM_ADMIN`만 가능하다.

목록 응답:

```json
{
  "items": [
    {
      "postId": 1,
      "type": "NOTICE",
      "title": "공지",
      "authorUserId": "00000000-0000-0000-0000-000000000001",
      "scope": "COHORT",
      "cohortId": 1,
      "pinned": true,
      "createdAt": "2026-08-20T00:00:00Z",
      "updatedAt": "2026-08-20T00:00:00Z",
      "attachmentCount": 1
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

목록 Item에는 본문 `content`가 없다. 게시글을 열 때 상세 API를 호출한다.

상세 응답:

```json
{
  "postId": 1,
  "type": "FREE",
  "title": "질문 있습니다",
  "content": "내용입니다.",
  "authorUserId": "00000000-0000-0000-0000-000000000001",
  "scope": "COHORT",
  "cohortId": 1,
  "pinned": false,
  "createdAt": "2026-08-20T00:00:00Z",
  "updatedAt": "2026-08-20T00:00:00Z",
  "attachments": [
    {
      "attachmentId": 10,
      "originalFileName": "image.png",
      "contentType": "image/png",
      "sizeBytes": 12000,
      "displayOrder": 0
    }
  ]
}
```

첨부파일 생성은 `multipart/form-data`다.

```text
part "post"
  Content-Type: application/json
  value: {"type":"FREE","title":"제목","content":"내용","scope":"COHORT","cohortId":1}

part "attachments"
  0~5개 이미지 파일
```

허용 확장자·Content-Type은 `jpg`, `jpeg`, `png`, `gif`와 대응 이미지 MIME이며 기본 파일당
최대 5MB다. Browser `FormData`를 BFF에서 JSON stringify하지 말고 multipart 그대로 재구성한다.

## 10. Gamification 계약

```http
GET  /api/v1/gamification/characters
POST /api/v1/gamification/characters/representative
GET  /api/v1/gamification/home
GET  /api/v1/gamification/quests/daily
GET  /api/v1/gamification/progression?cohortId=1&aggregationDate=2026-08-20
POST /api/v1/gamification/quests/{userDailyQuestId}/claim
```

캐릭터 목록:

```json
[
  {
    "gameCharacterId": 1,
    "code": "NIGHT_CLASS",
    "assetKey": "night",
    "name": "야간반",
    "description": "기본 캐릭터"
  }
]
```

대표 캐릭터 생성:

```json
{
  "gameCharacterId": 1,
  "nickname": "오마",
  "colorId": "pistachio"
}
```

응답은 `201 Created`다. Frontend는 목록 응답의 `assetKey`로 기존 `characterId`를
`gameCharacterId`에 매핑하고 `colorId`를 함께 보낸다.

```json
{
  "userCharacterId": 10,
  "gameCharacterId": 1,
  "gameCharacterCode": "NIGHT_CLASS",
  "type": "night",
  "colorId": "pistachio",
  "assetKey": "night/pistachio",
  "gameCharacterName": "야간반",
  "nickname": "오마",
  "displayName": "오마",
  "totalXp": 0,
  "level": 1,
  "advancementStage": "BASE",
  "representative": true
}
```

- Frontend는 먼저 `/characters`에서 숫자 `gameCharacterId`를 받는다.
- 선택 UI는 이 ID, 사용자가 입력한 `nickname`, `colorId`를 전송한다.
- 허용 색상은 `original`, `pistachio`, `cyan`, `cream_can`, `light_coral`,
  `light_purple`, `white`, `dark_gray`다. 생략하면 `original`이다.

홈 응답:

```json
{
  "growth": {
    "nickname": "오마",
    "displayName": "오마",
    "totalXp": 1000,
    "level": 3,
    "currentLevelXp": 100,
    "nextLevelRequiredXp": 300,
    "advancementStage": "FIRST"
  },
  "dailyQuests": [
    {
      "id": 1,
      "questDate": "2026-08-20",
      "type": "ROUTINE",
      "code": "ATTENDANCE",
      "title": "출석하기",
      "targetCount": 1,
      "progressCount": 1,
      "rewardXp": 50,
      "status": "COMPLETED"
    }
  ]
}
```

진행도 응답:

```json
{
  "aggregationDate": "2026-08-20",
  "studySeconds": 28800,
  "reachedFourHours": true,
  "reachedSixHours": true,
  "reachedEightHours": true,
  "currentWeekdayStreakDays": 5,
  "streakQualified": true
}
```

`/api/v1/gamification/events/**`는 공개 API가 아니며 Controller에서도 제거되었다. Frontend가
출석이나 학습 완료 후 이벤트 API를 추가로 호출하면 안 된다. 출석 체크인과 학습 기록 생성·타이머
정상 종료가 커밋되면 Learning Service가 내부 이벤트로 일일 퀘스트를 진행한다. Frontend는 해당
Domain API 성공 후 `/gamification/home` 또는 `/gamification/quests/daily`를 재조회한다.

`character-checked`, `llm-quest-completed`는 신뢰할 수 있는 내부 발생 조건이 확정될 때까지
외부에서 발생시킬 수 없다.

## 11. Ranking 계약

학생 목록 + 내 순위:

```http
GET /api/v1/cohorts/{cohortId}/study-rankings?period=WEEKLY&maxRank=100
```

내 순위만:

```http
GET /api/v1/cohorts/{cohortId}/study-rankings/me?period=WEEKLY
```

관리자 목록:

```http
GET /api/v1/cohorts/{cohortId}/study-rankings/management?period=WEEKLY&maxRank=100
```

- `period`: `DAILY`, `WEEKLY`, `MONTHLY`
- `maxRank`: 생략 시 100, 범위 1~1000
- 예전 문서의 `baseDate` Query Parameter는 없다.
- 응답에 `period`, `baseDate`, `rangeStartDate`, `generatedAt`이 포함되지 않는다.

학생 응답:

```json
{
  "rankedMemberCount": 30,
  "returnedEntryCount": 2,
  "entries": [
    {
      "rank": 1,
      "displayName": "첫째",
      "studySeconds": 7200
    },
    {
      "rank": 2,
      "displayName": "둘째",
      "studySeconds": 3600
    }
  ],
  "myRanking": {
    "ranked": true,
    "ranking": {
      "rank": 3,
      "displayName": "오마",
      "studySeconds": 1800
    }
  }
}
```

순위가 없으면 `ranked=false`, `ranking=null`이다.

관리자 응답은 `rankedMemberCount`, `returnedEntryCount`, `entries`만 포함하고 `myRanking`은 없다.

## 12. Presence 계약

### 12.1 초기 Snapshot

```http
GET /api/v1/cohorts/me/presence
```

```json
{
  "cohortId": 1,
  "users": [
    {
      "userId": "00000000-0000-0000-0000-000000000002",
      "status": "ONLINE"
    }
  ],
  "occurredAt": "2026-08-20T09:00:00+09:00"
}
```

사용자의 현재 ACTIVE 기수를 서버가 결정한다. 기수 ID Query Parameter는 받지 않는다.

### 12.2 Learning WebSocket/STOMP

```text
Handshake endpoint: /ws
STOMP CONNECT Header: Authorization: Bearer <access-token>
SEND: /app/presence/heartbeat
SUBSCRIBE: /topic/cohorts/{cohortId}/presence
SUBSCRIBE: /user/queue/notifications
Status: ONLINE, AWAY, OFFLINE
```

현재 Gateway에는 `/ws` route가 없고 Browser에는 Access Token을 노출하지 않으므로 Browser가
Learning STOMP에 직접 접속할 수 없다.

Frontend에서 현재 사용하는 EventSource 구조를 유지하려면 다음 BFF Bridge를 구현한다.

```text
Browser EventSource
  GET /bff/v1/presence/stream
    → Frontend BFF가 Session Access Token으로 Gateway/Learning STOMP CONNECT
    → /topic/cohorts/{cohortId}/presence 구독
    → Snapshot을 SSE data로 Browser에 중계
    → 연결 동안 /app/presence/heartbeat 주기 전송
```

Gateway WebSocket route는 외부 경로와 Learning upstream을 다음처럼 분리한다.

```text
Gateway 외부 경로: ws://localhost:8080/ws
Gateway route predicate: Path=/ws
Gateway route URI: ws://localhost:8084
Learning endpoint: /ws
```

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: learning-websocket
          uri: ws://localhost:8084
          predicates:
            - Path=/ws
```

위 구성은 Gateway가 받은 `/ws`를 그대로 Learning Service의 `/ws`로 전달하므로
`RewritePath`나 `SetPath` filter가 필요 없다.

HTTP Upgrade Handshake에는 Bearer Token을 요구하지 않고 `/ws`를 Learning Service로 전달한다.
인증은 Learning Service가 STOMP `CONNECT` 프레임의
`Authorization: Bearer <access-token>`을 검증하는 정책이다. Token이 없거나 유효하지 않으면
STOMP 연결을 거부하며, `SUBSCRIBE`는 JWT 사용자의 ACTIVE 기수 소속 여부를 추가로 검사한다.
Gateway에 이 route가 추가되기 전까지는 REST Snapshot만 연동 가능한 상태로 본다.

## 13. Enum 목록

```text
CohortStatus: PREPARING, ACTIVE, CLOSED
CohortMembershipRole: MANAGER, MENTOR, STUDENT
CohortMembershipStatus: PENDING, ACTIVE, REJECTED, ENDED
CohortJoinCodeStatus: ACTIVE, REVOKED

AttendanceStatus:
PENDING, PRESENT, LATE, ABSENT, LEFT_EARLY, LATE_LEFT_EARLY, MISSING_CHECK_OUT

CommunityPostType: NOTICE, FREE
CommunityPostScope: GLOBAL, COHORT

QuestType: ROUTINE, LLM
QuestStatus: IN_PROGRESS, COMPLETED, CLAIMED, EXPIRED
AdvancementStage: BASE, FIRST, SECOND, THIRD

StudyRankingPeriod: DAILY, WEEKLY, MONTHLY
PresenceStatus: ONLINE, AWAY, OFFLINE
```

## 14. 현재 Frontend 코드에서 반드시 바꿀 부분

| 현재 Frontend | 실제 계약/조치 |
|---|---|
| `api.js`의 API Base `/bff/v1` | 유지하고 BFF Controller를 실제 구현한다. |
| `attendance/history`, `attendance/today` | BFF가 Profile의 `approvedCohortId`로 `/attendance-records/me` 호출 후 변환한다. |
| `checkInAt`, `checkOutAt` | `checkedInAt`, `checkedOutAt`으로 변경한다. |
| `character.saveSelection({characterId, colorId})` | `{gameCharacterId, nickname}`으로 변경한다. |
| Character ID가 `study` 같은 문자열 | `/gamification/characters`의 숫자 ID를 사용한다. |
| Presence `/presence/lab/stream` SSE | BFF STOMP→SSE Bridge를 만들거나 REST Snapshot까지만 우선 연동한다. |
| 출결·프로필·레벨 `localStorage` | 서버 응답을 Source of Truth로 바꾸고 Prototype 저장을 제거한다. |
| `404`일 때 Prototype fallback | 실제 연동 완료 후 제거한다. |
| BFF 쓰기 요청에 CSRF Header 없음 | `X-CSRF-TOKEN`을 모든 쓰기 요청에 추가한다. |
| Community JSON 전용 request helper | 첨부파일용 `FormData` request helper를 추가한다. |

## 15. 현재 계약으로 화면을 완성할 수 없는 항목

다음 항목은 Frontend 매핑만으로 해결되지 않으며 Backend/API 추가 또는 Identity 조합이 필요하다.

1. Presence 사용자 표시
   - 응답에는 `userId`, `status`만 있다.
   - 현재 UI가 요구하는 사용자 이름·닉네임·캐릭터 이미지가 없다.
   - UUID 목록을 사용자 요약으로 바꾸는 Batch API 또는 BFF의 Identity/Learning 조합이 필요하다.

2. 기수 멤버 관리자 화면
   - 소속 응답에는 `userId`만 있고 이름·이메일이 없다.
   - BFF에서 Identity 사용자 정보와 조합하거나 Backend Projection을 추가해야 한다.

3. 커뮤니티 첨부파일 표시
   - 상세 응답에는 첨부 메타데이터만 있고 다운로드 URL/API가 없다.
   - 인증된 첨부파일 조회 endpoint 또는 외부 Object Storage URL 계약이 필요하다.

4. 감사 로그
   - 과거 문서의 `/cohorts/{cohortId}/audit-logs`는 현재 구현되어 있지 않다.

## 16. 연동 권장 순서

1. Frontend Gateway HTTP Client와 Bearer Token Relay 구현
2. `GET /bff/v1/me/profile` 구현
3. 가입 상태·대표 캐릭터 유무에 따른 화면 분기
4. 출결 조회·입실·퇴실 구현 후 `localStorage` 제거
5. Gamification 캐릭터·홈·퀘스트 구현
6. Community JSON CRUD 구현
7. Community multipart 구현
8. 학생/관리자 Ranking 구현
9. 관리자 Cohort·출결 정책·신청 관리 구현
10. Presence REST Snapshot 구현
11. Gateway WebSocket + BFF STOMP→SSE Bridge 구현
12. 위 “추가 계약 필요” 항목 합의 및 Backend 보완

## 17. Frontend 완료 체크리스트

- [ ] Browser가 `/api/v1/**`를 직접 호출하지 않는다.
- [ ] Access Token이 Browser 저장소와 응답에 노출되지 않는다.
- [ ] BFF outbound 요청에 Bearer Token이 한 번만 들어간다.
- [ ] CSRF 누락으로 쓰기 요청이 `403` 되지 않는다.
- [ ] Profile의 nullable `approvedCohort`, `currentCharacter`를 처리한다.
- [ ] 출결 필드명을 `checkedInAt`, `checkedOutAt`으로 사용한다.
- [ ] 가입 요청은 `joinCode` 필드를 사용한다.
- [ ] Community PageInfo를 `response.page.*`에서 읽는다.
- [ ] Multipart의 `post` part Content-Type이 `application/json`이다.
- [ ] Ranking에 제거된 `baseDate`를 보내거나 읽지 않는다.
- [ ] `204 No Content`를 JSON parsing하지 않는다.
- [ ] `401`, `403`, `409`, `500`을 빈 성공 응답으로 바꾸지 않는다.
- [ ] Prototype `localStorage`와 Mock API 의존을 제거했다.
- [ ] Gateway `8080`, Learning `8084` Health 확인 후 E2E를 수행했다.
