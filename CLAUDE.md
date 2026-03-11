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
| DB (dev) | H2 (in-memory) | `jdbc:h2:mem:connecto` |
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
│   └── service/FcmService.java          # registerToken, deleteToken, @Async sendToUserAsync
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
| GET | `/match/result/{sessionId}` | 통화 종료 후 상대 프로필 조회 (권한 필수) | O |

### 5.7 통화 (`/call`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/call/end` | 통화 종료 → 세션 ENDED | O |
| POST | `/call/again` | 재연결 의사 표현 (wantAgain bool) | O |
| POST | `/call/request/{friendId}` | 친구에게 통화 요청 → 즉시 IN_PROGRESS 세션 생성 | O |

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
| emit → client | `match:error` | 매칭 실패 → `{ message }` |
| on | `webrtc:join` | 통화 채널 입장 |
| on | `webrtc:offer` | WebRTC Offer SDP |
| on | `webrtc:answer` | WebRTC Answer SDP |
| on | `webrtc:ice` | ICE Candidate |

**클라이언트 인증:** Socket.IO `auth.token` 필드에서 JWT 추출

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
bash run-local.sh
# → .env.local 자동 로드 후 bootRun
# H2 콘솔: http://localhost:8080/h2-console
# Swagger:  http://localhost:8080/swagger-ui.html
# 매칭 API는 비활성화됨 (Redis 필요)
```

### Redis 포함 전체 실행
```bash
docker-compose up -d   # Redis 실행
bash run-local.sh
```

### 테스트
```bash
./gradlew test
```

---

## 11. 구현 현황 (항상 최신 유지)

> **마지막 업데이트:** 2026-03-11 (단위 테스트 전체 완료 — 97개 테스트 통과)
>
> **API 테스트 결과 (2026-03-10):** 회원가입/로그인/프로필/언어/관심사/친구/신고/매칭/로그아웃 정상 동작 확인
> **단위 테스트 현황 (2026-03-11):** AuthService, UserService, ProfileService, LanguageService, InterestService, FriendService, CallService, ReportService, AuthController(통합) — 97개 전체 통과
> **수정 사항:** `Interest.category` → `Interest.tag` (실제 구현 필드명), `GlobalExceptionHandler` RuntimeException 로깅 추가
> **JwtAuthenticationFilter 개선:** PUBLIC_PATHS 상수화, 토큰 타입 검증 추가
> **ReportService 개선:** 세션 참여 검증 + 피신고자 실제 상대방 검증 추가

### 백엔드 완료 ✅

| 도메인 | 엔드포인트 | 비고 |
|--------|-----------|------|
| 인증 | `POST /auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout` | refreshToken HttpOnly 쿠키 / 로그아웃 시 FCM 토큰 전체 삭제 |
| 사용자 | `GET/PUT/DELETE /users/me`, `GET /users/exists/email` | |
| 프로필 | `POST/GET/PATCH /users/me/profile`, `PATCH /users/me/profile/image`, `GET /profiles/exists` | **2026-03-07 이미지 업로드 구현 완료** — AWS S3 |
| 언어 | `POST/GET/PUT/DELETE /users/me/languages` | |
| 관심사 | `POST/GET/DELETE /users/me/interests` | **2026-03-06 구현 완료** — `GET /users/me` 응답에 포함 |
| 매칭 | `POST /match/start,cancel`, `GET /match/status,result/{id}` | Redis 필요 (@ConditionalOnProperty) |
| 인증 (소셜) | `POST /auth/social-login` | **2026-03-07 Google OAuth ID Token 검증 구현** — User 자동 생성 포함 |
| 통화 | `POST /call/end`, `POST /call/again`, `POST /call/request/{friendId}` | **2026-03-06 친구 통화 요청 추가** |
| 신고 | `POST /reports` | **2026-03-06 구현 완료** — 자기 신고/중복 신고 방지 |
| 친구 | `GET /friends`, `GET /friends/requests`, `POST /friends/request`, `PATCH /friends/request/{id}/accept`, `PATCH /friends/request/{id}/reject` | **2026-03-06 구현 완료** |
| 스케줄러 | CallSessionScheduler — 5분 초과 자동 종료 + 대기열 만료 정리 | **2026-03-06 구현 확인 완료** |
| 소켓 매칭 | `match:start`, `match:cancel` on / `match:success`, `match:error`, `match:cancelled` emit | |
| WebRTC 시그널링 | `webrtc:join/offer/answer/ice` 핸들러 + `match:success`에 `isOfferer` 추가 | **2026-03-06 구현 완료** |
| 푸시 알림 | `POST/DELETE /users/me/device-token` | **2026-03-09 FCM 구현 완료** — 친구 요청/수락/통화 요청 트리거 포함 |

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
| `notification/service/FcmService.java` | 토큰 등록/삭제 + `@Async` FCM 전송 + UNREGISTERED 토큰 자동 삭제. `@Autowired(required=false)` 로 FirebaseApp null 처리 |
| `notification/dto/DeviceTokenRequest.java` | `{ token, platform }` — platform은 "android"\|"ios" 패턴 검증 |
| `notification/controller/DeviceTokenController.java` | `POST/DELETE /users/me/device-token` |
| `friend/service/FriendService.java` | sendFriendRequest(), acceptFriendRequest() 완료 후 FCM 비동기 전송 |
| `call/service/CallService.java` | requestCallToFriend() 완료 후 FCM 비동기 전송. ProfileRepository + FcmService 추가 주입 |
| `user/controller/AuthController.java` | logout 시 fcmService.deleteAllTokens() 연동. UserContext 추가 주입 |

**알림 이벤트:**
| 이벤트 | 트리거 | title | body |
|--------|--------|-------|------|
| 친구 요청 | FriendService.sendFriendRequest() | "친구 요청" | "{nickname}님이 친구 요청을 보냈어요" |
| 친구 수락 | FriendService.acceptFriendRequest() | "친구 수락" | "{nickname}님이 친구 요청을 수락했어요" |
| 통화 요청 | CallService.requestCallToFriend() | "통화 요청" | "{nickname}님이 통화를 요청했어요" |

> FCM 전송 실패는 비즈니스 로직에 영향 없음 (로그만 기록). 로컬 개발 시 FIREBASE_SERVICE_ACCOUNT_JSON 없이 정상 동작.

### 백엔드 미구현 항목 ❌

현재 모든 백엔드 기능이 구현 완료되었습니다. 추가 요구사항이 생기면 이 섹션에 기록합니다.
