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
│   ├── controller/CallController.java    # POST /call/end, /call/again, /call/request/{friendId}
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
│   └── service/S3Service.java            # S3 upload / delete / extractKey
├── friend/
│   ├── controller/FriendController.java  # GET /friends, /friends/requests, POST /friends/request, PATCH accept/reject
│   ├── domain/FriendRequest.java
│   ├── domain/FriendRequestStatus.java   # PENDING, ACCEPTED, REJECTED
│   ├── domain/Friendship.java
│   ├── dto/FriendRequestCreateRequest.java
│   ├── dto/FriendRequestResponse.java
│   ├── dto/FriendResponse.java
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
    ├── controller/UserController.java    # GET/PUT/DELETE /users/me, GET /users/exists/email
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

### 5.8 친구 (`/friends`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| GET | `/friends` | 내 친구 목록 조회 | O |
| GET | `/friends/requests` | 받은 친구 요청 목록 (PENDING) | O |
| POST | `/friends/request` | 친구 요청 전송 | O |
| PATCH | `/friends/request/{id}/accept` | 친구 요청 수락 → Friendship 생성 | O |
| PATCH | `/friends/request/{id}/reject` | 친구 요청 거절 | O |

### 5.9 신고 (`/reports`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/reports` | 사용자 신고 (자기 신고/중복 신고 방지) | O |

**Request Body:** `{ "sessionId": Long (필수), "reportedUserId": Long (필수), "reason": String (선택, 500자 이하) }`

### 5.10 푸시 알림 (`/users/me/device-token`)
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
| emit → client | `friend:status-change` | 친구 온라인/오프라인 변경 → `{ friendId, isOnline }` |

**클라이언트 인증:** `Authorization: Bearer <token>` 헤더 또는 `?token=` URL 파라미터로 JWT 전송 (netty-socketio는 `socket.auth` 미지원)

**`emitToUser()` 패턴:** `MatchSocketHandler.emitToUser(userId, eventName, data)` — CallService 등 외부 서비스가 userId 기반으로 소켓 이벤트를 전송할 때 사용. MatchSocketHandler가 `clientUserIdMap`을 관리하므로 다른 서비스는 이 메서드를 통해 emit.

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

## 11. 구현 현황 (항상 최신 유지)

> **마지막 업데이트:** 2026-03-17 (매칭 상태 IDLE/MATCHING 구분, otherWantAgain 응답, 친구 통화 중 409, isOfferer 명시)
>
> **API 테스트 결과 (2026-03-16):** 전체 API Zero Script QA 완료 — 회원가입/로그인/프로필/언어/관심사/친구/신고/매칭/TURN/로그아웃 정상 동작 확인
> **단위 테스트 현황 (2026-03-11):** AuthService, UserService, ProfileService, LanguageService, InterestService, FriendService, CallService, ReportService, AuthController(통합) — 97개 전체 통과
> **소켓 이벤트 추가 (2026-03-16):** `call:ended`, `call:rematch`, `call:incoming`, `friend:status-change` — MatchSocketHandler.emitToUser() 패턴으로 구현
> **FCM data payload (2026-03-16):** FcmService.sendToUserWithDataAsync() 추가 — call_rematch 시 백그라운드 딥링크용 data 필드 포함 전송
> **API 품질 개선 (2026-03-16):** JWT 필터 에러 포맷 ApiResponse 통일, refresh 쿠키 누락 500→401 수정, 세션 상태 오류 403→409 수정
> **보안 강화 (2026-03-13):** OWASP Top 10 감사 Rev 3 완료. Refresh Token Redis 폐기, Rate Limiting, WebRTC 채널 인가, 스레드 풀 제한, 보안 헤더 5종 추가
> **에뮬레이터 테스트 버그 수정 (2026-03-15):** 로컬 DB → PostgreSQL 전환, Redis pExpire 버그 Lua 스크립트로 수정, Socket.IO 토큰 추출(헤더+URL param), 매칭 이중 진입 방지, stale 세션 정리 5분, /call/again FCM 알림 추가

### 백엔드 완료 ✅

| 도메인 | 엔드포인트 | 비고 |
|--------|-----------|------|
| 인증 | `POST /auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout` | refreshToken HttpOnly 쿠키 / 로그아웃 시 FCM 토큰 전체 삭제 |
| 사용자 | `GET/PUT/DELETE /users/me`, `GET /users/exists/email` | |
| 프로필 | `POST/GET/PATCH /users/me/profile`, `PATCH /users/me/profile/image`, `GET /profiles/exists` | **2026-03-07 이미지 업로드 구현 완료** — AWS S3 |
| 언어 | `POST/GET/PUT/DELETE /users/me/languages` | |
| 관심사 | `POST/GET/DELETE /users/me/interests` | **2026-03-06 구현 완료** — `GET /users/me` 응답에 포함 |
| 매칭 | `POST /match/start,cancel`, `GET /match/status,result/{id}` | Redis 필요 (@ConditionalOnProperty). **2026-03-17 개선** — IDLE/MATCHING/MATCHED 3단계 상태, `result`에 `otherWantAgain` 추가 |
| 인증 (소셜) | `POST /auth/social-login` | **2026-03-07 Google OAuth ID Token 검증 구현** — User 자동 생성 포함 |
| 통화 | `POST /call/end`, `POST /call/again`, `POST /call/request/{friendId}` | **2026-03-16 소켓 이벤트 추가** — call:ended(종료 알림), call:rematch(상호 재통화), call:incoming(친구 통화 수신). **2026-03-17 개선** — 친구 통화 중 409, `FriendCallResponse.isOfferer` 추가 |
| 신고 | `POST /reports` | **2026-03-06 구현 완료** — 자기 신고/중복 신고 방지 |
| 친구 | `GET /friends`, `GET /friends/requests`, `POST /friends/request`, `PATCH /friends/request/{id}/accept`, `PATCH /friends/request/{id}/reject` | **2026-03-06 구현 완료** |
| 스케줄러 | CallSessionScheduler — 5분 초과 자동 종료 + 대기열 만료 정리 | **2026-03-06 구현 확인 완료** |
| 소켓 매칭 | `match:start`, `match:cancel` on / `match:success`, `match:error`, `match:cancelled` emit | |
| WebRTC 시그널링 | `webrtc:join/offer/answer/ice` 핸들러 + `match:success`에 `isOfferer` 추가 | **2026-03-06 구현 완료** |
| 푸시 알림 | `POST/DELETE /users/me/device-token` | **2026-03-09 FCM 구현 완료** — 친구 요청/수락/통화 요청 트리거 포함 |
| TURN 자격증명 | `GET /webrtc/turn-credentials` | **2026-03-14 구현 완료** — HMAC-SHA1 단기 자격증명, TURN 미설정 시 STUN only (SEC-H1 백엔드) |

### 프로필 이미지 업로드 세부 사항 (2026-03-07)

| 파일 | 역할 |
|------|------|
| `common/config/S3Config.java` | AWS SDK v2 `S3Client` 빈 등록 |
| `common/service/S3Service.java` | S3 upload / delete / extractKey |
| `profile/service/ProfileService.updateProfileImage()` | 이미지 업로드 + 이전 이미지 삭제 + DB 업데이트 |
| `user/controller/UserController` — `PATCH /users/me/profile/image` | multipart/form-data 수신, 5MB 제한, JPEG/PNG/WEBP만 허용 |

### 소셜 로그인 세부 사항 (2026-03-07)

| 파일 | 역할 |
|------|------|
| `user/domain/User.java` | `provider` (기본 "local"), `providerId` (nullable), `password` nullable, `createSocialUser()` 팩토리 |
| `user/dto/SocialLoginRequest.java` | `{ provider, token }` DTO |
| `auth/service/AuthService.socialLogin()` | provider 분기 → token 검증 → 유저 조회/자동 생성 |
| `auth/service/AuthService.verifyGoogleToken()` | `GoogleIdTokenVerifier`로 ID Token 검증 → email 추출 |
| `user/controller/AuthController` — `POST /auth/social-login` | public 엔드포인트, accessToken + refreshToken 쿠키 반환 |

**Gradle 의존성:** `com.google.api-client:google-api-client:2.2.0`
> `google-auth-library-oauth2-http`에는 `GoogleIdTokenVerifier`가 없음 — `google-api-client`가 올바른 라이브러리

**운영 필수 환경변수:**
```
AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_S3_BUCKET, AWS_REGION
GOOGLE_ANDROID_CLIENT_ID, GOOGLE_WEB_CLIENT_ID
FIREBASE_SERVICE_ACCOUNT_JSON
```

### 푸시 알림 세부 사항 (2026-03-09)

| 파일 | 역할 |
|------|------|
| `notification/config/FcmConfig.java` | Firebase Admin SDK 초기화. `FIREBASE_SERVICE_ACCOUNT_JSON` 미설정 시 null 반환 → FCM 비활성 |
| `notification/domain/DeviceToken.java` | FCM 토큰 엔티티 (user_id 인덱스, token unique 인덱스) |
| `notification/repository/DeviceTokenRepository.java` | findAllByUserId, findByToken, deleteByUserIdAndToken, deleteAllByUserId |
| `notification/service/FcmService.java` | 토큰 등록/삭제 + `@Async` FCM 전송 + UNREGISTERED 토큰 자동 삭제. `@Autowired(required=false)` 로 FirebaseApp null 처리. `sendToUserWithDataAsync()` 추가 — data payload(딥링크용) 포함 전송 |
| `notification/dto/DeviceTokenRequest.java` | `{ token, platform }` — platform은 "android"\|"ios" 패턴 검증 |
| `notification/controller/DeviceTokenController.java` | `POST/DELETE /users/me/device-token` |
| `friend/service/FriendService.java` | sendFriendRequest(), acceptFriendRequest() 완료 후 FCM 비동기 전송 |
| `call/service/CallService.java` | requestCallToFriend() 완료 후 FCM 비동기 전송. ProfileRepository + FcmService 추가 주입 |
| `user/controller/AuthController.java` | logout 시 fcmService.deleteAllTokens() 연동. UserContext 추가 주입 |

**알림 이벤트:**
| 이벤트 | 트리거 | title | body |
|--------|--------|-------|------|
| 친구 요청 | FriendService.sendFriendRequest() | "친구 요청" | "{nickname}님이 친구 신청을 보냈습니다" |
| 친구 수락 | FriendService.acceptFriendRequest() | "친구 수락" | "{nickname}님이 친구 요청을 수락했어요" |
| 통화 요청 | CallService.requestCallToFriend() | "통화 요청" | "{nickname}님이 통화를 요청했어요" |
| 재통화 연결 (data) | CallService.expressCallAgain() — bothWantAgain | "재통화 연결" | "상대방도 다시 통화하고 싶어합니다!" + data:  |
| 재통화 요청 | CallService.expressCallAgain() | "다시 통화 요청" | "{nickname}님이 다시 통화하고 싶어합니다" |

> FCM 전송 실패는 비즈니스 로직에 영향 없음 (로그만 기록). 로컬 개발 시 FIREBASE_SERVICE_ACCOUNT_JSON 없이 정상 동작.

### 보안 강화 세부 사항 (2026-03-13)

OWASP Top 10 감사 3회 수행, 17개 전체 이슈 해결 완료. 감사 문서: `docs/02-design/security-spec.md`

**Rev 3 (2026-03-13) 수정 내역:**

| 파일 | 역할 |
|------|------|
| `auth/service/AuthService.java` | `generateRefreshToken()` → Redis `rt:{userId}` 저장 (TTL 7일). `refreshAccessToken()` → Redis 검증. `revokeRefreshToken()` → 로그아웃 시 삭제 |
| `auth/interceptor/AuthRateLimitInterceptor.java` | IP별 Rate Limiting: login/social-login 10회/분, signup 5회/시간. Redis 기반 Lua INCR+EXPIRE. 429 응답. **두 에뮬레이터가 서버에서 127.0.0.1로 동일 인식 → 한쪽 초과 시 양쪽 차단됨** |
| `common/config/WebConfig.java` | `AuthRateLimitInterceptor` 등록 (`/auth/login`, `/auth/signup`, `/auth/social-login`) |
| `common/filter/SecurityHeadersFilter.java` | 보안 헤더 5종: X-Content-Type-Options, X-Frame-Options, Referrer-Policy, CSP, HSTS. **Swagger 경로(`/swagger-ui/**`, `/v3/api-docs/**`)는 CSP 완화 적용 (`default-src 'none'` → `script-src 'self' 'unsafe-inline'` 등)** |
| `match/handler/MatchSocketHandler.java` | URL query param 토큰 제거(M-02), WebRTC 채널 인가(M-04), raw Thread → ExecutorService(100) + 120s timeout(M-06) |
| `match/repository/CallSessionRepository.java` | `findByWebrtcChannelIdAndUserId()` 추가 |
| `common/response/ErrorCode.java` | `TOO_MANY_REQUESTS` (429) 추가 |

> Redis 없는 환경(로컬 개발)에서는 Rate Limiting과 Token Revocation이 자동 비활성화 (`@Autowired(required=false)`).

**Rate Limit 동작 규칙:**
- 차단 기준: login/social-login 분당 10회, signup 시간당 5회 (IP 기준)
- 차단 후 동작: 60초(login) / 3600초(signup) 뒤 자동 해제 — **영구 차단 아님**
- 차단 중 올바른 비밀번호 입력해도 429 반환 (정상 동작)
- 창 시작: 첫 번째 요청 시점부터 카운트 (마지막 실패 시점 아님)
- 에뮬레이터 주의: 로컬 환경에서 모든 기기가 127.0.0.1로 인식 → 한 기기에서 초과 시 다른 기기도 차단
- 수동 해제: `redis-cli DEL rate:login:127.0.0.1` 또는 `rate:login:0:0:0:0:0:0:0:1` (IPv6)

**주의 — Redis 재시작 후 키 TTL 손실 버그 (2026-03-16 수정):**
- Lua 스크립트는 `c == 1`일 때만 EXPIRE 설정 → Redis 재시작 등으로 키가 TTL 없이 잔존 시 영구 차단 발생
- 수정: `TTL == -1`인 경우에도 EXPIRE 재설정하도록 Lua 스크립트 개선

### TURN 자격증명 세부 사항 (2026-03-14)

| 파일 | 역할 |
|------|------|
| `webrtc/controller/TurnCredentialController.java` | `GET /webrtc/turn-credentials` — 인증 필수 |
| `webrtc/service/TurnCredentialService.java` | HMAC-SHA1 자격증명 생성 (Coturn `--use-auth-secret` 호환), TTL 3600s |
| `webrtc/dto/TurnCredentialResponse.java` | `{ iceServers: [{ urls, username?, credential? }], ttl }` |
| `application.yaml` | `turn.secret`, `turn.url`, `turn.stun-url` 설정 추가 |

**환경변수 추가:**
```
TURN_SECRET=   # Coturn --static-auth-secret 값 (미설정 시 STUN only)
TURN_URL=      # 예: turn:your-server.com:3478
```

> 프론트엔드 연동: `GET /webrtc/turn-credentials` 호출 후 `RTCPeerConnection({ iceServers })` 초기화. `EXPO_PUBLIC_TURN_*` 환경변수 제거 필요 (프론트 SEC-H1).

### 에뮬레이터 테스트 버그 수정 세부 사항 (2026-03-15)

| 파일 | 수정 내용 |
|------|----------|
| `src/main/resources/application-dev.yaml` | H2 → PostgreSQL 전환. `ddl-auto: update`, `PostgreSQLDialect`, Redis 비밀번호 추가 |
| `src/main/resources/application.yaml` | `ddl-auto: create-drop` → `update` |
| `.env.local` | `DB_PASSWORD` 추가 |
| `auth/interceptor/AuthRateLimitInterceptor.java` | Spring Data Redis 3.5.7 `pExpire` StackOverflowError 버그 수정 — `redisTemplate.expire()` → Lua 스크립트 `INCR+EXPIRE` 원자 실행으로 대체 |
| `match/handler/MatchSocketHandler.java` | ① Socket.IO 토큰 추출 개선: `Authorization` 헤더 + `?token=` URL param 둘 다 지원 (netty-socketio는 `socket.auth` 미지원) ② 매칭 이중 진입 방지: REST `/match/start` 후 `match:start` 소켓 이벤트 수신 시 이미 대기열에 있으면 enqueue 건너뛰고 async 매칭만 시작 |
| `match/service/MatchService.java` | stale IN_PROGRESS 세션 자동 정리 기준 10분 → **5분**으로 단축 |
| `call/service/CallService.java` | `expressCallAgain()` — `wantAgain=true` 시 상대방에게 FCM 비동기 알림 발송 ("재통화 요청", "{nickname}님이 다시 통화하고 싶어해요") |

**알려진 미해결 이슈:**
- WebRTC 통화 연결 불안정 (에뮬레이터 환경에서 STUN 경유 — TURN 서버 필요 시 배포 환경에서 설정)

### 버그 수정 및 개선 세부 사항 (2026-03-16)

| 파일 | 수정 내용 |
|------|----------|
| `auth/interceptor/AuthRateLimitInterceptor.java` | Rate Limit 영구 차단 버그 수정 — Lua 스크립트에 `TTL == -1` 조건 추가. Redis 재시작 등으로 키에 TTL이 없는 경우에도 EXPIRE 재설정 |
| `common/filter/SecurityHeadersFilter.java` | Swagger UI 접속 불가 수정 — CSP `default-src 'none'`이 Swagger JS/CSS 로딩 차단. `/swagger-ui/**`, `/v3/api-docs/**` 경로는 CSP 완화 적용 |
| `auth/filter/JwtAuthenticationFilter.java` | 에러 응답 포맷 통일 — 자체 `ErrorResponse` record 제거, Spring `ObjectMapper` 주입 + `ApiResponse.error()` 사용으로 GlobalExceptionHandler와 포맷 일치 |
| `common/exception/GlobalExceptionHandler.java` | `MissingRequestCookieException` 핸들러 추가 — `POST /auth/refresh` 쿠키 누락 시 500 → 401 `INVALID_TOKEN` "토큰이 누락되었습니다." |
| `common/response/ErrorCode.java` | `INVALID_SESSION_STATE` (409 Conflict) 추가 — 세션 상태 불일치 시 사용 (기존 403 ACCESS_DENIED 오용 수정) |
| `call/service/CallService.java` | ① `endCall()` — 이미 종료된 세션 요청 시 403→409 `INVALID_SESSION_STATE` / ② `expressCallAgain()` — 통화 중 세션 요청 시 403→409 `INVALID_SESSION_STATE` / ③ `call:ended` 소켓 emit 추가 / ④ `call:rematch` 상호 동의 시 새 세션 생성 + 소켓 emit / ⑤ `call:incoming` 친구 통화 요청 소켓 emit 추가 |
| `match/handler/MatchSocketHandler.java` | `emitToUser(userId, eventName, data)` public 메서드 추가 — 외부 서비스(CallService)가 userId 기반 소켓 이벤트 전송 시 사용. `FriendshipRepository` 주입 + `notifyFriendsStatus()` 추가 — 소켓 connect/disconnect 시 친구 전원에게 `friend:status-change` emit |
| `friend/service/FriendService.java` | FCM 텍스트 수정 — "친구 요청을 보냈어요" → "친구 신청을 보냈습니다" |

### API 품질 개선 세부 사항 (2026-03-17)

| 파일 | 수정 내용 |
|------|----------|
| `match/dto/MatchStatusResponse.java` | IDLE/MATCHING/MATCHED 3단계 구분 — `waiting()` 제거, `idle()` (대기열 없음), `matching()` (대기열 있음) 추가 |
| `match/service/MatchService.java` | `getMatchStatus()` — 대기열(`isInQueue`) 여부에 따라 MATCHING vs IDLE 반환. 기존 WAITING 단일 상태 → 3단계 분리 |
| `match/dto/MatchResultResponse.java` | `otherWantAgain` 필드 추가 — `GET /match/result/{id}` 응답에 상대방 재통화 의사 포함 |
| `match/service/MatchService.java` | `getMatchResult()` — `otherWantAgain` 계산 (user1↔user2 교차 참조) 후 응답에 포함 |
| `call/service/CallService.java` | `requestCallToFriend()` — 상대방(`friendId`) IN_PROGRESS 세션 존재 시 409 ALREADY_IN_CALL 반환 (유령 세션 생성 방지) |
| `call/dto/FriendCallResponse.java` | `isOfferer` 필드 추가 — 발신자(caller)는 항상 `true` |
| `test/CallServiceTest.java` | 예외 타입 업데이트 — `ForbiddenException(ACCESS_DENIED)` → `BusinessException(INVALID_SESSION_STATE)` (2개 케이스) |

### 백엔드 미구현 항목 ❌

현재 미구현 항목 없음. 모든 명세 API 구현 완료.

### 알려진 개선 필요 항목 (중간 우선순위)

| 항목 | 내용 | 파일 |
|------|------|------|
| 통화 거절 API 없음 | `call:incoming` 수신 후 거절 시 백엔드 처리 없음 (5분 후 스케줄러 자동 정리) | — |

> **2026-03-17 처리 완료:** 매칭 상태 IDLE/MATCHING 구분, `otherWantAgain` 응답 추가, 친구 통화 시 상대방 통화 중 409 체크, `FriendCallResponse.isOfferer` 추가
