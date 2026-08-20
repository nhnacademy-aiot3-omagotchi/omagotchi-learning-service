# Frontend API Handoff

> **Deprecated:** 이 문서는 2026-08-10 시점의 과거 작업 참고 자료다. 현재 Frontend 연동은
> [`Frontend-Learning-API-Integration-Handoff.md`](Front-LearningService/Frontend-Learning-API-Integration-Handoff.md)를 사용한다.
> 특히 `/api/v1` Prefix, User Profile, Ranking, Presence/BFF 계약은 새 문서가 기준이다.

범위: `attendance`, `cohort`, `community`, `gamification`, `ranking`, `realtime`, `telegram`, `user`.

제외: `space`, `team`, `occupancy`, 일반 타이머/학습 세션 CRUD. 단, 게이미피케이션 진행도와 랭킹에서 노출되는 `studySeconds`, streak 관련 값은 포함한다.

## 공통

기본 요청/응답은 JSON이다.

날짜/시간 형식:

- `LocalDate`: `2026-08-10`
- `LocalTime`: `09:00:00`
- `Instant`: `2026-08-10T00:10:00Z`
- `OffsetDateTime`: `2026-08-10T09:00:00+09:00`

공통 에러 응답:

```json
{
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/...",
  "requestId": null
}
```

## User

### 내 프로필

```http
GET /api/users/me/profile
```

프론트는 여기서 `approvedCohort.cohortId`를 꺼내 기수별 API 호출에 붙이면 된다.

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
    "type": "NIGHT_CLASS",
    "assetKey": "NIGHT_CLASS"
  }
}
```

### 닉네임 변경

```http
PATCH /api/users/me/nickname
```

```json
{
  "nickname": "오마"
}
```

응답:

```json
{
  "nickname": "오마"
}
```

## Cohort

### 기수 기본 API

```http
POST /api/cohorts
GET /api/cohorts
GET /api/cohorts/{cohortId}
PATCH /api/cohorts/{cohortId}
PATCH /api/cohorts/{cohortId}/status
```

생성/수정 요청:

```json
{
  "name": "NHN Academy 1기",
  "description": "학습 기수",
  "startDate": "2026-08-01",
  "endDate": "2026-12-31"
}
```

상태 변경 요청:

```json
{
  "status": "ACTIVE"
}
```

응답 `CohortResponse`:

```json
{
  "id": 1,
  "name": "NHN Academy 1기",
  "description": "학습 기수",
  "startDate": "2026-08-01",
  "endDate": "2026-12-31",
  "status": "ACTIVE",
  "createdByUserId": "00000000-0000-0000-0000-000000000001",
  "createdAt": "2026-08-10T09:00:00+09:00",
  "updatedAt": "2026-08-10T09:00:00+09:00"
}
```

### 가입 코드 / 신청 / 멤버

```http
GET /api/cohorts/{cohortId}/join-code
POST /api/cohorts/{cohortId}/join-code
PATCH /api/cohorts/{cohortId}/join-code/revoke
POST /api/cohorts/join-requests
POST /api/cohorts/applications
GET /api/cohorts/join-requests/me
GET /api/cohorts/{cohortId}/join-requests
GET /api/cohorts/{cohortId}/members
PATCH /api/cohort-memberships/{membershipId}/approve
PATCH /api/cohort-memberships/{membershipId}/reject
POST /api/cohorts/{cohortId}/managers
PATCH /api/cohorts/{cohortId}/members/{memberUserId}/role
```

가입 코드 발급:

```json
{
  "expiresAt": "2026-08-31T23:59:59+09:00"
}
```

가입 신청:

```json
{
  "joinCode": "ABC123"
}
```

승인/역할 변경:

```json
{
  "role": "STUDENT"
}
```

거절:

```json
{
  "reason": "대상자가 아닙니다."
}
```

멤버 응답:

```json
{
  "id": 10,
  "cohortId": 1,
  "userId": "00000000-0000-0000-0000-000000000002",
  "role": "STUDENT",
  "status": "ACTIVE",
  "requestedAt": "2026-08-10T09:00:00+09:00",
  "processedAt": "2026-08-10T09:10:00+09:00",
  "processedByUserId": "00000000-0000-0000-0000-000000000001",
  "rejectionReason": null,
  "endedAt": null
}
```

### 출결 정책

기수 매니저용이다.

```http
GET /api/cohorts/{cohortId}/attendance-policy
PUT /api/cohorts/{cohortId}/attendance-policy
```

저장 요청:

```json
{
  "timezone": "Asia/Seoul",
  "scheduledStartTime": "09:00:00",
  "scheduledEndTime": "18:00:00",
  "absenceCutoffTime": "10:00:00",
  "allowedAwayMinutes": 30
}
```

응답:

```json
{
  "cohortId": 1,
  "timezone": "Asia/Seoul",
  "scheduledStartTime": "09:00:00",
  "scheduledEndTime": "18:00:00",
  "absenceCutoffTime": "10:00:00",
  "allowedAwayMinutes": 30,
  "updatedByUserId": "00000000-0000-0000-0000-000000000001",
  "updatedAt": "2026-08-10T09:00:00+09:00"
}
```

### 감사 로그

기수 매니저용이다.

```http
GET /api/cohorts/{cohortId}/audit-logs
```

```json
[
  {
    "id": 1,
    "cohortId": 1,
    "actorUserId": "00000000-0000-0000-0000-000000000001",
    "targetType": "COHORT_MEMBERSHIP",
    "targetId": 10,
    "action": "CHANGE_MEMBER_ROLE",
    "beforeValue": null,
    "afterValue": null,
    "reason": "관리자 수동 정정",
    "requestId": "manual-001",
    "occurredAt": "2026-08-10T09:00:00+09:00"
  }
]
```

## Attendance

```http
POST /api/cohorts/{cohortId}/attendance-records/check-in
POST /api/cohorts/{cohortId}/attendance-records/check-out
GET /api/cohorts/{cohortId}/attendance-records/me
GET /api/cohorts/{cohortId}/attendance-records?date=2026-08-10
PATCH /api/cohorts/{cohortId}/attendance-records/{attendance-id}/status
```

상태 변경 요청:

```json
{
  "nextStatus": "PRESENT",
  "reason": "관리자 수동 정정",
  "requestId": "manual-20260810-001"
}
```

`check-in`, `check-out`, 조회 응답:

```json
{
  "id": 10,
  "cohortMembershipId": 100,
  "attendanceDate": "2026-08-10",
  "autoStatus": "LATE",
  "finalStatus": "PRESENT",
  "checkedInAt": "2026-08-10T00:10:00Z",
  "checkedOutAt": "2026-08-10T09:00:00Z",
  "lateMinutes": 10,
  "earlyLeaveMinutes": 0,
  "version": 2,
  "createdAt": "2026-08-10T00:10:00Z",
  "updatedAt": "2026-08-10T09:05:00Z"
}
```

상태 변경 응답: `204 No Content`

## Community

게시글 목록/상세/생성/수정/삭제/고정 API다.

```http
GET /api/community/posts?page=0&size=20&type=NOTICE&search=공지
GET /api/community/posts/{postId}
POST /api/community/posts
POST /api/community/posts (multipart/form-data)
PATCH /api/community/posts/{postId}
PATCH /api/community/posts/{postId} (multipart/form-data)
DELETE /api/community/posts/{postId}
PATCH /api/community/posts/{postId}/pin
```

JSON 생성 요청:

```json
{
  "type": "NOTICE",
  "title": "공지 제목",
  "content": "공지 내용",
  "scope": "COHORT",
  "cohortId": 1
}
```

multipart 생성/수정:

- `post`: `CreateCommunityPostRequest` 또는 `UpdateCommunityPostRequest` JSON part
- `attachments`: 파일 배열 part, optional

수정 요청:

```json
{
  "title": "수정 제목",
  "content": "수정 내용"
}
```

고정 요청:

```json
{
  "pinned": true
}
```

목록 응답:

```json
{
  "items": [
    {
      "postId": 1,
      "type": "NOTICE",
      "title": "공지 제목",
      "authorUserId": "00000000-0000-0000-0000-000000000001",
      "scope": "COHORT",
      "cohortId": 1,
      "pinned": true,
      "createdAt": "2026-08-10T00:10:00Z",
      "updatedAt": "2026-08-10T00:10:00Z",
      "attachmentCount": 1
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

상세 응답:

```json
{
  "postId": 1,
  "type": "NOTICE",
  "title": "공지 제목",
  "content": "공지 내용",
  "authorUserId": "00000000-0000-0000-0000-000000000001",
  "scope": "COHORT",
  "cohortId": 1,
  "pinned": true,
  "createdAt": "2026-08-10T00:10:00Z",
  "updatedAt": "2026-08-10T00:10:00Z",
  "attachments": [
    {
      "attachmentId": 10,
      "originalFileName": "notice.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 12000,
      "displayOrder": 0
    }
  ]
}
```

## Gamification

### 캐릭터 온보딩

```http
GET /gamification/characters
POST /gamification/characters/representative
```

캐릭터 목록 응답:

```json
[
  {
    "gameCharacterId": 1,
    "code": "NIGHT_CLASS",
    "name": "야간반",
    "description": "기본 캐릭터"
  }
]
```

대표 캐릭터 생성 요청:

```json
{
  "gameCharacterId": 1,
  "nickname": "오마"
}
```

대표 캐릭터 응답:

```json
{
  "userCharacterId": 10,
  "gameCharacterId": 1,
  "gameCharacterCode": "NIGHT_CLASS",
  "gameCharacterName": "야간반",
  "nickname": "오마",
  "displayName": "오마",
  "totalXp": 0,
  "level": 1,
  "advancementStage": "BASE",
  "representative": true
}
```

주의:

- `gameCharacterId`는 `BIGINT`다.
- 캐릭터 마스터 ERD는 `id BIGINT`, `code VARCHAR(30)`, `name VARCHAR(50)`, `description VARCHAR(255)`, `active BOOLEAN`, `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ` 기준으로 맞췄다.

### 홈 / 퀘스트 / 레벨

```http
GET /gamification/home
GET /gamification/quests/daily
POST /gamification/quests/{userDailyQuestId}/claim
POST /gamification/events/attendance
POST /gamification/events/study-completed
POST /gamification/events/character-checked
POST /gamification/events/llm-quest-completed
```

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
      "questDate": "2026-08-10",
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

퀘스트 이벤트/보상 수령 응답은 `DailyQuestResponse` 단건이다.

### 진행도 / 스트릭

```http
GET /gamification/progression?cohortId=1&aggregationDate=2026-08-10
```

`aggregationDate` 생략 시 서버 기준일을 사용한다.

```json
{
  "aggregationDate": "2026-08-10",
  "studySeconds": 28800,
  "reachedFourHours": true,
  "reachedSixHours": true,
  "reachedEightHours": true,
  "currentWeekdayStreakDays": 5,
  "streakQualified": true
}
```

## Ranking

```http
GET /rankings/study?cohortId=1&period=WEEKLY&baseDate=2026-08-10
```

`period`: `DAILY`, `WEEKLY`, `MONTHLY`  
`baseDate` 생략 시 서버 기준일을 사용한다.

```json
{
  "period": "WEEKLY",
  "baseDate": "2026-08-10",
  "rangeStartDate": "2026-08-10",
  "rangeEndDate": "2026-08-16",
  "generatedAt": "2026-08-10T00:10:00Z",
  "top10": [
    {
      "rank": 1,
      "displayName": "오마",
      "studySeconds": 28800
    }
  ],
  "myRank": {
    "rank": 3,
    "displayName": "내 캐릭터",
    "studySeconds": 14400
  }
}
```

## Realtime

### Presence Snapshot

```http
GET /api/cohorts/me/presence
```

현재 사용자의 ACTIVE 기수 기준 초기 snapshot이다.

```json
{
  "cohortId": 1,
  "users": [
    {
      "userId": "00000000-0000-0000-0000-000000000002",
      "status": "ONLINE"
    }
  ],
  "occurredAt": "2026-08-10T09:00:00+09:00"
}
```

### WebSocket/STOMP

```text
Endpoint: /ws
Application prefix: /app
Topic prefix: /topic
User queue prefix: /user
```

프론트 송신:

```text
SEND /app/presence/heartbeat
```

프론트 구독:

```text
SUBSCRIBE /topic/cohorts/{cohortId}/presence
SUBSCRIBE /user/queue/notifications
```

Presence status enum: `ONLINE`, `AWAY`, `OFFLINE`

## Telegram

```http
POST /api/telegram/link-token
GET /api/telegram/link
PATCH /api/telegram/link/notification
DELETE /api/telegram/link
POST /api/telegram/webhook
```

`/api/telegram/webhook`은 텔레그램 서버가 호출하는 엔드포인트라 일반 프론트 화면에서는 호출하지 않는다.

링크 토큰 발급 응답:

```json
{
  "linkUrl": "https://t.me/bot?start=token",
  "expiresAt": "2026-08-10T09:10:00+09:00"
}
```

내 연동 상태 응답:

```json
{
  "userId": "00000000-0000-0000-0000-000000000001",
  "telegramUserId": 123456789,
  "telegramChatId": 123456789,
  "notificationEnabled": true,
  "linkedAt": "2026-08-10T09:00:00+09:00",
  "disconnectedAt": null
}
```

알림 설정 변경:

```json
{
  "enabled": true
}
```

응답은 연동 상태 응답과 동일하다.

## Enum

```text
CohortStatus: PREPARING, ACTIVE, CLOSED
CohortMembershipRole: MANAGER, MENTOR, STUDENT
CohortMembershipStatus: PENDING, ACTIVE, REJECTED, ENDED
CohortJoinCodeStatus: ACTIVE, EXPIRED, REVOKED

AttendanceStatus: PENDING, PRESENT, LATE, ABSENT, LEFT_EARLY, LATE_LEFT_EARLY, MISSING_CHECK_OUT

CommunityPostType: NOTICE, FREE
CommunityPostScope: GLOBAL, COHORT

AdvancementStage: BASE, FIRST, SECOND, THIRD
QuestType: ROUTINE, LLM
QuestStatus: IN_PROGRESS, COMPLETED, CLAIMED, EXPIRED

RankingPeriod: DAILY, WEEKLY, MONTHLY
PresenceStatus: ONLINE, AWAY, OFFLINE
```
