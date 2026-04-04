# Plan: chat-read

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | chat-read |
| 작성일 | 2026-03-26 |
| 상태 | Plan |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 채팅방 목록에서 읽지 않은 메시지 수를 알 수 없어 메시지 확인 여부 파악이 불가능 |
| Solution | 룸별 마지막 읽은 메시지 ID 추적으로 읽음 상태 관리 (REST + Socket.IO) |
| Function UX Effect | 채팅방 목록에 unreadCount 표시, 상대방 읽음 확인 시 체크 표시로 소통 완결감 향상 |
| Core Value | 메시지가 읽혔는지 알 수 있는 신뢰감 있는 채팅 경험 |

---

## 1. 기능 정의

### 1.1 읽음 처리 방식

**last_read_message_id 방식** (카카오톡/WhatsApp 표준 패턴)

- `chat_room_members` 테이블 신규 생성 (ChatRoomMember 엔티티)
- 각 유저별, 룸별 `lastReadMessageId` 저장
- unreadCount = 내 lastReadMessageId 이후 상대방이 보낸 메시지 수

**선택 이유:**
- per-message 읽음 테이블보다 N배 경량
- 1:1 채팅에 최적 (그룹 채팅은 per-message 방식이 유리)
- 쿼리 단순: `COUNT WHERE id > lastReadMessageId AND senderId != me`

### 1.2 읽음 처리 트리거

| 상황 | 동작 |
|------|------|
| 소켓 `chat:join` 이벤트 | 해당 룸 최신 messageId로 lastReadMessageId 업데이트 |
| REST `GET /chat/rooms/{roomId}/messages` 호출 | 해당 룸 최신 messageId로 lastReadMessageId 업데이트 |
| `chat:read` 소켓 이벤트 (신규) | 클라이언트가 명시적으로 읽음 처리 요청 |

### 1.3 상대방 알림

- 읽음 처리 시 상대방에게 `chat:read` 소켓 이벤트 emit
  - payload: `{ roomId, readerId, lastReadMessageId }`
- 상대방 클라이언트는 해당 메시지까지 체크 표시 업데이트

---

## 2. API 명세

### 2.1 신규 REST API

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| PATCH | `/chat/rooms/{roomId}/read` | 읽음 처리 (lastReadMessageId 업데이트) | O |
| GET | `/chat/rooms/{roomId}/unread` | 특정 룸의 미읽음 카운트 조회 | O |

**PATCH /chat/rooms/{roomId}/read**
- Request Body: `{ "lastMessageId": Long }` (클라이언트가 본 마지막 메시지 ID)
- Response: `{ "unreadCount": 0 }`

### 2.2 기존 API 변경

**GET /chat/rooms** 응답에 `unreadCount` 필드 추가:
```json
{
  "roomId": 1,
  "friendId": 2,
  "friendNickname": "홍길동",
  "lastMessage": "안녕하세요",
  "unreadCount": 3,
  "updatedAt": "..."
}
```

**GET /chat/rooms/{roomId}/messages** 호출 시 자동 읽음 처리 (lastReadMessageId 업데이트)

### 2.3 소켓 이벤트

| 방향 | 이벤트 | 페이로드 | 설명 |
|------|--------|----------|------|
| on | `chat:read` | `{ roomId: Long }` | 클라이언트 읽음 처리 요청 |
| emit → 상대방 | `chat:read` | `{ roomId, readerId, lastReadMessageId }` | 읽음 처리 알림 |

---

## 3. 데이터 모델 변경

### 3.1 신규 엔티티: ChatRoomMember

```java
@Entity
@Table(
  name = "chat_room_members",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_chat_room_member",
    columnNames = {"chat_room_id", "user_id"}
  )
)
public class ChatRoomMember {
    Long id
    ChatRoom room          // @ManyToOne LAZY
    User user              // @ManyToOne LAZY
    Long lastReadMessageId // nullable (한 번도 읽지 않은 경우)
    LocalDateTime updatedAt
}
```

### 3.2 인덱스

- `idx_chat_room_member_room_user (chat_room_id, user_id)` — 조회 최적화
- `idx_chat_room_member_user (user_id)` — 내 전체 룸 unreadCount 조회

### 3.3 ChatRoomResponse DTO 변경

```java
public record ChatRoomResponse(
    Long roomId,
    Long friendId,
    String friendNickname,
    String friendProfileImageUrl,
    String lastMessage,
    int unreadCount,          // 신규 필드
    LocalDateTime updatedAt
) {}
```

---

## 4. 구현 범위

### 4.1 백엔드

| 파일 | 변경 유형 | 내용 |
|------|-----------|------|
| `chat/domain/ChatRoomMember.java` | 신규 | 엔티티 |
| `chat/repository/ChatRoomMemberRepository.java` | 신규 | findByRoomIdAndUserId, findByUserIdAndRoomIdIn |
| `chat/service/ChatService.java` | 수정 | markAsRead(), getUnreadCount(), getRooms()에 unreadCount 포함 |
| `chat/controller/ChatController.java` | 수정 | PATCH /read 엔드포인트 추가 |
| `chat/dto/ChatRoomResponse.java` | 수정 | unreadCount 필드 추가 |
| `chat/handler/ChatSocketHandler.java` | 수정 | chat:read on 핸들러 추가, getMessages/chat:join 시 자동 읽음 처리 |

### 4.2 unreadCount 계산 쿼리

```sql
-- 특정 룸의 나의 미읽음 수
SELECT COUNT(*) FROM chat_messages
WHERE room_id = :roomId
  AND sender_id != :myUserId
  AND id > COALESCE(:lastReadMessageId, 0)
```

### 4.3 getRooms() 최적화

현재 getRooms()는 lastMessage를 별도 쿼리로 조회. unreadCount 추가 시:
1. ChatRoomMember 일괄 조회 (내 lastReadMessageId 가져오기)
2. 룸별 unreadCount 일괄 계산 (쿼리 1회)

---

## 5. 엣지 케이스

| 케이스 | 처리 방법 |
|--------|-----------|
| ChatRoomMember 없는 경우 (첫 방문 전) | lastReadMessageId = null → unreadCount = 전체 상대방 메시지 수 |
| 내가 보낸 메시지는 unreadCount에 포함 안 함 | WHERE sender_id != myUserId |
| 읽음 처리 시 lastReadMessageId < 현재 값 | 업데이트 무시 (이전 읽음으로 되돌리기 불가) |
| 동시 읽음 처리 | lastReadMessageId는 MAX 값으로 유지 (업데이트 시 > 조건 추가) |

---

## 6. 프론트엔드 인터페이스 변경

### 6.1 채팅방 목록 (변경)

```typescript
interface ChatRoom {
  roomId: number;
  friendId: number;
  friendNickname: string | null;
  friendProfileImageUrl: string | null;
  lastMessage: string | null;
  unreadCount: number;  // 신규
  updatedAt: string;
}
```

### 6.2 읽음 처리 호출 시점

```
채팅방 진입 시:
  1. socket.emit("chat:join", { roomId })  → 서버 자동 읽음 처리
  2. GET /chat/rooms/{roomId}/messages     → 서버 자동 읽음 처리

채팅 중 새 메시지 수신 시:
  - 채팅방이 포커스 상태면 socket.emit("chat:read", { roomId })
  - 백그라운드면 unreadCount 로컬 증가

채팅방 나갈 때:
  - PATCH /chat/rooms/{roomId}/read (lastMessageId 전송) 또는 소켓 이벤트
```

### 6.3 상대방 읽음 표시

```
socket.on("chat:read", ({ roomId, readerId, lastReadMessageId }) => {
  // lastReadMessageId 이하 메시지에 읽음 체크 표시
  updateReadStatus(roomId, lastReadMessageId);
});
```

---

## 7. 구현 우선순위

| 우선순위 | 항목 |
|----------|------|
| P0 (필수) | ChatRoomMember 엔티티 + GET /chat/rooms unreadCount |
| P1 (권장) | PATCH /chat/rooms/{roomId}/read REST API |
| P1 (권장) | chat:read 소켓 이벤트 (읽음 알림) |
| P2 (선택) | chat:join 시 자동 읽음 처리 (이미 구현된 join 활용) |
| P2 (선택) | getMessages() 호출 시 자동 읽음 처리 |

---

## 8. 미구현 시 영향

읽음 처리 없이도 앱 동작에는 문제 없음. 다만:
- 채팅방 목록에 unreadCount = 0 고정 표시
- 상대방이 내 메시지를 읽었는지 확인 불가
- 알림 뱃지 수 계산 불가

→ MVP 이후 구현 가능 (P1 단계)
