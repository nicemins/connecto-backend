# Design: chat-read

> Feature: 채팅 읽음 처리 및 미읽음 카운트
> 작성일: 2026-03-31
> 선택 설계안: Option C — Pragmatic Balance
> Plan 문서: `docs/01-plan/features/chat-read.plan.md`

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

## 1. 설계 결정

**선택: Option C — Pragmatic Balance**

- `ChatRoomMember` 신규 엔티티로 DB 관심사 분리
- `ChatService` 확장 (별도 서비스 없음) — `markAsRead()`, `getUnreadCount()` 추가
- `ChatController` 확장 — `PATCH /read`, `GET /unread` 엔드포인트 추가
- `ChatSocketHandler` 확장 — `chat:read` 이벤트 핸들러 추가
- `ChatRoomResponse` — `unreadCount` 필드 추가

---

## 2. 데이터 모델

### 2.1 신규 엔티티: ChatRoomMember

```java
@Entity
@Table(
    name = "chat_room_members",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_room_member",
        columnNames = {"chat_room_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_chat_room_member_room_user", columnList = "chat_room_id, user_id"),
        @Index(name = "idx_chat_room_member_user", columnList = "user_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private Long lastReadMessageId;  // null = 한 번도 읽지 않음

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ChatRoomMember(ChatRoom room, User user) {
        this.room = room;
        this.user = user;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateLastRead(Long messageId) {
        if (this.lastReadMessageId == null || messageId > this.lastReadMessageId) {
            this.lastReadMessageId = messageId;
            this.updatedAt = LocalDateTime.now();
        }
    }
}
```

### 2.2 ChatRoomResponse DTO 변경

```java
// 기존
public record ChatRoomResponse(
    Long roomId, Long friendId, String friendNickname,
    String friendProfileImageUrl, String lastMessage, LocalDateTime updatedAt
) {}

// 변경 후 — unreadCount 추가
public record ChatRoomResponse(
    Long roomId, Long friendId, String friendNickname,
    String friendProfileImageUrl, String lastMessage,
    int unreadCount,          // 신규
    LocalDateTime updatedAt
) {}
```

---

## 3. Repository

### 3.1 ChatRoomMemberRepository (신규)

```java
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    // getRooms() 최적화: 내 전체 룸의 lastReadMessageId 일괄 조회
    @Query("SELECT m FROM ChatRoomMember m WHERE m.user.id = :userId AND m.room.id IN :roomIds")
    List<ChatRoomMember> findByUserIdAndRoomIdIn(
        @Param("userId") Long userId,
        @Param("roomIds") Collection<Long> roomIds
    );
}
```

### 3.2 ChatMessageRepository 추가 쿼리

```java
// unreadCount 계산: 내 lastReadMessageId 이후 상대방 메시지 수
@Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.room.id = :roomId " +
       "AND m.sender.id != :myUserId AND m.id > :lastReadMessageId")
int countUnread(
    @Param("roomId") Long roomId,
    @Param("myUserId") Long myUserId,
    @Param("lastReadMessageId") Long lastReadMessageId
);

// lastReadMessageId = null일 때 (한 번도 읽지 않음)
@Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.room.id = :roomId " +
       "AND m.sender.id != :myUserId")
int countAllUnread(@Param("roomId") Long roomId, @Param("myUserId") Long myUserId);
```

---

## 4. API 명세

### 4.1 PATCH /chat/rooms/{roomId}/read

읽음 처리 (lastReadMessageId 업데이트)

```
PATCH /chat/rooms/{roomId}/read
Authorization: Bearer <token>

Request Body:
{
    "lastMessageId": 42
}

Response 200:
{
    "success": true,
    "data": { "unreadCount": 0 }
}

Error:
403 — 채팅방 멤버 아님
404 — 채팅방 없음
```

### 4.2 GET /chat/rooms/{roomId}/unread

특정 룸의 미읽음 카운트 조회

```
GET /chat/rooms/{roomId}/unread
Authorization: Bearer <token>

Response 200:
{
    "success": true,
    "data": { "unreadCount": 3 }
}
```

### 4.3 GET /chat/rooms (기존 — 변경)

`ChatRoomResponse`에 `unreadCount` 필드 추가:

```json
{
    "success": true,
    "data": [
        {
            "roomId": 1,
            "friendId": 2,
            "friendNickname": "홍길동",
            "friendProfileImageUrl": "https://...",
            "lastMessage": "안녕하세요",
            "unreadCount": 3,
            "updatedAt": "2026-03-31T10:00:00"
        }
    ]
}
```

---

## 5. 소켓 이벤트

### 5.1 chat:read (on — 클라이언트 → 서버)

클라이언트가 명시적으로 읽음 처리 요청

```
이벤트: chat:read
페이로드: { roomId: Long }
```

처리 순서:
1. JWT 인증
2. 해당 룸의 최신 메시지 ID 조회
3. ChatRoomMember.updateLastRead() 호출
4. 상대방에게 chat:read emit

### 5.2 chat:read (emit — 서버 → 상대방)

읽음 처리 완료 알림

```
이벤트: chat:read
대상: 상대방 클라이언트 (emitToUser)
페이로드: {
    roomId: Long,
    readerId: Long,
    lastReadMessageId: Long
}
```

### 5.3 자동 읽음 처리 트리거

| 상황 | 동작 |
|------|------|
| `chat:join` 이벤트 | 룸 입장 시 최신 messageId로 자동 읽음 처리 + 상대방에게 chat:read emit |
| `GET /chat/rooms/{roomId}/messages` | 메시지 조회 시 자동 읽음 처리 + 상대방에게 chat:read emit |

---

## 6. ChatService 변경 상세

### 6.1 신규 메서드

```java
// 읽음 처리 (REST + 소켓 공통 사용)
// 반환: { unreadCount, otherUserId } — 소켓 emit용
public record ReadResult(int unreadCount, Long otherUserId, Long lastReadMessageId) {}

@Transactional
public ReadResult markAsRead(Long roomId, Long userId, Long lastMessageId) {
    // 1. 룸 멤버 검증
    // 2. ChatRoomMember upsert (없으면 생성, 있으면 update)
    // 3. unreadCount 계산 후 반환
}

// unreadCount 단순 조회 (GET /unread)
@Transactional(readOnly = true)
public int getUnreadCount(Long roomId, Long userId) { ... }
```

### 6.2 getRooms() 변경

```java
// 기존: lastMessage만 일괄 조회
// 변경: lastMessage + 내 lastReadMessageId + unreadCount 일괄 계산

@Transactional(readOnly = true)
public List<ChatRoomResponse> getRooms(Long userId) {
    // 1. 룸 목록 조회 (기존)
    // 2. ChatRoomMember 일괄 조회 (roomIds + userId)  ← 신규
    // 3. lastMessage 일괄 조회 (기존)
    // 4. unreadCount 계산 포함하여 DTO 변환
}
```

### 6.3 getMessages() 변경

```java
// 기존: 페이징 조회만
// 변경: 페이징 조회 후 자동 읽음 처리 추가
@Transactional
public ChatMessagePageResponse getMessages(Long userId, Long roomId, int page, int size) {
    // ... 기존 로직 ...
    // 마지막 메시지 ID로 자동 읽음 처리 (ChatRoomMember upsert)
    // 반환값에 otherUserId 포함 → Controller에서 소켓 emit
}
```

---

## 7. 엣지 케이스

| 케이스 | 처리 |
|--------|------|
| ChatRoomMember 없는 경우 (첫 방문 전) | unreadCount = 전체 상대방 메시지 수 (countAllUnread) |
| lastMessageId < 현재 lastReadMessageId | updateLastRead() 내부에서 무시 (MAX 유지) |
| 동시 읽음 처리 | updateLastRead()의 조건(`id > this.lastReadMessageId`) 으로 자동 처리 |
| 본인 메시지 unreadCount 포함 안 함 | `WHERE sender_id != myUserId` 조건 |
| 소켓 없이 REST로만 읽음 처리 | markAsRead() 결과를 Controller에서 소켓 emit (matchSocketHandler null 체크) |

---

## 8. 구현 파일 목록

### 신규 생성 (2개)

| 파일 | 내용 |
|------|------|
| `chat/domain/ChatRoomMember.java` | 엔티티 |
| `chat/repository/ChatRoomMemberRepository.java` | JPA Repository |

### 수정 (5개)

| 파일 | 변경 내용 |
|------|-----------|
| `chat/dto/ChatRoomResponse.java` | `unreadCount` 필드 추가 |
| `chat/service/ChatService.java` | `markAsRead()`, `getUnreadCount()`, `getRooms()` 변경, `getMessages()` 변경 |
| `chat/controller/ChatController.java` | `PATCH /read`, `GET /unread` 엔드포인트 추가, `getMessages()` 소켓 emit 처리 |
| `chat/repository/ChatMessageRepository.java` | `countUnread()`, `countAllUnread()` 쿼리 추가 |
| `chat/handler/ChatSocketHandler.java` | `chat:read` 핸들러 추가, `chat:join` 자동 읽음 처리 추가 |

---

## 9. 구현 순서 (Do 단계 권장)

```
Step 1. ChatRoomMember 엔티티 + Repository 생성
Step 2. ChatMessageRepository 쿼리 추가
Step 3. ChatRoomResponse DTO unreadCount 추가
Step 4. ChatService.markAsRead() + getUnreadCount() 구현
Step 5. ChatService.getRooms() unreadCount 포함 변경
Step 6. ChatService.getMessages() 자동 읽음 처리 추가
Step 7. ChatController PATCH /read + GET /unread 추가
Step 8. ChatSocketHandler chat:read 핸들러 + chat:join 자동 읽음 처리
```

---

## 10. 프론트엔드 인터페이스 명세

### 10.1 채팅방 목록 타입 변경

```typescript
// 기존
interface ChatRoom {
    roomId: number;
    friendId: number;
    friendNickname: string | null;
    friendProfileImageUrl: string | null;
    lastMessage: string | null;
    updatedAt: string;
}

// 변경 후
interface ChatRoom {
    roomId: number;
    friendId: number;
    friendNickname: string | null;
    friendProfileImageUrl: string | null;
    lastMessage: string | null;
    unreadCount: number;   // 신규 — 0이면 읽음 완료
    updatedAt: string;
}
```

### 10.2 읽음 처리 호출 시점

```
채팅방 진입 시 (권장 순서):
  1. socket.emit("chat:join", { roomId })
     → 서버가 자동 읽음 처리 + 상대방에게 chat:read emit
  2. GET /chat/rooms/{roomId}/messages
     → 서버가 자동 읽음 처리 (중복 처리 무방 — MAX 유지)

채팅 중 새 메시지 수신 시:
  - 채팅방이 포커스(foreground) 상태 → socket.emit("chat:read", { roomId })
  - 백그라운드 상태 → 로컬에서 unreadCount++ (서버와 동기화 안 됨, 재진입 시 서버값으로 리셋)

채팅방 나갈 때 (선택):
  - PATCH /chat/rooms/{roomId}/read, body: { lastMessageId } 전송
```

### 10.3 상대방 읽음 표시 수신

```typescript
// chat:read 이벤트 수신
socket.on("chat:read", (data: {
    roomId: number;
    readerId: number;
    lastReadMessageId: number;
}) => {
    // data.lastReadMessageId 이하 메시지에 읽음 표시 (✓✓)
    // 예: messages.filter(m => m.id <= data.lastReadMessageId) → isRead = true
    updateReadStatus(data.roomId, data.lastReadMessageId);
});
```

### 10.4 REST 읽음 처리 API (명시적 호출)

```typescript
// PATCH /chat/rooms/{roomId}/read
const markAsRead = async (roomId: number, lastMessageId: number) => {
    const res = await fetch(`/chat/rooms/${roomId}/read`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${token}` },
        body: JSON.stringify({ lastMessageId })
    });
    // response: { success: true, data: { unreadCount: 0 } }
};
```

### 10.5 프론트 구현 우선순위

| 우선순위 | 항목 |
|----------|------|
| P0 (필수) | `ChatRoom.unreadCount` 렌더링 (채팅방 목록 뱃지) |
| P1 (권장) | `chat:read` 수신 → 상대방 읽음 표시 (✓✓) |
| P1 (권장) | 채팅방 진입 시 `socket.emit("chat:join")` 연동 (서버 자동 처리) |
| P2 (선택) | 포커스 상태에서 새 메시지 수신 시 즉시 `socket.emit("chat:read")` |
| P2 (선택) | `PATCH /read` 명시적 호출 (채팅방 나갈 때) |

---

## 11. 비고

- `ddl-auto: update` 설정이므로 서버 재시작 시 `chat_room_members` 테이블 자동 생성
- 기존 채팅방의 ChatRoomMember는 첫 `chat:join` 또는 `getMessages()` 호출 시 자동 생성됨
- unreadCount가 없던 기존 클라이언트와의 호환: `unreadCount` 필드는 추가이므로 기존 파싱 로직에 영향 없음
