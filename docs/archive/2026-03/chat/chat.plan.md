# Plan: chat

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | chat |
| 작성일 | 2026-03-18 |
| 상태 | Plan |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 통화 이후 친구와 지속적으로 소통할 수단이 없어 관계가 단절됨 |
| Solution | 친구 사이에만 채팅방 생성 가능한 1:1 채팅 기능 (REST + Socket.IO) |
| Function UX Effect | 통화 후 자연스럽게 채팅으로 이어지는 사용자 경험, 메시지 히스토리 페이징 제공 |
| Core Value | 5분 익명 통화에서 시작된 인연을 채팅으로 이어갈 수 있는 지속적 소통 채널 |

---

## 1. 기능 정의

### 1.1 채팅방 생성
- `POST /chat/rooms` body: `{ friendId: Long }` → 201 `{ roomId, friendId, ... }`
- 친구 사이에만 생성 가능 (비친구 요청 시 403)
- 이미 해당 친구와 채팅방이 존재하면 기존 채팅방 반환 (중복 생성 방지)

### 1.2 채팅방 목록 조회
- `GET /chat/rooms` → `[{ roomId, friendId, friendNickname, lastMessage, unreadCount, updatedAt }]`
- 최근 메시지 기준 내림차순 정렬

### 1.3 메시지 히스토리 조회 (페이징)
- `GET /chat/rooms/{roomId}/messages?page=0&size=50` → `{ messages: [...], hasNext: boolean }`
- 해당 채팅방 멤버만 조회 가능 (아니면 403)
- 최신 메시지 → 과거 순 (createdAt DESC)

### 1.4 메시지 전송 (Socket.IO)
- Client emit: `chat:send` `{ roomId: Long, content: String }`
- Server emit: `chat:receive` `{ roomId, message: { id, senderId, content, createdAt } }`
- 전송자 포함 채팅방 양측 모두에게 emit
- DB 저장 (ChatMessage)

---

## 2. 도메인 설계

### ChatRoom
```java
Long id
User user1          // @ManyToOne
User user2          // @ManyToOne
LocalDateTime createdAt
LocalDateTime updatedAt   // 마지막 메시지 시각 (목록 정렬용)
// unique constraint: (user1_id, user2_id)
```

### ChatMessage
```java
Long id
ChatRoom room       // @ManyToOne
User sender         // @ManyToOne
String content      // max 1000자
LocalDateTime createdAt
// index: idx_chat_message_room_id (room_id, created_at DESC)
```

---

## 3. 패키지 구조

```
chat/
├── controller/ChatController.java
├── domain/ChatRoom.java
├── domain/ChatMessage.java
├── dto/ChatRoomCreateRequest.java
├── dto/ChatRoomResponse.java
├── dto/ChatMessageResponse.java
├── dto/ChatMessagePageResponse.java
├── handler/ChatSocketHandler.java     ← Socket.IO chat:send 처리
├── repository/ChatRoomRepository.java
├── repository/ChatMessageRepository.java
└── service/ChatService.java
```

---

## 4. API 명세

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/chat/rooms` | 채팅방 생성 (친구만) → 201 | O |
| GET | `/chat/rooms` | 내 채팅방 목록 → 200 | O |
| GET | `/chat/rooms/{roomId}/messages` | 메시지 히스토리 (페이징) → 200 | O |

### Socket.IO 이벤트

| 방향 | 이벤트 | payload |
|------|--------|---------|
| client → server | `chat:send` | `{ roomId, content }` |
| server → client | `chat:receive` | `{ roomId, message: { id, senderId, content, createdAt } }` |

---

## 5. 구현 순서

1. `ChatRoom`, `ChatMessage` 도메인
2. `ChatRoomRepository`, `ChatMessageRepository`
3. DTO 클래스들
4. `ChatService` (createRoom, getRooms, getMessages)
5. `ChatController`
6. `ChatSocketHandler` — `chat:send` 이벤트 처리 + `chat:receive` emit
7. SocketIOConfig에 ChatSocketHandler 등록

---

## 6. 제약 조건

- 채팅방은 친구 사이에만 생성 가능
- content 최대 1000자
- 채팅방 멤버만 메시지 조회 가능 (403 처리)
- Socket.IO 인증은 기존 JWT 방식 그대로 (MatchSocketHandler와 동일)
- 메시지 페이지 기본 size=50, 최대 100
- **차단 시 채팅방은 유지**, `chat:send` 시 차단 여부 확인 후 차단 상태면 전송 거부 (400 또는 403)
