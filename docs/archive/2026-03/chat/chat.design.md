# Design: chat

> Plan 참조: `docs/01-plan/features/chat.plan.md`
> 작성일: 2026-03-18

---

## 1. 신규 파일 목록

| 파일 | 역할 |
|------|------|
| `chat/domain/ChatRoom.java` | 채팅방 엔티티 |
| `chat/domain/ChatMessage.java` | 채팅 메시지 엔티티 |
| `chat/repository/ChatRoomRepository.java` | 채팅방 조회 쿼리 |
| `chat/repository/ChatMessageRepository.java` | 메시지 페이징 쿼리 |
| `chat/dto/ChatRoomCreateRequest.java` | 채팅방 생성 요청 DTO |
| `chat/dto/ChatRoomResponse.java` | 채팅방 목록 응답 DTO |
| `chat/dto/ChatMessageResponse.java` | 메시지 단건 응답 DTO |
| `chat/dto/ChatMessagePageResponse.java` | 메시지 페이징 응답 DTO |
| `chat/service/ChatService.java` | 채팅 비즈니스 로직 |
| `chat/controller/ChatController.java` | REST API 엔드포인트 |
| `chat/handler/ChatSocketHandler.java` | Socket.IO chat:send/receive 처리 |

---

## 2. 수정 파일 목록

| 파일 | 수정 내용 |
|------|----------|
| `match/config/SocketIOConfig.java` | ChatSocketHandler 등록 (`addListeners`) |
| `common/response/ErrorCode.java` | CHAT_ROOM_NOT_FOUND, NOT_CHAT_MEMBER, MESSAGE_BLOCKED 추가 |

---

## 3. 도메인 설계

### ChatRoom.java
```java
@Entity
@Table(name = "chat_rooms",
    uniqueConstraints = @UniqueConstraint(name = "uk_chat_room", columnNames = {"user1_id", "user2_id"}),
    indexes = {
        @Index(name = "idx_chat_room_user1", columnList = "user1_id"),
        @Index(name = "idx_chat_room_user2", columnList = "user2_id")
    }
)
public class ChatRoom {
    @Id @GeneratedValue(strategy = IDENTITY)
    Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    User user1;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    User user2;

    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt;

    // 마지막 메시지 시각 (목록 정렬용)
    LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    // 멤버 여부 확인
    public boolean isMember(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    // 상대방 조회
    public User getOtherUser(Long userId) {
        if (user1.getId().equals(userId)) return user2;
        if (user2.getId().equals(userId)) return user1;
        throw new IllegalArgumentException("User is not a member of this chat room");
    }
}
```

### ChatMessage.java
```java
@Entity
@Table(name = "chat_messages",
    indexes = {
        @Index(name = "idx_chat_message_room", columnList = "room_id, created_at DESC")
    }
)
public class ChatMessage {
    @Id @GeneratedValue(strategy = IDENTITY)
    Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    ChatRoom room;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    User sender;

    @Column(nullable = false, length = 1000)
    String content;

    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
```

---

## 4. Repository 설계

### ChatRoomRepository.java
```java
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 두 사용자 간 채팅방 조회 (양방향)
    @Query("SELECT r FROM ChatRoom r WHERE " +
           "(r.user1.id = :userId1 AND r.user2.id = :userId2) OR " +
           "(r.user1.id = :userId2 AND r.user2.id = :userId1)")
    Optional<ChatRoom> findBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // 내 채팅방 목록 (최신순)
    @Query("SELECT r FROM ChatRoom r WHERE r.user1.id = :userId OR r.user2.id = :userId " +
           "ORDER BY r.updatedAt DESC")
    List<ChatRoom> findAllByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);
}
```

### ChatMessageRepository.java
```java
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 메시지 히스토리 페이징 (최신 → 과거순)
    Page<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    // 마지막 메시지 조회 (채팅방 목록용)
    Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(Long roomId);
}
```

---

## 5. DTO 설계

### ChatRoomCreateRequest.java
```java
public record ChatRoomCreateRequest(
    @NotNull Long friendId
) {}
```

### ChatRoomResponse.java
```java
public record ChatRoomResponse(
    Long roomId,
    Long friendId,
    String friendNickname,
    String friendProfileImageUrl,
    String lastMessage,       // null if no messages
    LocalDateTime updatedAt
) {}
```

### ChatMessageResponse.java
```java
public record ChatMessageResponse(
    Long id,
    Long senderId,
    String content,
    LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage msg) { ... }
}
```

### ChatMessagePageResponse.java
```java
public record ChatMessagePageResponse(
    List<ChatMessageResponse> messages,
    boolean hasNext,
    int page,
    int size
) {}
```

---

## 6. Service 설계

### ChatService.java
```java
@Service
public class ChatService {

    // 채팅방 생성 (이미 있으면 기존 반환)
    @Transactional
    public ChatRoomResponse createOrGetRoom(Long userId, Long friendId) {
        // 1. 친구 여부 확인
        if (!friendshipRepository.existsBetween(userId, friendId)) {
            throw new ForbiddenException(ACCESS_DENIED);
        }
        // 2. 기존 채팅방 조회
        Optional<ChatRoom> existing = chatRoomRepository.findBetween(userId, friendId);
        if (existing.isPresent()) {
            return toResponse(existing.get(), userId);
        }
        // 3. 신규 생성
        User user1 = userRepository.getReferenceById(userId);
        User user2 = userRepository.getReferenceById(friendId);
        ChatRoom room = chatRoomRepository.save(new ChatRoom(user1, user2));
        return toResponse(room, userId);
    }

    // 내 채팅방 목록
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getRooms(Long userId) {
        return chatRoomRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
            .stream().map(r -> toResponse(r, userId)).toList();
    }

    // 메시지 히스토리 (페이징)
    @Transactional(readOnly = true)
    public ChatMessagePageResponse getMessages(Long userId, Long roomId, int page, int size) {
        ChatRoom room = chatRoomRepository.findById(roomId)
            .orElseThrow(() -> new ResourceNotFoundException(CHAT_ROOM_NOT_FOUND));
        if (!room.isMember(userId)) {
            throw new ForbiddenException(ACCESS_DENIED);
        }
        size = Math.min(size, 100); // 최대 100
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> msgPage = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
        return new ChatMessagePageResponse(
            msgPage.getContent().stream().map(ChatMessageResponse::from).toList(),
            msgPage.hasNext(), page, size
        );
    }

    // 메시지 저장 (소켓 핸들러에서 호출)
    @Transactional
    public ChatMessage saveMessage(Long roomId, Long senderId, String content) {
        ChatRoom room = chatRoomRepository.findById(roomId)
            .orElseThrow(() -> new ResourceNotFoundException(CHAT_ROOM_NOT_FOUND));
        if (!room.isMember(senderId)) {
            throw new ForbiddenException(ACCESS_DENIED);
        }
        // 차단 여부 확인
        Long otherUserId = room.getOtherUser(senderId).getId();
        if (blockRepository.existsBlockBetween(senderId, otherUserId)) {
            throw new ForbiddenException(MESSAGE_BLOCKED);
        }
        User sender = userRepository.getReferenceById(senderId);
        ChatMessage msg = new ChatMessage(room, sender, content);
        chatMessageRepository.save(msg);
        // updatedAt 갱신
        room.setUpdatedAt(msg.getCreatedAt());
        return msg;
    }
}
```

---

## 7. Controller 설계

### ChatController.java
```java
@Tag(name = "채팅", description = "1:1 채팅 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/chat")
public class ChatController {

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatRoomResponse> createRoom(
        @Valid @RequestBody ChatRoomCreateRequest request) {
        return ApiResponse.success(
            chatService.createOrGetRoom(userContext.getUserId(), request.friendId()));
    }

    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomResponse>> getRooms() {
        return ApiResponse.success(chatService.getRooms(userContext.getUserId()));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<ChatMessagePageResponse> getMessages(
        @PathVariable Long roomId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(
            chatService.getMessages(userContext.getUserId(), roomId, page, size));
    }
}
```

---

## 8. Socket Handler 설계

### ChatSocketHandler.java
```java
@Component
public class ChatSocketHandler {

    private final SocketIOServer server;
    private final ChatService chatService;
    private final MatchSocketHandler matchSocketHandler; // emitToUser() 재사용

    // chat:send 이벤트 처리
    // payload: { roomId: Long, content: String }
    public void onChatSend(SocketIOClient client, ChatSendData data, AckRequest ack) {
        Long senderId = extractUserId(client); // JWT 검증
        try {
            ChatMessage msg = chatService.saveMessage(data.roomId(), senderId, data.content());
            ChatMessageResponse response = ChatMessageResponse.from(msg);

            // 채팅방 상대방에게 emit
            ChatRoom room = /* from msg */;
            Long otherUserId = room.getOtherUser(senderId).getId();
            Map<String, Object> payload = Map.of("roomId", data.roomId(), "message", response);

            matchSocketHandler.emitToUser(senderId, "chat:receive", payload);   // 본인
            matchSocketHandler.emitToUser(otherUserId, "chat:receive", payload); // 상대방
        } catch (Exception e) {
            client.sendEvent("chat:error", Map.of("message", e.getMessage()));
        }
    }
}
```

### SocketIOConfig 수정
```java
// 기존 MatchSocketHandler 등록 이후에 추가
@Autowired ChatSocketHandler chatSocketHandler;

server.addEventListener("chat:send", ChatSendData.class, chatSocketHandler::onChatSend);
```

---

## 9. Socket.IO 이벤트 명세

| 방향 | 이벤트 | payload |
|------|--------|---------|
| client → server | `chat:send` | `{ roomId: Long, content: String }` |
| server → client | `chat:receive` | `{ roomId: Long, message: { id, senderId, content, createdAt } }` |
| server → client | `chat:error` | `{ message: String }` |

---

## 10. ErrorCode 추가

```java
// 404 Not Found (Chat)
CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."),

// 403 Forbidden
MESSAGE_BLOCKED(HttpStatus.FORBIDDEN, "MESSAGE_BLOCKED", "차단된 사용자에게는 메시지를 보낼 수 없습니다."),
```

---

## 11. 구현 순서

1. `ErrorCode` 추가 (CHAT_ROOM_NOT_FOUND, MESSAGE_BLOCKED)
2. `ChatRoom`, `ChatMessage` 도메인
3. `ChatRoomRepository`, `ChatMessageRepository`
4. DTO 클래스 4개
5. `ChatService` (createOrGetRoom, getRooms, getMessages, saveMessage)
6. `ChatController`
7. `ChatSocketHandler` (chat:send 처리)
8. `SocketIOConfig`에 ChatSocketHandler 등록
