# Report: chat

> Feature: 1:1 채팅 (REST + Socket.IO)
> 기간: 2026-03-18
> Match Rate: 98%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | chat |
| 시작일 | 2026-03-18 |
| 완료일 | 2026-03-18 |
| Match Rate | **98%** |
| 구현 파일 | 신규 11개 + 수정 2개 |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 통화 이후 친구와 지속 소통할 수단이 없어 관계가 단절됨 |
| Solution | 친구 사이에만 채팅방 생성 가능한 1:1 채팅 (REST + Socket.IO, 차단 체크 포함) |
| Function UX Effect | 통화 후 자연스럽게 채팅으로 이어지는 경험, 메시지 히스토리 페이징 제공 |
| Core Value | 5분 익명 통화에서 시작된 인연을 채팅으로 이어갈 수 있는 지속적 소통 채널 |

---

## 1. 구현 내용

### 신규 생성 (11개)

| 파일 | 내용 |
|------|------|
| `chat/domain/ChatRoom.java` | 1:1 채팅방 엔티티 (UK, 인덱스, isMember/getOtherUser) |
| `chat/domain/ChatMessage.java` | 메시지 엔티티 (1000자 제한, 복합 인덱스) |
| `chat/repository/ChatRoomRepository.java` | findBetween, findAllByUserId 쿼리 |
| `chat/repository/ChatMessageRepository.java` | 페이징 조회, lastMessage 조회 |
| `chat/dto/ChatRoomCreateRequest.java` | friendId 요청 DTO |
| `chat/dto/ChatRoomResponse.java` | 채팅방 목록 응답 |
| `chat/dto/ChatMessageResponse.java` | 메시지 응답 |
| `chat/dto/ChatMessagePageResponse.java` | 페이징 응답 |
| `chat/service/ChatService.java` | createOrGetRoom, getRooms, getMessages, saveMessage |
| `chat/controller/ChatController.java` | POST/GET 엔드포인트 |
| `chat/handler/ChatSocketHandler.java` | chat:send/receive/join/leave/typing/read 이벤트 |

### 수정 (2개)

| 파일 | 변경 내용 |
|------|-----------|
| `common/response/ErrorCode.java` | CHAT_ROOM_NOT_FOUND, MESSAGE_BLOCKED 추가 |

---

## 2. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| `@PostConstruct` 자체 등록 | SocketIOConfig 수정 대신 ChatSocketHandler가 직접 이벤트 등록 | 더 독립적인 구조 |
| `SavedMessage` record | `saveMessage()` 반환 타입에 otherUserId 포함 | LazyInitializationException 버그 예방 |
| `Map<String, Object>` 파싱 | `ChatSendData` DTO 대신 Map 사용 | netty-socketio 호환성 향상 |
| `@ConditionalOnProperty` | Redis 없는 환경에서 ChatSocketHandler 비활성화 | 로컬 개발 정상 동작 |

---

## 3. 주요 버그 수정 (2026-03-18)

**LazyInitializationException — `chat:receive` 미수신 버그**
- 원인: `@Transactional` 종료 후 `msg.getRoom().getOtherUser()` 호출 → LAZY 프록시 → 예외
- 수정: `ChatService.saveMessage()` 반환을 `SavedMessage(message, otherUserId)`로 변경 → 트랜잭션 내 미리 계산

---

## 4. Success Criteria 최종

| 기준 | 상태 |
|------|------|
| POST /chat/rooms — 친구만 생성, 기존 방 중복 반환 | ✅ Met |
| GET /chat/rooms — 최신순 목록 | ✅ Met |
| GET /messages — 페이징 (최신순) | ✅ Met |
| chat:send → chat:receive 양측 emit | ✅ Met |
| 차단 시 MESSAGE_BLOCKED (403) | ✅ Met |
| 채팅방 멤버 아닌 경우 403 | ✅ Met |

**6/6 (100%)**

---

## 5. Gap 및 잔여 사항

| 심각도 | 항목 | 내용 |
|--------|------|------|
| ℹ️ Minor | `NOT_CHAT_MEMBER` ErrorCode | 설계에 명시되었으나 `ACCESS_DENIED` 재사용. 기능 정확성 무관 |

---

## 6. Match Rate

| Category | Score |
|----------|:-----:|
| Structural | 100% |
| Functional | 97% |
| API Contract | 100% |
| **Overall** | **98%** |
