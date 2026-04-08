# CLAUDE.md — Connecto 백엔드 AI 개발 컨텍스트

> 이 파일은 코드·Swagger·파일시스템에서 읽을 수 없는 맥락과 규칙만 담습니다.
> 프론트엔드 컨텍스트: `C:\connecto-app\CLAUDE.md`
> API 전체 목록: `http://localhost:8080/swagger-ui.html`
> 원본 스펙 시트: https://docs.google.com/spreadsheets/d/1lawKmS9PrXNMYv2US2IXgCX-SejIFrBuvuZI9DfssRU

---

## 1. 프로젝트 개요

5분 익명 보이스 채팅 기반 실시간 매칭·언어 교환 플랫폼.

| 항목 | 내용 |
|------|------|
| Package | `com.pm.connecto` |
| Java | 17 / Spring Boot 3.5.9 / Gradle |
| REST API | `localhost:8080` |
| Socket.IO | `localhost:9092` (netty-socketio) |
| DB | PostgreSQL `connecto` @ `localhost:5432` |
| Cache | Redis `localhost:6379` (Lettuce pool + Redisson 분산 락) |

---

## 2. 아키텍처 결정 사항

### 인증 — Spring Security 없음
커스텀 JWT 필터만 사용. `UserContext`(ThreadLocal)로 userId를 Controller에 주입.

```
요청 → JwtAuthenticationFilter → JwtTokenProvider.validateToken()
      → UserContext.setUserId(userId) → Controller.userContext.getUserId()
```

- **Controller에서 `@RequestHeader`로 userId 받는 것 금지** — 반드시 `UserContext` 사용
- 401 조건: 토큰 없음 / 만료 / 서명 불일치

인증 불필요 경로: `/auth/**`, `/users/exists/email`, `/profiles/exists`, `/health`, `/swagger-ui/**`, `/v3/api-docs/**`

### Redis 조건부 활성화
`@ConditionalOnProperty(spring.data.redis.host)` — Redis 없으면 `MatchController`, `SocketIOConfig` 비활성화.
매칭 기능 없이도 서버 기동 가능 (로컬 개발 편의).

### Socket.IO 인증
netty-socketio는 `socket.auth` 미지원 → `Authorization: Bearer <token>` 헤더 **또는** `?token=` URL 파라미터로 JWT 전달.

### emitToUser 패턴
`MatchSocketHandler`가 `clientUserIdMap`(socketId ↔ userId) 을 단독 관리.
외부 서비스(CallService, ChatSocketHandler 등)는 직접 소켓에 접근하지 않고 반드시 `MatchSocketHandler.emitToUser(userId, event, data)` 를 호출.

---

## 3. 비즈니스 규칙 (코드만 봐서는 이유를 알기 어려운 것들)

### 채팅 읽음 처리
`ChatRoomMember.lastReadMessageId`는 **MAX 보존** — `messageId > current` 일 때만 업데이트 (`updateLastRead()` 메서드).
소켓 역방향 전송 없이 클라이언트가 임의로 줄이는 것을 방지.

`chat:read` 소켓 이벤트는 아래 **세 곳**에서 상대방에게 emit:
1. `chat:read` on (소켓)
2. `chat:join` on (소켓)
3. REST `GET /chat/rooms/{id}/messages?page=0` (첫 페이지 자동 읽음)
4. REST `PATCH /chat/rooms/{id}/read`

`chat:receive`는 **발신자 포함** 룸 전체 브로드캐스트 (`io.in("chat:{roomId}")`) — 발신자 제외 아님.

### 이미지 메시지
- `lastMessage` 표시: `"사진"` 고정 문자열
- `ChatMessageResponse`: IMAGE 타입이면 `content = null`, `imageUrl` 포함 / TEXT 타입이면 반대
- 업로드 제한: 5MB, JPEG/PNG/WEBP

### 차단 정책
차단 시 기존 채팅방은 유지, 단 메시지 전송 시 403 `MESSAGE_BLOCKED`.
차단된 유저와는 친구 요청 불가, 매칭 큐에서 자동 제외.

### Interest DTO 필드명
`tag` — `category` 아님. (DTO와 DB 컬럼명이 `tag`임을 명시)

### refreshToken 쿠키
`HttpOnly=true, Secure=true, SameSite=Strict, Path=/` — 로그아웃 시 `maxAge=0`으로 삭제.

### FCM (Firebase)
`FcmConfig`는 nullable — `FIREBASE_SERVICE_ACCOUNT_JSON` 미설정 시 FCM 빈 등록 안 함, 로컬 개발 OK.

### Rate Limit
`AuthRateLimitInterceptor` (Redis 기반, IP 단위):
- login/social-login: 분당 10회, 차단 후 60초 자동 해제
- signup: 시간당 5회, 차단 후 3600초 자동 해제
- **에뮬레이터 주의**: 로컬에서 모든 기기가 `127.0.0.1` → 수동 해제: `redis-cli DEL rate:login:127.0.0.1`

---

## 4. 개발 규칙

- **레이어**: Controller → Service → Repository 엄수
- **예외**: `common/exception/` 하위 클래스 사용 → `GlobalExceptionHandler` 자동 처리
- **응답**: 항상 `ApiResponse.success(data)` 래핑
- **HTTP 상태**: 생성 201, 삭제/로그아웃 204, 중복 409, 인증 실패 401, 권한 없음 403

**커밋 타입:** `feat` / `fix` / `refactor` / `chore` / `test` / `docs`

---

## 5. 로컬 개발 환경

```bash
# Git Bash에서 실행 (PowerShell CRLF 오류 발생)
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.16.8-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

docker-compose up -d   # Redis (매칭 기능 필요 시)
bash run-local.sh      # .env.local 자동 로드 → bootRun (dev 프로파일)
```

**`.env.local` 필수 항목:**
```env
DB_PASSWORD=<postgres 비밀번호>
GOOGLE_ANDROID_CLIENT_ID=...
GOOGLE_WEB_CLIENT_ID=...
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
AWS_S3_BUCKET=connecto-dev
AWS_REGION=ap-northeast-2
FIREBASE_SERVICE_ACCOUNT_JSON=...   # 비워도 FCM만 비활성
```

PostgreSQL: `localhost:5432` / DB `connecto` / user `postgres`

```bash
./gradlew test
```
