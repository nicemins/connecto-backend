# Connecto 백엔드 변경 이력

> 주요 구현 및 수정 이력. 현재 상태 요약은 `CLAUDE.md` 섹션 11 참조.

---

## 2026-03-25

### chat:typing 및 친구 온라인 상태 초기화

| 파일 | 역할 |
|------|------|
| `chat/handler/ChatSocketHandler.java` | `chat:typing` 이벤트 등록 + `onChatTyping` 핸들러 추가 — `chatService.getOtherUserId()`로 상대방 확인 후 relay |
| `chat/service/ChatService.java` | `getOtherUserId(roomId, userId)` 추가 — 멤버십 검증 포함, 상대방 userId 반환 |
| `match/handler/MatchSocketHandler.java` | `onConnect` 내 `notifyOnlineFriendsToUser(userId, client)` 호출 추가. `notifyOnlineFriendsToUser()` — 내 친구 중 `clientUserIdMap`에 있는(= 접속 중인) 친구에게 `friend:status-change { friendId, isOnline: true }` emit |

**동작 원리:**
- `chat:typing`: 클라이언트 emit → 서버 수신 → 멤버십 확인 → 상대방에게만 relay (본인 미포함, DB 저장 안 함)
- 온라인 상태 초기화: connect 시 이미 접속 중인 친구 목록을 신규 접속자에게 일괄 전달

### chat:receive echo 및 이미지 메시지 전송

| 파일 | 역할 |
|------|------|
| `chat/domain/MessageType.java` | 신규 — `TEXT`, `IMAGE` enum |
| `chat/domain/ChatMessage.java` | `messageType` (`VARCHAR(10) DEFAULT 'TEXT'`), `imageUrl` 필드 추가. `content` nullable로 변경 |
| `chat/dto/ChatMessageResponse.java` | `imageUrl`, `messageType` 필드 추가 |
| `chat/repository/ChatMessageRepository.java` | `findLatestMessageContentByRoomIds` — IMAGE 타입 시 CASE WHEN → `"사진"` 반환 |
| `chat/service/ChatService.java` | `saveImageMessage()` 추가. `toResponse()` lastMessage IMAGE → `"사진"` 처리 |
| `chat/controller/ChatController.java` | `POST /chat/rooms/{roomId}/messages/image` 추가. S3 업로드 + `MatchSocketHandler` 소켓 emit (optional) |
| `chat/handler/ChatSocketHandler.java` | `chat:send` 처리 시 발신자에게 `chat:sent` + `chat:receive` 둘 다 emit (echo) |

**이미지 업로드 흐름:**
```
클라이언트 → POST /chat/rooms/{roomId}/messages/image (multipart image)
  → S3 업로드 (key: chat/{roomId}/{uuid}.ext)
  → ChatMessage(IMAGE, imageUrl) DB 저장
  → 소켓: 발신자/수신자 모두 chat:receive emit
  → 201 ChatMessageResponse 반환
```

**스키마 마이그레이션 주의:**
- `chat_messages` 테이블에 `message_type VARCHAR(10) DEFAULT 'TEXT' NOT NULL`, `image_url VARCHAR(1000)` 컬럼 추가
- `content` 컬럼 NOT NULL → NULL 허용 변경
- 프로덕션 배포 시 마이그레이션 스크립트 필요

### 차단 목록 조회 API

| 파일 | 역할 |
|------|------|
| `friend/dto/BlockedUserResponse.java` | 신규 — `{ blockedUserId, nickname, profileImageUrl, blockedAt }` |
| `friend/repository/BlockRepository.java` | `findAllByBlockerIdWithBlocked()` — JOIN FETCH (N+1 방지), 차단일시 역순 정렬 |
| `friend/service/FriendService.java` | `getBlockList(userId)` — 차단 목록 + 프로필 일괄 조회 |
| `user/controller/UserController.java` | `GET /users/me/blocks` 추가 |

---

## 2026-03-23

### 예외 처리 표준화

Zero Script QA + Gap Analysis(93%→97%) 수행 후 6개 항목 수정.

| 파일 | 수정 내용 |
|------|----------|
| `common/exception/GlobalExceptionHandler.java` | `HttpMessageNotReadableException` (400), `HttpRequestMethodNotSupportedException` (405), `HttpMediaTypeNotSupportedException` (415) 핸들러 추가 |
| `common/exception/LockAcquisitionException.java` | `REDIS_ERROR` → `LOCK_ACQUISITION_FAILED` (전용 에러코드 사용) |
| `match/service/MatchService.java` | `ForbiddenException(ALREADY_IN_CALL)` → `BusinessException(ALREADY_IN_CALL)` (409 의미 정확) |
| `report/service/ReportService.java` | `BusinessException(SESSION_NOT_FOUND)` → `ResourceNotFoundException` / `BusinessException(ACCESS_DENIED)` → `ForbiddenException` |
| `auth/interceptor/AuthRateLimitInterceptor.java` | 429 응답 수동 JSON → `ObjectMapper + ApiResponse.error(TOO_MANY_REQUESTS)` 통일 |

---

## 2026-03-18

### 친구 삭제·차단·확인 API / 1:1 채팅 / 매칭 차단 필터

| 파일 | 역할 |
|------|------|
| `friend/domain/Block.java` | Block 엔티티 (blocker, blocked, uk_block 유니크 제약) |
| `friend/repository/BlockRepository.java` | existsBlockBetween, findBlockedIdsByBlockerId, findBlockerIdsByBlockedId |
| `friend/controller/FriendController.java` | `DELETE /{friendshipId}`, `POST /{friendshipId}/block`, `GET /check` 추가 |
| `friend/service/FriendService.java` | deleteFriend, blockFriend, unblockUser, checkFriend 추가. Friendship.isMember() 활용 |
| `friend/domain/Friendship.java` | `isMember(Long userId)` 메서드 추가 |
| `user/controller/UserController.java` | `GET /users/me/blocks`, `DELETE /users/me/blocks/{blockedUserId}` 추가 |
| `match/service/MatchQueueService.java` | findMatch() 루프 전 차단 목록 일괄 조회 — N+1 → 2 쿼리 개선 |
| `chat/domain/ChatRoom.java` | ChatRoom 엔티티. 생성자에서 `user1.id < user2.id` 정규화 (uk_chat_room 방향성 문제 방지) |
| `chat/domain/ChatMessage.java` | ChatMessage 엔티티 (room, sender, content 1000자, 복합 인덱스) |
| `chat/repository/ChatRoomRepository.java` | findBetween, findAllByUserIdOrderByUpdatedAtDesc (JOIN FETCH) |
| `chat/repository/ChatMessageRepository.java` | findByRoomIdOrderByCreatedAtDesc (JOIN FETCH sender, N+1 방지), findLatestMessageContentByRoomIds |
| `chat/service/ChatService.java` | createOrGetRoom → `RoomResult(response, created)` 반환(201/200 구분), DataIntegrityViolationException catch(레이스컨디션 방어). saveMessage → `SavedMessage(messageResponse, senderId, otherUserId)` 반환 (LAZY 접근 완전 제거) |
| `chat/controller/ChatController.java` | POST /chat/rooms → ResponseEntity로 신규 201 / 기존 200 반환 |
| `chat/handler/ChatSocketHandler.java` | 발신자 → `chat:sent`, 수신자 → `chat:receive` 분리. SocketAuthUtil 사용 |
| `chat/dto/ChatMessageResponse.java` | `from(ChatMessage, Long senderId)` 오버로드 추가 (LAZY 프록시 접근 방지) |
| `common/socket/SocketAuthUtil.java` | Socket.IO JWT 추출 공통 유틸 (ChatSocketHandler/MatchSocketHandler 중복 제거) |
| `profile/repository/ProfileRepository.java` | findByUserIdIn 추가 (채팅방 목록 N+1 방지) |
| `common/response/ErrorCode.java` | FRIENDSHIP_NOT_FOUND, BLOCK_NOT_FOUND, ALREADY_BLOCKED, CHAT_ROOM_NOT_FOUND, MESSAGE_BLOCKED 추가 |

**버그 수정:** chat:receive 미수신 — LazyInitializationException (`@Transactional` 종료 후 LAZY 프록시 접근). `SavedMessage`에 DTO 변환 포함으로 해결
**프론트 변경 필요:** 발신자는 `chat:receive` 대신 `chat:sent` 이벤트 수신. `POST /chat/rooms` 응답 코드 신규 201 / 기존 200으로 분리

---

## 2026-03-17

### API 품질 개선

| 파일 | 수정 내용 |
|------|----------|
| `match/dto/MatchStatusResponse.java` | IDLE/MATCHING/MATCHED 3단계 구분 — `waiting()` 제거, `idle()`, `matching()` 추가 |
| `match/service/MatchService.java` | `getMatchStatus()` — 대기열 여부에 따라 MATCHING vs IDLE 반환 |
| `match/dto/MatchResultResponse.java` | `otherWantAgain` 필드 추가 |
| `match/service/MatchService.java` | `getMatchResult()` — `otherWantAgain` 계산 (user1↔user2 교차 참조) |
| `call/service/CallService.java` | `requestCallToFriend()` — 상대방 IN_PROGRESS 세션 존재 시 409 ALREADY_IN_CALL |
| `call/dto/FriendCallResponse.java` | `isOfferer` 필드 추가 — 발신자(caller)는 항상 `true` |
| `test/CallServiceTest.java` | `ForbiddenException(ACCESS_DENIED)` → `BusinessException(INVALID_SESSION_STATE)` (2개 케이스) |

---

## 2026-03-16

### 버그 수정 및 소켓 이벤트 추가

| 파일 | 수정 내용 |
|------|----------|
| `auth/interceptor/AuthRateLimitInterceptor.java` | Rate Limit 영구 차단 버그 수정 — Lua 스크립트에 `TTL == -1` 조건 추가 |
| `common/filter/SecurityHeadersFilter.java` | Swagger UI CSP 차단 수정 — `/swagger-ui/**`, `/v3/api-docs/**` CSP 완화 적용 |
| `auth/filter/JwtAuthenticationFilter.java` | 에러 응답 포맷 통일 — `ApiResponse.error()` 사용 |
| `common/exception/GlobalExceptionHandler.java` | `MissingRequestCookieException` 핸들러 추가 — refresh 쿠키 누락 시 500 → 401 |
| `common/response/ErrorCode.java` | `INVALID_SESSION_STATE` (409 Conflict) 추가 |
| `call/service/CallService.java` | endCall/expressCallAgain 403→409 수정 + call:ended / call:rematch / call:incoming 소켓 emit 추가 |
| `match/handler/MatchSocketHandler.java` | `emitToUser()` public 메서드 추가 + `notifyFriendsStatus()` — connect/disconnect 시 `friend:status-change` emit |
| `friend/service/FriendService.java` | FCM 텍스트 수정 — "친구 신청을 보냈습니다" |

---

## 2026-03-15

### 에뮬레이터 테스트 버그 수정

| 파일 | 수정 내용 |
|------|----------|
| `src/main/resources/application-dev.yaml` | H2 → PostgreSQL 전환. `ddl-auto: update`, `PostgreSQLDialect`, Redis 비밀번호 추가 |
| `src/main/resources/application.yaml` | `ddl-auto: create-drop` → `update` |
| `auth/interceptor/AuthRateLimitInterceptor.java` | Spring Data Redis 3.5.7 `pExpire` StackOverflowError — `redisTemplate.expire()` → Lua 스크립트 `INCR+EXPIRE` 원자 실행으로 대체 |
| `match/handler/MatchSocketHandler.java` | Socket.IO 토큰 추출 개선 (Authorization 헤더 + ?token= URL param). 매칭 이중 진입 방지 |
| `match/service/MatchService.java` | stale IN_PROGRESS 세션 정리 기준 10분 → 5분 |
| `call/service/CallService.java` | `expressCallAgain()` — wantAgain=true 시 FCM 알림 발송 |

---

## 2026-03-14

### TURN 자격증명

| 파일 | 역할 |
|------|------|
| `webrtc/controller/TurnCredentialController.java` | `GET /webrtc/turn-credentials` — 인증 필수 |
| `webrtc/service/TurnCredentialService.java` | HMAC-SHA1 자격증명 생성 (Coturn `--use-auth-secret` 호환), TTL 3600s |
| `webrtc/dto/TurnCredentialResponse.java` | `{ iceServers: [{ urls, username?, credential? }], ttl }` |
| `application.yaml` | `turn.secret`, `turn.url`, `turn.stun-url` 설정 추가 |

환경변수: `TURN_SECRET`, `TURN_URL` (미설정 시 STUN only)

---

## 2026-03-13

### 보안 강화 (OWASP Top 10 감사 Rev 3)

17개 전체 이슈 해결 완료. 감사 문서: `docs/02-design/security-spec.md`

| 파일 | 역할 |
|------|------|
| `auth/service/AuthService.java` | Refresh Token Redis 저장/검증/폐기 (`rt:{userId}`, TTL 7일) |
| `auth/interceptor/AuthRateLimitInterceptor.java` | IP별 Rate Limiting: login/social-login 10회/분, signup 5회/시간. Redis Lua INCR+EXPIRE. 429 응답 |
| `common/config/WebConfig.java` | `AuthRateLimitInterceptor` 등록 |
| `common/filter/SecurityHeadersFilter.java` | 보안 헤더 5종: X-Content-Type-Options, X-Frame-Options, Referrer-Policy, CSP, HSTS |
| `match/handler/MatchSocketHandler.java` | URL query param 토큰 제거(M-02), WebRTC 채널 인가(M-04), raw Thread → ExecutorService(100) + 120s timeout(M-06) |
| `match/repository/CallSessionRepository.java` | `findByWebrtcChannelIdAndUserId()` 추가 |
| `common/response/ErrorCode.java` | `TOO_MANY_REQUESTS` (429) 추가 |

> Redis 없는 환경에서는 Rate Limiting과 Token Revocation 자동 비활성화 (`@Autowired(required=false)`)

---

## 2026-03-11

**단위 테스트:** AuthService, UserService, ProfileService, LanguageService, InterestService, FriendService, CallService, ReportService, AuthController(통합) — 97개 전체 통과

---

## 2026-03-09

### FCM 푸시 알림

| 파일 | 역할 |
|------|------|
| `notification/config/FcmConfig.java` | Firebase Admin SDK 초기화. `FIREBASE_SERVICE_ACCOUNT_JSON` 미설정 시 null 반환 → FCM 비활성 |
| `notification/domain/DeviceToken.java` | FCM 토큰 엔티티 (user_id 인덱스, token unique 인덱스) |
| `notification/repository/DeviceTokenRepository.java` | findAllByUserId, findByToken, deleteByUserIdAndToken, deleteAllByUserId |
| `notification/service/FcmService.java` | 토큰 등록/삭제 + `@Async` FCM 전송 + UNREGISTERED 토큰 자동 삭제. `sendToUserWithDataAsync()` 추가 |
| `notification/controller/DeviceTokenController.java` | `POST/DELETE /users/me/device-token` |
| `friend/service/FriendService.java` | sendFriendRequest(), acceptFriendRequest() 완료 후 FCM 비동기 전송 |
| `call/service/CallService.java` | requestCallToFriend() 완료 후 FCM 비동기 전송 |
| `user/controller/AuthController.java` | logout 시 fcmService.deleteAllTokens() 연동 |

---

## 2026-03-07

### 프로필 이미지 업로드 (AWS S3)

| 파일 | 역할 |
|------|------|
| `common/config/S3Config.java` | AWS SDK v2 `S3Client` 빈 등록 |
| `common/service/S3Service.java` | S3 upload / delete / extractKey |
| `profile/service/ProfileService.updateProfileImage()` | 이미지 업로드 + 이전 이미지 삭제 + DB 업데이트 |

### 소셜 로그인 (Google OAuth)

| 파일 | 역할 |
|------|------|
| `user/domain/User.java` | `provider` (기본 "local"), `providerId` (nullable), `password` nullable, `createSocialUser()` 팩토리 |
| `user/dto/SocialLoginRequest.java` | `{ provider, token }` DTO |
| `auth/service/AuthService.socialLogin()` | provider 분기 → token 검증 → 유저 조회/자동 생성 |
| `auth/service/AuthService.verifyGoogleToken()` | `GoogleIdTokenVerifier`로 ID Token 검증 → email 추출 |

**Gradle 의존성:** `com.google.api-client:google-api-client:2.2.0`
> `google-auth-library-oauth2-http`에는 `GoogleIdTokenVerifier`가 없음 — `google-api-client`가 올바른 라이브러리

---

## 2026-03-06

- 관심사 API 구현 완료 (`GET /users/me` 응답에 포함)
- 신고 API 구현 완료 (자기 신고/중복 신고 방지)
- 친구 기본 구현 (요청/수락/거절)
- 스케줄러 구현 확인 완료 (CallSessionScheduler)
- WebRTC 시그널링 구현 완료 (`webrtc:join/offer/answer/ice`, `match:success`에 `isOfferer` 추가)
