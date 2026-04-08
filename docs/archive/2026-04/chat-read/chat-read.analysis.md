# Analysis: chat-read

> Feature: 채팅 읽음 처리 및 미읽음 카운트
> 분석일: 2026-04-04
> Analyzer: Gap Detector (Static Analysis)

---

## Context Anchor

| 항목 | 내용 |
|------|------|
| WHY | 채팅방 목록에서 읽지 않은 메시지 수를 알 수 없어 사용자가 메시지 확인 여부를 파악할 수 없음 |
| WHO | 채팅 기능을 사용하는 모든 Connecto 사용자 |
| RISK | ChatRoomMember 신규 테이블 도입으로 기존 채팅 조회 쿼리 변경 필요 |
| SUCCESS | GET /chat/rooms 응답에 unreadCount 포함, chat:read 소켓 이벤트 정상 동작 |
| SCOPE | 백엔드 전용 (ChatRoomMember 엔티티, REST API 2개, 소켓 이벤트 1개) |

---

## 1. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Structural Match | 100% | ✅ |
| Functional Depth | 97% | ✅ |
| API Contract | 100% | ✅ |
| **Overall** | **99%** | ✅ |

> 분석 방식: Static Only (서버 미실행). Formula = Structural×0.2 + Functional×0.4 + Contract×0.4

---

## 2. Structural Match (100%)

| 파일 | 유형 | 설계 요구 | 구현 상태 |
|------|------|-----------|-----------|
| `chat/domain/ChatRoomMember.java` | 신규 | ✅ 요구 | ✅ 구현 |
| `chat/repository/ChatRoomMemberRepository.java` | 신규 | ✅ 요구 | ✅ 구현 |
| `chat/dto/ChatRoomResponse.java` | 수정 | unreadCount 추가 | ✅ 구현 |
| `chat/service/ChatService.java` | 수정 | markAsRead 등 | ✅ 구현 |
| `chat/controller/ChatController.java` | 수정 | PATCH/GET 추가 | ✅ 구현 |
| `chat/repository/ChatMessageRepository.java` | 수정 | countUnread 쿼리 | ✅ 구현 |
| `chat/handler/ChatSocketHandler.java` | 수정 | chat:read 핸들러 | ✅ 구현 |

---

## 3. Functional Depth (97%)

### 3.1 ChatRoomMember 엔티티

| 항목 | 설계 | 구현 |
|------|------|------|
| `updateLastRead()` MAX 보존 | ✅ | ✅ |
| null 체크 (첫 방문) | ✅ | ✅ |
| `@UniqueConstraint` (room + user) | ✅ | ✅ |
| 인덱스 2개 | ✅ | ✅ |
| `updatedAt` 관리 | 생성자 설정 | `@PrePersist` (동등) |

### 3.2 chat:read 4개 트리거 (CLAUDE.md 명시 규칙)

| 트리거 | 설계 | 구현 |
|--------|------|------|
| `chat:read` 소켓 이벤트 | ✅ | ✅ `onChatRead` |
| `chat:join` 소켓 이벤트 | ✅ | ✅ `onChatJoin` |
| `GET /messages?page=0` | ✅ | ✅ `getMessages()` |
| `PATCH /read` REST | ✅ | ✅ `markAsRead()` |

### 3.3 ChatService 메서드

| 메서드 | 설계 | 구현 |
|--------|------|------|
| `markAsRead(roomId, userId, lastMessageId)` | ✅ | ✅ |
| `getUnreadCount(roomId, userId)` | ✅ | ✅ |
| `getRooms()` unreadCount 포함 | ✅ | ✅ |
| `getMessages()` 자동 읽음 (page=0) | ✅ | ✅ |
| `markAsReadLatest()` | 미명시 | ✅ 추가 (소켓용) |
| `upsertLastRead()` private | 미명시 | ✅ 추가 (공통 로직) |

---

## 4. API Contract (100%)

| API | 설계 | 구현 |
|-----|------|------|
| `PATCH /chat/rooms/{roomId}/read` | ✅ | ✅ |
| `GET /chat/rooms/{roomId}/unread` | ✅ | ✅ |
| `GET /chat/rooms` → unreadCount 포함 | ✅ | ✅ |
| Response `{ "unreadCount": N }` | ✅ | ✅ |
| chat:read emit `{ roomId, readerId, lastReadMessageId }` | ✅ | ✅ |
| 403 멤버 아님 / 404 룸 없음 | ✅ | ✅ |

---

## 5. Gap List

| # | 심각도 | 항목 | 세부 내용 | 권장 조치 |
|---|--------|------|-----------|-----------|
| 1 | ⚠️ Minor | `getRooms()` N+1 쿼리 | 룸당 개별 `countUnread()` 호출. 설계 §4.3에서 "unreadCount 일괄 계산" 언급했으나 배치 쿼리 미구현 | 스케일 시 `@Query`로 배치 쿼리 추가 권장 |

> Critical/Important 갭 없음. 기능 정확성 문제 없음.

---

## 6. Success Criteria 평가

| 기준 | 상태 | 근거 |
|------|------|------|
| GET /chat/rooms 응답에 unreadCount 포함 | ✅ Met | `ChatRoomResponse.unreadCount` + `getRooms()` |
| chat:read 소켓 이벤트 정상 동작 | ✅ Met | `onChatRead`, `onChatJoin`, `getMessages`, `markAsRead` 4곳 emit |
| lastReadMessageId MAX 보존 | ✅ Met | `updateLastRead()` 조건 `messageId > this.lastReadMessageId` |
| 본인 메시지 unreadCount 미포함 | ✅ Met | `WHERE sender_id != myUserId` |
| ChatRoomMember null 케이스 처리 | ✅ Met | `countAllUnread()` fallback |

**Success Criteria: 5/5 (100%)**
