# Report: chat-read

> Feature: 채팅 읽음 처리 및 미읽음 카운트
> 기간: 2026-03-26 ~ 2026-04-04
> Match Rate: 99%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | chat-read |
| 시작일 | 2026-03-26 (Plan) |
| 설계일 | 2026-03-31 (Design) |
| 완료일 | 2026-04-04 (Analysis) |
| Match Rate | **99%** |
| 구현 파일 | 신규 2개 + 수정 5개 |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 채팅방 목록에서 읽지 않은 메시지 수를 알 수 없어 사용자가 메시지 확인 여부를 파악할 수 없었음 |
| Solution | `ChatRoomMember` 엔티티로 룸별 lastReadMessageId 추적, 4개 트리거로 자동 읽음 처리 |
| Function UX Effect | 채팅방 목록 `unreadCount` 뱃지 + 상대방 읽음 시 `chat:read` 이벤트로 체크 표시 가능 |
| Core Value | 메시지가 읽혔는지 알 수 있는 신뢰감 있는 채팅 경험 (카카오톡/WhatsApp 표준 패턴) |

---

## 1. 구현 내용

### 1.1 신규 생성 파일

| 파일 | 내용 |
|------|------|
| `chat/domain/ChatRoomMember.java` | 룸별 사용자 lastReadMessageId 엔티티. `updateLastRead()` MAX 보존 |
| `chat/repository/ChatRoomMemberRepository.java` | `findByRoomIdAndUserId`, `findByUserIdAndRoomIdIn` |

### 1.2 수정 파일

| 파일 | 변경 내용 |
|------|-----------|
| `chat/dto/ChatRoomResponse.java` | `unreadCount` 필드 추가 |
| `chat/repository/ChatMessageRepository.java` | `countUnread`, `countAllUnread`, `findMaxIdByRoomId` 쿼리 추가 |
| `chat/service/ChatService.java` | `markAsRead`, `getUnreadCount`, `markAsReadLatest`, `getRooms` 변경, `getMessages` 자동 읽음 추가 |
| `chat/controller/ChatController.java` | `PATCH /read`, `GET /unread` 엔드포인트 추가, 소켓 emit 연동 |
| `chat/handler/ChatSocketHandler.java` | `chat:read` 핸들러, `chat:join` 자동 읽음 처리 추가 |

---

## 2. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| lastReadMessageId 방식 | per-message 대신 per-member 방식 선택 | 쿼리 단순, 1:1 채팅 최적 |
| ChatService 확장 | 별도 서비스 없이 ChatService에 통합 | 복잡도 최소화 |
| `markAsReadLatest()` 추가 | 소켓용 최신 메시지 자동 조회 메서드 | 소켓 핸들러 코드 간소화 |
| `upsertLastRead()` private | 공통 로직 분리 | 중복 제거 (4곳 공유) |
| `@PrePersist` `updatedAt` | 생성자 설정 대신 JPA 어노테이션 | Spring 관례 준수 |

---

## 3. chat:read 트리거 (4곳)

CLAUDE.md에 명시된 규칙 — 전체 구현 완료:

| 트리거 | 구현 위치 | 상태 |
|--------|-----------|------|
| `chat:read` 소켓 이벤트 | `ChatSocketHandler.onChatRead()` | ✅ |
| `chat:join` 소켓 이벤트 | `ChatSocketHandler.onChatJoin()` | ✅ |
| `GET /messages?page=0` | `ChatController.getMessages()` | ✅ |
| `PATCH /read` REST | `ChatController.markAsRead()` | ✅ |

---

## 4. Success Criteria 최종

| 기준 | 상태 |
|------|------|
| GET /chat/rooms unreadCount 포함 | ✅ Met |
| chat:read 소켓 이벤트 동작 | ✅ Met |
| lastReadMessageId MAX 보존 | ✅ Met |
| 본인 메시지 unreadCount 제외 | ✅ Met |
| null (첫 방문) 케이스 처리 | ✅ Met |

**5/5 (100%)**

---

## 5. Gap 및 잔여 사항

| 심각도 | 항목 | 내용 |
|--------|------|------|
| ⚠️ Minor | `getRooms()` N+1 | 룸별 개별 `countUnread()` 호출. 스케일 시 배치 쿼리로 개선 권장 |

> 기능 정확성 문제 없음. Critical/Important 갭 없음.

---

## 6. Match Rate

| Category | Score |
|----------|:-----:|
| Structural | 100% |
| Functional | 97% |
| API Contract | 100% |
| **Overall** | **99%** |
