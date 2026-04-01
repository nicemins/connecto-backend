# CLAUDE.md — Connecto 백엔드 AI 개발 컨텍스트

> 이 파일은 Claude Code AI가 백엔드 개발 시 참조하는 프로젝트 명세서입니다.
> 프론트엔드 컨텍스트: `C:\connecto-app\CLAUDE.md`
> 원본 스펙 시트: https://docs.google.com/spreadsheets/d/1lawKmS9PrXNMYv2US2IXgCX-SejIFrBuvuZI9DfssRU

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | Connecto |
| 설명 | 5분 익명 보이스 채팅 기반 실시간 매칭 및 언어 교환 플랫폼 |
| 슬로건 | "지금, 누군가와 5분만 이야기해요." |
| Group | com.pm |
| Artifact | connecto |
| Package | com.pm.connecto |
| Java | 17 |
| Spring Boot | 3.5.9 |
| Build | Gradle |

---

## 2. 기술 스택

| 분류 | 기술 | 비고 |
|------|------|------|
| Language | Java 17 | |
| Framework | Spring Boot 3.5.9 | |
| Build | Gradle (YAML config) | |
| DB (prod) | PostgreSQL | |
| DB (dev) | PostgreSQL (로컬) | Windows 로컬 postgres:5432 / `connecto` DB |
| Cache | Redis 6379 | Lettuce pool |
| 분산 락 | Redisson | 매칭 큐 동시성 제어 |
| Auth | JWT Custom Filter | Spring Security 없음 |
| Realtime | Socket.IO (netty-socketio) | 포트 9092 |
| API Docs | Swagger / OpenAPI 3 | `/swagger-ui.html` |
| Deploy | Docker + docker-compose | |

---

## 3. 설정값 (application.yaml)

```yaml
# REST API 포트: 8080 (Spring Boot 기본)
# Socket.IO 포트: 9092

socketio:
  host: 0.0.0.0
  port: 9092

jwt:
  secret: (256bit 이상 키)
  access-expiration: 3600000    # 1시간 (ms)
  refresh-expiration: 604800000 # 7일 (ms)

cors:
  allowed-origins: http://localhost:3000

spring.data.redis:
  host: localhost
  port: 6379
  timeout: 2000ms
```

> **주의:** `MatchController`, `SocketIOConfig`는 `@ConditionalOnProperty(spring.data.redis.host)` 조건부 활성화 — Redis 없으면 매칭 API 비활성화

---

## 4. 패키지 구조

```
com.pm.connecto/
├── ConnectoApplication.java
├── auth/
│   ├── config/FilterConfig.java          # JWT 필터 등록
│   ├── filter/JwtAuthenticationFilter.java
│   ├── jwt/JwtTokenProvider.java
│   └── service/AuthService.java          # authenticate, generateToken, refreshAccessToken, socialLogin
├── call/
│   ├── controller/CallController.java    # POST /call/end, /call/again, /call/request/{friendId}, /call/reject/{sessionId}
│   ├── dto/CallEndRequest.java
│   ├── dto/CallAgainRequest.java
│   ├── dto/FriendCallResponse.java
│   └── service/CallService.java
├── common/
│   ├── config/AsyncConfig.java
│   ├── config/PasswordEncoderConfig.java
│   ├── config/S3Config.java              # AWS SDK v2 S3Client 빈 등록
│   ├── config/SwaggerConfig.java
│   ├── config/WebConfig.java             # CORS 설정
│   ├── context/UserContext.java          # ThreadLocal userId
│   ├── exception/                        # Business, Duplicate, Forbidden, Lock, MaxLimit, NotFound, Unauthorized
│   ├── response/ApiResponse.java         # 공통 응답 래퍼
│   ├── response/ErrorCode.java
│   ├── service/S3Service.java            # S3 upload / delete / extractKey
│   └── socket/SocketAuthUtil.java        # Socket.IO JWT 추출 공통 유틸 (ChatSocketHandler/MatchSocketHandler 공유)
├── chat/
│   ├── controller/ChatController.java    # POST/GET /chat/rooms, GET /chat/rooms/{roomId}/messages, POST /chat/rooms/{roomId}/messages/image, PATCH /read, GET /unread
│   ├── domain/ChatMessage.java
│   ├── domain/ChatRoom.java
│   ├── domain/ChatRoomMember.java        # 읽음 상태 엔티티 (lastReadMessageId per user per room)
│   ├── domain/MessageType.java           # TEXT, IMAGE
│   ├── dto/ChatMessagePageResponse.java
│   ├── dto/ChatMessageResponse.java
│   ├── dto/ChatRoomCreateRequest.java
│   ├── dto/ChatRoomResponse.java
│   ├── dto/ReadRequest.java              # { lastMessageId }
│   ├── handler/ChatSocketHandler.java    # chat:join/leave/send/typing/read on / chat:receive(룸 전체) + chat:typing + chat:read(상대방) emit
│   ├── repository/ChatMessageRepository.java
│   ├── repository/ChatRoomMemberRepository.java
│   ├── repository/ChatRoomRepository.java
│   └── service/ChatService.java
├── friend/
│   ├── controller/FriendController.java  # GET /friends, /friends/requests, /friends/check, POST /friends/request, PATCH accept/reject, DELETE /{id}, POST /{id}/block
│   ├── domain/Block.java
│   ├── domain/FriendRequest.java
│   ├── domain/FriendRequestStatus.java   # PENDING, ACCEPTED, REJECTED
│   ├── domain/Friendship.java
│   ├── dto/BlockedUserResponse.java      # { blockedUserId, nickname, profileImageUrl, blockedAt }
│   ├── dto/FriendCheckResponse.java
│   ├── dto/FriendRequestCreateRequest.java
│   ├── dto/FriendRequestResponse.java
│   ├── dto/FriendResponse.java
│   ├── repository/BlockRepository.java
│   ├── repository/FriendRequestRepository.java
│   ├── repository/FriendshipRepository.java
│   └── service/FriendService.java
├── health/
│   └── HealthController.java             # GET /health
├── interest/
│   ├── controller/InterestController.java # POST/GET/DELETE /users/me/interests
│   ├── domain/Interest.java
│   ├── dto/InterestCreateRequest.java
│   ├── dto/InterestResponse.java
│   ├── repository/InterestRepository.java
│   └── service/InterestService.java
├── language/
│   ├── controller/LanguageController.java # GET/POST/PUT/DELETE /users/me/languages
│   ├── domain/Language.java
│   ├── domain/LanguageLevel.java         # BEGINNER, INTERMEDIATE, ADVANCED, NATIVE
│   ├── domain/LanguageType.java          # NATIVE, LEARNING
│   ├── dto/LanguageCreateRequest.java
│   ├── dto/LanguageRequest.java
│   ├── dto/LanguageResponse.java
│   ├── repository/LanguageRepository.java
│   └── service/LanguageService.java
├── match/
│   ├── config/RedisConfig.java
│   ├── config/RedissonConfig.java
│   ├── config/SocketIOConfig.java        # port 9092, ConditionalOnProperty
│   ├── controller/MatchController.java   # ConditionalOnProperty
│   ├── domain/CallSession.java
│   ├── domain/CallSessionStatus.java     # WAITING, IN_PROGRESS, ENDED
│   ├── dto/MatchResultResponse.java
│   ├── dto/MatchStartResponse.java
│   ├── dto/MatchStatusResponse.java
│   ├── handler/MatchSocketHandler.java   # Socket.IO 이벤트 핸들러
│   ├── repository/CallSessionRepository.java
│   ├── scheduler/CallSessionScheduler.java # 타임아웃 세션 정리
│   ├── service/MatchPollingService.java
│   ├── service/MatchQueueService.java    # Redis FIFO 큐
│   └── service/MatchService.java
├── profile/
│   ├── controller/ProfileController.java # POST/GET/PATCH /users/me/profile, PATCH /image
│   ├── domain/Profile.java
│   ├── dto/ProfileCreateRequest.java
│   ├── dto/ProfileResponse.java
│   ├── dto/ProfileUpdateRequest.java
│   ├── repository/ProfileRepository.java
│   └── service/ProfileService.java
├── notification/
│   ├── config/FcmConfig.java             # Firebase Admin SDK 초기화 (nullable, 미설정 시 FCM 비활성)
│   ├── controller/DeviceTokenController.java # POST/DELETE /users/me/device-token
│   ├── domain/DeviceToken.java           # userId, token, platform, createdAt
│   ├── dto/DeviceTokenRequest.java       # { token, platform } — android|ios 검증
│   ├── repository/DeviceTokenRepository.java
│   └── service/FcmService.java          # registerToken, deleteToken, @Async sendToUserAsync, @Async sendToUserWithDataAsync
├── report/
│   ├── controller/ReportController.java  # POST /reports
│   ├── domain/Report.java
│   ├── dto/ReportCreateRequest.java
│   ├── repository/ReportRepository.java
│   └── service/ReportService.java
└── user/
    ├── controller/AuthController.java    # POST /auth/signup, /login, /refresh, /logout, /social-login
    ├── controller/UserController.java    # GET/PUT/DELETE /users/me, GET /users/exists/email, GET/DELETE /me/blocks
    ├── domain/User.java
    ├── domain/UserStatus.java            # ACTIVE, BLOCKED, DELETED
    ├── dto/ (Login, UserCreate, UserMe, UserResponse, Availability, SocialLogin...)
    ├── repository/UserRepository.java
    ├── service/UserMeService.java        # GET /users/me 통합 조회
    └── service/UserService.java
```

---

## 5. API 명세

### 공통 응답 형식
```java
// ApiResponse<T>
{ "success": true/false, "data": T, "message": "..." }
```

### 5.1 인증 (`/auth`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/auth/signup` | 회원가입 → 201 Created | X |
| POST | `/auth/login` | 로그인 → accessToken(body) + refreshToken(Set-Cookie) | X |
| POST | `/auth/refresh` | 토큰 갱신 (`@CookieValue refreshToken`) | Cookie |
| POST | `/auth/logout` | 로그아웃 → refreshToken 쿠키 maxAge=0 → 204 | O |
| POST | `/auth/social-login` | 소셜 로그인 (Google OAuth ID Token) → 유저 자동 생성 | X |

**refreshToken 쿠키 설정:**
```
HttpOnly=true, Secure=true, SameSite=Strict, Path=/
```

### 5.2 사용자 (`/users`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| GET | `/users/me` | user + profile + languages + interests 통합 조회 | O |
| PUT | `/users/me` | 비밀번호 변경 | O |
| DELETE | `/users/me` | 회원 탈퇴 (soft delete) → 204 | O |
| GET | `/users/exists/email?email=` | 이메일 중복 확인 | X |

### 5.3 프로필 (`/users/me/profile`, `/profiles`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/users/me/profile` | 프로필 최초 생성 | O |
| GET | `/users/me/profile` | 내 프로필 조회 | O |
| PATCH | `/users/me/profile` | 프로필 수정 (nickname, bio) | O |
| PATCH | `/users/me/profile/image` | 이미지 수정 (multipart/form-data, 5MB, JPEG/PNG/WEBP) | O |
| GET | `/profiles/exists?nickname=` | 닉네임 중복 확인 | X |

### 5.4 언어 (`/users/me/languages`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/users/me/languages` | 언어 추가 (NATIVE/LEARNING, 최대 10개) | O |
| GET | `/users/me/languages` | 내 언어 목록 조회 | O |
| PUT | `/users/me/languages` | 언어 전체 교체 | O |
| DELETE | `/users/me/languages/{id}` | 언어 삭제 | O |

### 5.5 관심사 (`/users/me/interests`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/users/me/interests` | 관심사 저장 | O |
| GET | `/users/me/interests` | 내 관심사 목록 조회 | O |
| DELETE | `/users/me/interests/{id}` | 관심사 삭제 | O |

### 5.6 매칭 (`/match`) — Redis 필요
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/match/start` | 매칭 대기열 진입 | O |
| POST | `/match/cancel` | 대기 취소 | O |
| GET | `/match/status` | 매칭 상태 (IDLE/MATCHING/MATCHED) | O |
| GET | `/match/result/{sessionId}` | 통화 종료 후 상대 프로필 조회 → `{ profile, wantAgain, otherWantAgain }` (권한 필수) | O |

### 5.7 통화 (`/call`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/call/end` | 통화 종료 → 세션 ENDED | O |
| POST | `/call/again` | 재연결 의사 표현 (wantAgain bool) | O |
| POST | `/call/request/{friendId}` | 친구에게 통화 요청 → 즉시 IN_PROGRESS 세션 생성 / 상대방 통화 중 시 409 | O |
| POST | `/call/reject/{sessionId}` | 수신된 통화 거절 → 세션 ENDED, 발신자에게 `call:rejected` emit | O |

### 5.8 친구 (`/friends`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| GET | `/friends` | 내 친구 목록 조회 | O |
| GET | `/friends/requests` | 받은 친구 요청 목록 (PENDING) | O |
| GET | `/friends/check?userId={targetUserId}` | 친구/차단 여부 확인 → `{ isFriend, friendshipId, isBlocked }` | O |
| POST | `/friends/request` | 친구 요청 전송 | O |
| PATCH | `/friends/request/{id}/accept` | 친구 요청 수락 → Friendship 생성 | O |
| PATCH | `/friends/request/{id}/reject` | 친구 요청 거절 | O |
| DELETE | `/friends/{friendshipId}` | 친구 삭제 → 204 | O |
| POST | `/friends/{friendshipId}/block` | 친구 차단 (Friendship 삭제 + Block 생성) → 200 | O |

### 5.9 차단 (`/users/me/blocks`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| GET | `/users/me/blocks` | 내 차단 목록 조회 → `[{ blockedUserId, nickname, profileImageUrl, blockedAt }]` | O |
| DELETE | `/users/me/blocks/{blockedUserId}` | 차단 해제 → 204 | O |

> 차단 시 기존 채팅방은 유지되나 차단 상태에서는 메시지 전송 불가 (403 `MESSAGE_BLOCKED`)
> 차단된 사용자와는 친구 요청 불가, 매칭 대기열에서 자동 제외

### 5.10 채팅 (`/chat`) — 친구 간 1:1
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/chat/rooms` | 채팅방 생성 (친구 사이에만). 이미 있으면 기존 반환 → 201 | O |
| GET | `/chat/rooms` | 내 채팅방 목록 (최신 메시지 순) | O |
| GET | `/chat/rooms/{roomId}/messages?page=0&size=50` | 메시지 히스토리 (최신순 페이징, max 100) — 첫 페이지 자동 읽음 처리 | O |
| POST | `/chat/rooms/{roomId}/messages/image` | 이미지 메시지 전송 (multipart/form-data, 5MB, JPEG/PNG/WEBP) → 201 | O |
| PATCH | `/chat/rooms/{roomId}/read` | 읽음 처리 — lastReadMessageId 업데이트, 상대방에게 `chat:read` 소켓 emit | O |
| GET | `/chat/rooms/{roomId}/unread` | 미읽음 카운트 조회 → `{ unreadCount: N }` | O |

**ChatRoomCreateRequest:** `{ "friendId": Long }`
**ChatRoomResponse:** `{ roomId, friendId, friendNickname, friendProfileImageUrl, lastMessage, unreadCount, updatedAt }`
**ReadRequest:** `{ "lastMessageId": Long }` (PATCH /read 바디)
**ChatMessagePageResponse:** `{ messages: [...], hasNext, page, size }`
**ChatMessageResponse:** `{ id, senderId, content, imageUrl, messageType, createdAt }` — `messageType`: `"TEXT"` | `"IMAGE"`, `imageUrl`은 IMAGE 타입만 포함, `content`는 IMAGE 타입 시 null
**이미지 lastMessage:** `"사진"` 고정 문자열

### 5.11 신고 (`/reports`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/reports` | 사용자 신고 (자기 신고/중복 신고 방지) | O |

**Request Body:** `{ "sessionId": Long (필수), "reportedUserId": Long (필수), "reason": String (선택, 500자 이하) }`

### 5.12 푸시 알림 (`/users/me/device-token`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/users/me/device-token` | FCM 디바이스 토큰 등록/갱신 | O |
| DELETE | `/users/me/device-token` | FCM 디바이스 토큰 삭제 | O |

**Request Body:** `{ "token": "fcm_token_string", "platform": "android" | "ios" }`

---

## 6. 인증 아키텍처

```
요청
  → JwtAuthenticationFilter
  → Authorization: Bearer <accessToken> 추출
  → JwtTokenProvider.validateToken()
  → UserContext.setUserId(userId)   ← ThreadLocal
  → Controller에서 userContext.getUserId() 사용

401 응답 조건:
  - 토큰 없음
  - 토큰 만료
  - 서명 불일치
```

**인증 불필요 경로:**
- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/social-login`
- `GET /users/exists/email`
- `GET /profiles/exists`
- `GET /health`
- `/swagger-ui/**`, `/v3/api-docs/**`
- `/h2-console/**`

---

## 7. 도메인 모델

### User
```java
Long id
String email
String password        // BCrypt 해시 (소셜 로그인 시 null)
String provider        // "local" | "google" (기본값 "local")
String providerId      // 소셜 로그인 제공자 ID (nullable)
UserStatus status      // ACTIVE, BLOCKED, DELETED
LocalDateTime createdAt
LocalDateTime updatedAt
LocalDateTime deletedAt  // soft delete
```

### Profile
```java
Long id
User user              // @OneToOne
String nickname
String profileImageUrl
String bio
LocalDateTime createdAt
LocalDateTime updatedAt
```

### Language
```java
Long id
User user              // @ManyToOne
String languageCode    // ISO: "ko", "en", "ja", "zh", "es", "fr", "de"
LanguageType type      // NATIVE, LEARNING
LanguageLevel level    // BEGINNER, INTERMEDIATE, ADVANCED, NATIVE
```

### Interest
```java
Long id
User user              // @ManyToOne
String tag             // 관심사 태그 (최대 50자) — DTO 필드명: tag (category 아님)
```

### CallSession
```java
Long id
User user1
User user2
CallSessionStatus status  // WAITING, IN_PROGRESS, ENDED
String webrtcChannelId
Boolean user1WantAgain    // 기본값 false
Boolean user2WantAgain    // 기본값 false
LocalDateTime createdAt
LocalDateTime updatedAt
LocalDateTime startedAt
LocalDateTime endedAt
```

### FriendRequest
```java
Long id
User sender            // @ManyToOne
User receiver          // @ManyToOne
FriendRequestStatus status  // PENDING, ACCEPTED, REJECTED
LocalDateTime createdAt
LocalDateTime updatedAt
```

### Friendship
```java
Long id
User user1             // @ManyToOne
User user2             // @ManyToOne
LocalDateTime createdAt
```

### Block
```java
Long id
User blocker           // @ManyToOne
User blocked           // @ManyToOne
LocalDateTime createdAt
// UniqueConstraint: uk_block(blocker_id, blocked_id)
// 인덱스: idx_block_blocker, idx_block_blocked
```

### ChatRoom
```java
Long id
User user1             // @ManyToOne LAZY
User user2             // @ManyToOne LAZY
LocalDateTime createdAt
LocalDateTime updatedAt
// UniqueConstraint: uk_chat_room(user1_id, user2_id)
// isMember(Long userId), getOtherUser(Long userId), updateTimestamp(LocalDateTime)
```

### ChatMessage
```java
Long id
ChatRoom room          // @ManyToOne LAZY
User sender            // @ManyToOne LAZY
MessageType messageType // TEXT (기본값), IMAGE — columnDefinition "VARCHAR(10) DEFAULT 'TEXT'"
String content         // max 1000자, TEXT 타입 전용 (nullable)
String imageUrl        // max 1000자, IMAGE 타입 전용 (nullable)
LocalDateTime createdAt
// 인덱스: idx_chat_message_room_created (room_id, created_at DESC)
```

### ChatRoomMember
```java
Long id
ChatRoom room          // @ManyToOne LAZY
User user              // @ManyToOne LAZY
Long lastReadMessageId // nullable — 마지막으로 읽은 메시지 ID (MAX 보존: 이전 값보다 클 때만 업데이트)
LocalDateTime updatedAt
// UniqueConstraint: uk_chat_room_member(chat_room_id, user_id)
// 인덱스: idx_chat_room_member_room_user, idx_chat_room_member_user
// updateLastRead(Long messageId): messageId > current 일 때만 업데이트
```

### MessageType
```java
TEXT   // 텍스트 메시지 (기본값)
IMAGE  // 이미지 메시지 — content null, imageUrl S3 URL
```

### Report
```java
Long id
User reporter          // @ManyToOne
User reported          // @ManyToOne
Long sessionId
String reason
LocalDateTime createdAt
```

### DeviceToken
```java
Long id
User user              // @ManyToOne
String token           // FCM 토큰, unique, max 500자
String platform        // "android" | "ios"
LocalDateTime createdAt
// 인덱스: idx_device_token_user_id, idx_device_token_token (unique)
```

---

## 8. Socket.IO 이벤트 (MatchSocketHandler)

| 방향 | 이벤트 | 설명 |
|------|--------|------|
| on | `match:start` | 매칭 요청 |
| on | `match:cancel` | 매칭 취소 |
| emit → client | `match:success` | 매칭 완료 → `{ sessionId, webrtcChannelId, isOfferer }` |
| emit → client | `match:error` | 매칭 실패 → `{ code, message }` |
| emit → client | `match:cancelled` | 매칭 취소 완료 → `{ success: true }` |
| on | `webrtc:join` | 통화 채널 입장 → `{ channelId }` |
| on | `webrtc:offer` | WebRTC Offer SDP |
| on | `webrtc:answer` | WebRTC Answer SDP |
| on | `webrtc:ice` | ICE Candidate |
| emit → client | `webrtc:error` | 채널 인가 실패 → `{ message }` |
| emit → client | `call:ended` | 상대방이 통화 종료 → `{ sessionId }` |
| emit → client | `call:rematch` | 양측 재통화 동의 시 새 세션 → `{ sessionId, webrtcChannelId, isOfferer }` |
| emit → client | `call:incoming` | 친구 통화 요청 수신 → `{ sessionId, webrtcChannelId, callerId, callerNickname }` |
| emit → client | `call:rejected` | 수신자가 통화 거절 → `{ sessionId }` |
| emit → client | `friend:status-change` | 친구 온라인/오프라인 변경 → `{ friendId, isOnline }` |

| on | `chat:join` | 채팅 룸 입장 → `{ roomId: Long }` — socket.join("chat:" + roomId) |
| on | `chat:leave` | 채팅 룸 퇴장 → `{ roomId: Long }` (선택) |
| on | `chat:send` | 채팅 메시지 전송 → `{ roomId: Long, content: String }` |
| emit → 룸 전체 | `chat:receive` | 채팅 메시지 수신 → `{ roomId, message: { id, senderId, content, imageUrl, messageType, createdAt } }` — `chat:{roomId}` 룸 브로드캐스트 (발신자 포함, io.in(room) 방식) |
| on | `chat:typing` | 타이핑 인디케이터 → `{ roomId: Long }` |
| emit → 상대방 | `chat:typing` | 타이핑 relay → `{ roomId: Long }` (본인 미포함) |
| on | `chat:read` | 읽음 처리 → `{ roomId: Long }` — 최신 메시지 ID 자동 조회 후 읽음 처리, 상대방에게 `chat:read` emit |
| emit → 상대방 | `chat:read` | 읽음 알림 → `{ roomId, readerId, lastReadMessageId }` — chat:join/chat:read on / REST GET messages(p0) / REST PATCH /read 시 emit |
| emit → client | `chat:error` | 채팅 오류 → `{ message }` (인증 실패 / 필드 누락 / 1000자 초과 / 차단 상태) |

**클라이언트 인증:** `Authorization: Bearer <token>` 헤더 또는 `?token=` URL 파라미터로 JWT 전송 (netty-socketio는 `socket.auth` 미지원)

**`emitToUser()` 패턴:** `MatchSocketHandler.emitToUser(userId, eventName, data)` — CallService, ChatSocketHandler 등 외부 서비스가 userId 기반으로 소켓 이벤트를 전송할 때 사용. MatchSocketHandler가 `clientUserIdMap`을 관리하므로 다른 서비스는 이 메서드를 통해 emit.

---

## 9. 개발 규칙 및 컨벤션

### 커밋 타입
| Type | 설명 |
|------|------|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `chore` | 빌드/설정 |
| `test` | 테스트 |
| `docs` | 문서 |

### 코드 규칙
- Controller → Service → Repository 레이어 분리 엄수
- `UserContext`로 인증된 userId 주입 (`@RequestHeader` 사용 금지)
- 예외는 `common/exception/` 하위 클래스 사용 (`GlobalExceptionHandler`가 처리)
- 모든 응답은 `ApiResponse.success(data)` 래핑
- `@ConditionalOnProperty`로 Redis 없는 환경 대응

### API 응답 코드
| 상황 | 코드 |
|------|------|
| 성공 | 200 OK |
| 생성 | 201 Created |
| 삭제/로그아웃 | 204 No Content |
| 잘못된 입력 | 400 Bad Request |
| 인증 실패 | 401 Unauthorized |
| 권한 없음 | 403 Forbidden |
| 리소스 없음 | 404 Not Found |
| 중복 | 409 Conflict |

---

## 10. 로컬 개발 환경

### 환경변수 설정 (`.env.local`)

루트에 `.env.local` 파일이 있음 (`.gitignore`로 커밋 제외). 최초 세팅 시 아래 항목 채울 것:

```env
GOOGLE_ANDROID_CLIENT_ID=<android_client_id>.apps.googleusercontent.com
GOOGLE_WEB_CLIENT_ID=<web_client_id>.apps.googleusercontent.com
AWS_ACCESS_KEY_ID=<실제 키 또는 dummy>
AWS_SECRET_ACCESS_KEY=<실제 키 또는 dummy>
AWS_S3_BUCKET=connecto-dev
AWS_REGION=ap-northeast-2
FIREBASE_SERVICE_ACCOUNT_JSON=<service_account_json_content>  # 미설정 시 FCM 비활성 (로컬 개발 OK)
```

### 빠른 시작 (권장)
```bash
# JAVA_HOME 필요 (bash에서 java 못 찾을 경우)
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.16.8-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

bash run-local.sh
# → .env.local 자동 로드 후 bootRun (dev 프로파일: PostgreSQL + Redis)
# Swagger:  http://localhost:8080/swagger-ui.html
# Socket.IO: ws://localhost:9092
# H2 콘솔: 비활성화 (dev 프로파일)
```

> **주의:** `run-local.sh`는 CRLF 줄바꿈 문제로 PowerShell에서 직접 실행하면 오류 남. Git Bash에서 실행할 것.

### 로컬 DB (PostgreSQL)
- 위치: 로컬 Windows PostgreSQL 17 (port 5432, PID ~8680)
- DB명: `connecto` (pgAdmin4에서 생성)
- 유저: `postgres` / 비밀번호: `.env.local`의 `DB_PASSWORD`
- `application-dev.yaml`로 H2 대신 PostgreSQL 사용 (`ddl-auto: update`)

### Redis 포함 전체 실행 (매칭 기능 포함)
```bash
docker-compose up -d   # Redis 컨테이너 실행 (connecto-redis)
bash run-local.sh
```

### 테스트
```bash
./gradlew test
```

---

## 11. 구현 현황

> **마지막 업데이트:** 2026-03-31 | 상세 변경 이력: `docs/CHANGELOG.md`

### 백엔드 완료 ✅

| 도메인 | 엔드포인트 |
|--------|-----------|
| 인증 | `POST /auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/social-login` |
| 사용자 | `GET/PUT/DELETE /users/me`, `GET /users/exists/email` |
| 프로필 | `POST/GET/PATCH /users/me/profile`, `PATCH /users/me/profile/image`, `GET /profiles/exists` |
| 언어 | `POST/GET/PUT/DELETE /users/me/languages` |
| 관심사 | `POST/GET/DELETE /users/me/interests` |
| 매칭 | `POST /match/start,cancel`, `GET /match/status,result/{id}` — Redis 필요 (@ConditionalOnProperty) |
| 통화 | `POST /call/end`, `POST /call/again`, `POST /call/request/{friendId}`, `POST /call/reject/{sessionId}` |
| 신고 | `POST /reports` |
| 친구 | `GET /friends`, `GET /friends/requests`, `GET /friends/check`, `POST /friends/request`, `PATCH /friends/request/{id}/accept,reject`, `DELETE /friends/{id}`, `POST /friends/{id}/block` |
| 차단 | `GET /users/me/blocks`, `DELETE /users/me/blocks/{blockedUserId}` |
| 채팅 | `POST/GET /chat/rooms`, `GET /chat/rooms/{id}/messages`, `POST /chat/rooms/{id}/messages/image`, `PATCH /chat/rooms/{id}/read`, `GET /chat/rooms/{id}/unread`, Socket `chat:send/chat:typing/chat:read` |
| 스케줄러 | CallSessionScheduler — 5분 초과 자동 종료 + 대기열 만료 정리 |
| 소켓 매칭 | `match:start`, `match:cancel` on / `match:success`, `match:error`, `match:cancelled` emit |
| WebRTC 시그널링 | `webrtc:join/offer/answer/ice` + `match:success`에 `isOfferer` |
| 푸시 알림 | `POST/DELETE /users/me/device-token` — FCM, 친구 요청/수락/통화 요청 트리거 |
| TURN 자격증명 | `GET /webrtc/turn-credentials` — HMAC-SHA1, TURN 미설정 시 STUN only |

**백엔드 미구현 항목 없음.** 모든 명세 API 구현 완료.

### 알려진 개선 필요 항목

현재 알려진 개선 필요 항목 없음.

### 운영 참고

**FCM 알림 이벤트:**
| 이벤트 | title | body |
|--------|-------|------|
| 친구 요청 | "친구 요청" | "{nickname}님이 친구 신청을 보냈습니다" |
| 친구 수락 | "친구 수락" | "{nickname}님이 친구 요청을 수락했어요" |
| 통화 요청 | "통화 요청" | "{nickname}님이 통화를 요청했어요" |
| 재통화 요청 | "다시 통화 요청" | "{nickname}님이 다시 통화하고 싶어합니다" |
| 재통화 연결 (data) | "재통화 연결" | "상대방도 다시 통화하고 싶어합니다!" |

**Rate Limit (AuthRateLimitInterceptor):**
- login/social-login: 분당 10회 / signup: 시간당 5회 (IP 기준)
- 차단 후 60초(login) / 3600초(signup) 자동 해제 — 영구 차단 아님
- 에뮬레이터 주의: 로컬에서 모든 기기가 127.0.0.1로 인식 → 한 기기 초과 시 다른 기기도 차단
- 수동 해제: `redis-cli DEL rate:login:127.0.0.1`

**GlobalExceptionHandler 커버리지:**
400: `HttpMessageNotReadable`, `MethodArgumentNotValid`, `ConstraintViolation`, `HandlerMethodValidation`, `MissingServletRequestParameter`, `MaxUploadSizeExceeded`, `IllegalArgument` /
401: `MissingRequestCookie` /
405: `HttpRequestMethodNotSupported` /
415: `HttpMediaTypeNotSupported` /
500: `DataAccess`, `RuntimeException` (fallback)
