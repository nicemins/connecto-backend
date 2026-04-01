# Plan: friend-block

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | friend-block |
| 작성일 | 2026-03-18 |
| 상태 | Plan |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 원하지 않는 사용자와 계속 매칭되거나 친구 신청을 받는 상황을 제어할 수단이 없음 |
| Solution | 친구 삭제 + 차단/해제 API + 친구 여부 사전 확인 API 제공 |
| Function UX Effect | 통화 종료 후 친구 신청 버튼 상태 즉시 반영, 차단된 사용자는 매칭 큐에서 자동 제외 |
| Core Value | 사용자가 안전하고 편안한 소통 환경을 직접 제어할 수 있게 함 |

---

## 1. 기능 정의

### 1.1 친구 삭제
- `DELETE /friends/{friendshipId}` → 204 No Content
- 요청자가 해당 friendship의 user1 또는 user2여야 함 (아니면 403)
- Friendship 레코드 삭제

### 1.2 친구 차단
- `POST /friends/{friendshipId}/block` → 200 `{ success: true }`
- 차단 시 동작:
  1. 기존 Friendship 삭제
  2. Block 레코드 생성 (blocker → blocked)
  3. 이후 매칭에서 양방향 제외
  4. 이후 친구 신청 불가
- friendshipId로 차단 대상 식별 (내가 해당 friendship의 멤버여야 함)

### 1.3 차단 해제
- `DELETE /users/me/blocks/{blockedUserId}` → 204 No Content
- friendshipId는 차단 시 이미 삭제되므로 blockedUserId 기준으로 처리
- 차단한 본인만 해제 가능

### 1.4 친구 여부 사전 확인
- `GET /friends/check?userId={userId}` → `{ isFriend: boolean, friendshipId?: Long }`
- 통화 종료 후 프론트에서 친구 신청 버튼 초기 상태 세팅에 사용
- isBlocked 여부도 함께 반환 고려: `{ isFriend, friendshipId, isBlocked }`

---

## 2. 영향 범위

### 신규 도메인
- `friend/domain/Block.java` — blocker(User), blocked(User), createdAt

### 신규 Repository
- `friend/repository/BlockRepository.java`
  - `existsByBlockerIdAndBlockedId(Long, Long)`
  - `findByBlockerIdAndBlockedId(Long, Long)`
  - `existsBlockBetween(Long, Long)` — 양방향 차단 확인

### 신규 DTO
- `friend/dto/FriendCheckResponse.java` — `{ isFriend, friendshipId, isBlocked }`

### 기존 수정
- `FriendService` — deleteFriend(), blockFriend(), unblockFriend(), checkFriend()
- `FriendController` — 신규 엔드포인트 4개 추가
- `FriendRequestRepository` — 차단 여부 확인 쿼리 추가 (친구 신청 시 차단 체크)
- `MatchQueueService` / `MatchService` — 매칭 시 차단 여부 필터링

### ErrorCode 추가
- `FRIENDSHIP_NOT_FOUND` (404)
- `ALREADY_BLOCKED` (409)
- `BLOCK_NOT_FOUND` (404)

---

## 3. API 명세

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| DELETE | `/friends/{friendshipId}` | 친구 삭제 → 204 | O |
| POST | `/friends/{friendshipId}/block` | 친구 차단 → 200 | O |
| DELETE | `/users/me/blocks/{blockedUserId}` | 차단 해제 → 204 | O |
| GET | `/friends/check?userId={userId}` | 친구/차단 여부 확인 → 200 | O |

---

## 4. 구현 순서

1. `Block` 도메인 + `BlockRepository`
2. `ErrorCode` 추가
3. `FriendCheckResponse` DTO
4. `FriendService` 메서드 추가
5. `FriendController` 엔드포인트 추가
6. `FriendRequestService` 차단 체크 적용
7. `MatchQueueService` / `MatchService` 차단 필터링

---

## 5. 제약 조건

- 차단 해제 API는 friendshipId가 아닌 blockedUserId 기준 (`friendshipId`는 삭제된 상태)
- 차단 관계는 단방향 (A가 B를 차단해도 B는 A를 차단한 게 아님)
- 매칭 제외는 양방향 (A→B 또는 B→A 차단이면 둘 다 매칭 안 됨)
