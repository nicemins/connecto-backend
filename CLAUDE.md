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
│   └── service/AuthService.java          # authenticate, generateToken, refreshAccessToken
├── call/
│   ├── controller/CallController.java    # POST /call/end, /call/again
│   ├── dto/CallEndRequest.java
│   ├── dto/CallAgainRequest.java
│   └── service/CallService.java
├── common/
│   ├── config/AsyncConfig.java
│   ├── config/PasswordEncoderConfig.java
│   ├── config/SwaggerConfig.java
│   ├── config/WebConfig.java             # CORS 설정
│   ├── context/UserContext.java          # ThreadLocal userId
│   ├── exception/                        # Business, Duplicate, Forbidden, Lock, MaxLimit, NotFound, Unauthorized
│   ├── response/ApiResponse.java         # 공통 응답 래퍼
│   └── response/ErrorCode.java
├── health/
│   └── HealthController.java             # GET /health
├── language/
│   ├── controller/LanguageController.java # POST /users/me/languages
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
│   ├── domain/CallSessionStatus.java     # ACTIVE, ENDED
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
└── user/
    ├── controller/AuthController.java    # POST /auth/signup, /login, /refresh, /logout
    ├── controller/UserController.java    # GET /users/me, /users/exists/email
    ├── domain/User.java
    ├── domain/UserStatus.java            # ACTIVE, BLOCKED
    ├── dto/ (Login, UserCreate, UserMe, UserResponse, Availability...)
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

**refreshToken 쿠키 설정:**
```
HttpOnly=true, Secure=true, SameSite=Strict, Path=/
```

### 5.2 사용자 (`/users`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| GET | `/users/me` | user + profile + languages 통합 조회 | O |
| GET | `/users/exists/email?email=` | 이메일 중복 확인 | X |

### 5.3 프로필 (`/users/me/profile`, `/profiles`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/users/me/profile` | 프로필 최초 생성 | O |
| GET | `/users/me/profile` | 내 프로필 조회 | O |
| PATCH | `/users/me/profile` | 프로필 수정 (nickname, bio) | O |
| PATCH | `/users/me/profile/image` | 이미지 수정 (multipart/form-data) | O |
| GET | `/profiles/exists?nickname=` | 닉네임 중복 확인 | X |

### 5.4 언어 / 관심사
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/users/me/languages` | 언어 저장 (NATIVE/LEARNING) | O |
| POST | `/users/me/interests` | 관심사 저장 | O |

### 5.5 매칭 (`/match`) — Redis 필요
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/match/start` | 매칭 대기열 진입 | O |
| POST | `/match/cancel` | 대기 취소 | O |
| GET | `/match/status` | 매칭 상태 (IDLE/MATCHING/MATCHED) | O |
| GET | `/match/result/{sessionId}` | 통화 종료 후 상대 프로필 조회 (권한 필수) | O |

### 5.6 통화 (`/call`)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/call/end` | 통화 종료 → 세션 ENDED | O |
| POST | `/call/again` | 재연결 의사 표현 (wantAgain bool) | O |

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
String password        // BCrypt 해시
UserStatus status      // ACTIVE, BLOCKED
LocalDateTime createdAt
LocalDateTime deletedAt  // soft delete
```

### Profile
```java
Long id
User user              // @OneToOne
String nickname
String profileImageUrl
String bio
```

### Language
```java
Long id
User user              // @ManyToOne
String languageCode    // ISO: "ko", "en", "ja", "zh", "es", "fr", "de"
LanguageType type      // NATIVE, LEARNING
LanguageLevel level    // BEGINNER, INTERMEDIATE, ADVANCED, NATIVE
```

### CallSession
```java
Long id
User user1
User user2
CallSessionStatus status  // WAITING, IN_PROGRESS, ENDED
String webrtcChannelId
boolean user1WantAgain
boolean user2WantAgain
LocalDateTime startedAt
LocalDateTime endedAt
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

### 빠른 시작 (Redis 없이)
```bash
./gradlew bootRun
# H2 콘솔: http://localhost:8080/h2-console
# Swagger: http://localhost:8080/swagger-ui.html
# 매칭 API는 비활성화됨 (Redis 필요)
```

### Redis 포함 전체 실행
```bash
docker-compose up -d   # Redis 실행
./gradlew bootRun
```

### 테스트
```bash
./gradlew test
```

---

## 11. 구현 현황 (항상 최신 유지)

> **마지막 업데이트:** 2026-03-07 (소셜 로그인 구현 완료)

### 백엔드 완료 ✅

| 도메인 | 엔드포인트 | 비고 |
|--------|-----------|------|
| 인증 | `POST /auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout` | refreshToken HttpOnly 쿠키 |
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

### 프로필 이미지 업로드 세부 사항 (2026-03-07)

| 파일 | 역할 |
|------|------|
| `common/config/S3Config.java` | AWS SDK v2 `S3Client` 빈 등록 |
| `common/service/S3Service.java` | S3 upload / delete / extractKey |
| `profile/service/ProfileService.updateProfileImage()` | 이미지 업로드 + 이전 이미지 삭제 + DB 업데이트 |
| `user/controller/UserController` — `PATCH /users/me/profile/image` | multipart/form-data 수신, 5MB 제한, JPEG/PNG/WEBP만 허용 |

**운영 필수 환경변수:**
```
AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_S3_BUCKET, AWS_REGION
```

### 백엔드 미구현 항목 ❌

현재 모든 백엔드 기능이 구현 완료되었습니다. 추가 요구사항이 생기면 이 섹션에 기록합니다.
