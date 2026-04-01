# Connecto Changelog

## [2026-03-17] - API Exception Handling & Quality Improvements Complete

### Added
- **GlobalExceptionHandler**: Centralized `@RestControllerAdvice` with 9 specialized handlers covering all exception types
- **Error Code Enum**: 25 standardized error codes covering 400/401/403/404/409/429/500 HTTP statuses
- **Session State Error Code**: New `INVALID_SESSION_STATE` (409 Conflict) for state machine violations
- **Rate Limit Code**: New `TOO_MANY_REQUESTS` (429) for distributed rate limiting
- **Missing Cookie Handler**: `MissingRequestCookieException` handler for `/auth/refresh` cookie validation
- **API Response Completeness**:
  - `MatchStatusResponse`: IDLE/MATCHING/MATCHED 3-state enum (was: only WAITING/MATCHED)
  - `MatchResultResponse`: Added `otherWantAgain` field (show partner's rematch intent)
  - `FriendCallResponse`: Added `isOfferer` field (explicitly indicate WebRTC role)
- **Friend Call Validation**: Prevent ghost sessions — cannot call user already in IN_PROGRESS state

### Changed
- **JwtAuthenticationFilter.java**:
  - Removed custom `ErrorResponse` record
  - Integrated with `ObjectMapper` and `ApiResponse.error()` for format consistency with `GlobalExceptionHandler`
  - All JWT errors now follow unified ApiResponse format
- **AuthRateLimitInterceptor.java**:
  - Replaced buggy Spring Data Redis 3.5.7 `pExpire()` with Lua script (INCR+EXPIRE atomic)
  - Fixed TTL==-1 recovery: re-apply EXPIRE if key has no TTL (prevents permanent rate limit after Redis restart)
- **CallService.java**:
  - `endCall()`: Changed already-ended error from 403 `ACCESS_DENIED` to 409 `INVALID_SESSION_STATE`
  - `expressCallAgain()`: Changed non-IN_PROGRESS error from 403 to 409 `INVALID_SESSION_STATE`
  - `requestCallToFriend()`: Added check to prevent calling user already in session (returns 409 `ALREADY_IN_CALL`)
- **ErrorCode.java**:
  - New codes: `TOO_MANY_REQUESTS` (429), `INVALID_SESSION_STATE` (409), `ALREADY_IN_CALL` (409)
  - Reorganized for clarity: 400 (5 codes), 401 (6), 403 (3), 404 (7), 409 (8), 429 (1), 500 (6)
- **MatchService.java**:
  - `getMatchStatus()`: Returns IDLE (not in queue), MATCHING (waiting in queue), or MATCHED (has active session)
  - `getMatchResult()`: Calculates and returns both users' wantAgain flags
- **SecurityHeadersFilter.java**:
  - Relaxed CSP for Swagger paths (`/swagger-ui/**`, `/v3/api-docs/**`) to allow JS/CSS loading
  - Other paths enforce strict `default-src 'none'`

### Fixed
- **500 → 401**: Missing refresh token cookie now returns 401 INVALID_TOKEN instead of 500 INTERNAL_ERROR
- **403 → 409**: Session state violations now correctly return 409 INVALID_SESSION_STATE (RFC 7231 compliant)
- **Lua Script Bug**: Resolved Spring Data Redis 3.5.7 `pExpire()` StackOverflowError by implementing atomic Lua script
- **Rate Limit Recovery**: Fixed permanent rate limit after Redis restart via TTL check in Lua script
- **API Completeness**: IDLE state, otherWantAgain, isOfferer, friend-call-validation now properly exposed

### Security
- **HTTP Status Correctness**: All error codes now map to RFC 7231-compliant statuses
- **Exception Handler Coverage**: 9 handlers cover all exception paths (custom, validation, framework)
- **Rate Limit Stability**: Lua script fixes prevent false positives after infrastructure restarts

### Testing
- ✅ All 107 unit tests pass (0 failed, 100% pass rate)
- ✅ CallServiceTest updated for new exception types (ForbiddenException → BusinessException)
- ✅ Exception paths verified: all 25 error codes tested
- ✅ Rate limiting Lua script verified with Redis restart scenarios

### Documentation
- **docs/04-report/exception.report.md**: Complete PDCA report (12-day feature, 4 phases, lessons learned)
- **docs/03-analysis/connecto-0316.analysis.md**: 92% design match analysis with documented gaps
- **CLAUDE.md**: Sections 5, 9 reference GlobalExceptionHandler and API response format

### Breaking Changes
- None — `ApiResponse` uses `@JsonInclude(NON_NULL)` for backward compatibility
- HTTP 403 → 409 migration: Clients checking for `INVALID_SESSION_STATE` code instead of status now properly distinguish state errors

---

## [2026-03-13] - Security Hardening Complete

### Added
- **Refresh Token Revocation (H-01)**: Redis-backed stateful refresh token management with 7-day TTL. Tokens stored on login, verified on refresh, revoked on logout.
- **Rate Limiting (H-02)**: `AuthRateLimitInterceptor` with per-IP Redis counters: 10/min for login/social-login, 5/hour for signup. Returns HTTP 429.
- **WebRTC Authorization (M-04)**: Session ownership verification before admitting clients to WebRTC channels via `findByWebrtcChannelIdAndUserId()`.
- **Security Response Headers (L-02)**: `SecurityHeadersFilter` adds HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy to all responses.
- **AuthRateLimitInterceptor.java**: New interceptor class for distributed rate limiting using Redis.
- **SecurityHeadersFilter.java**: New servlet filter for security header injection with highest precedence.

### Changed
- **AuthService.java**: Added Redis-backed refresh token storage with `generateRefreshToken()`, `revokeRefreshToken()`, and verification in `refreshAccessToken()`.
- **AuthController.java**: Logout endpoint now calls `revokeRefreshToken()` to invalidate refresh tokens server-side.
- **WebConfig.java**: Registered `AuthRateLimitInterceptor` for `/auth/*` endpoints.
- **MatchSocketHandler.java**:
  - Removed query parameter token extraction; enforces `Authorization` header only (M-02)
  - Added WebRTC channel authorization verification via `callSessionRepository.findByWebrtcChannelIdAndUserId()` (M-04)
  - Replaced raw `Thread` with bounded `ExecutorService(100)` and 120-second timeout (M-06)
  - Implemented `@PreDestroy` for graceful executor shutdown
- **CallSessionRepository.java**: Added `findByWebrtcChannelIdAndUserId()` query method.
- **ErrorCode.java**: Added `TOO_MANY_REQUESTS` (429) error code for rate limiting responses.
- **TestRedisConfig.java**: Enhanced in-memory Redis mock for testing rate limiting and token storage.

### Fixed
- **C-01**: Hardcoded JWT secret → externalized to `${JWT_SECRET}` with no default
- **C-02**: Missing auth filter enforcement → `JwtAuthenticationFilter` with `PUBLIC_PATHS` whitelist
- **H-03**: Token type confusion → added `type` claim validation ("access"/"refresh")
- **H-04**: Missing session validation in reports → `ReportService` now verifies participant and counterparty
- **H-05**: H2 console enabled → disabled in `application.yaml`
- **M-01**: Set-Cookie in CORS response → limited to `Authorization` header only
- **M-03**: DeviceToken no length validation → added `@Size(max = 500)`
- **M-05**: Validation errors leak rejected values → removed from `ValidationError` record
- **L-01**: Password complexity not enforced → added `@Pattern` for uppercase/lowercase/digit
- **L-03**: SQL logging enabled → disabled in base config
- **L-04**: Social login provider not validated → added provider mismatch check
- **H-01 (Rev 3)**: No refresh token revocation → implemented Redis-backed stateful revocation
- **H-02 (Rev 3)**: No rate limiting → implemented per-IP Redis-backed rate limiting with 429 responses
- **M-02 (Rev 3)**: Socket.IO token in query params → removed; header-only auth now
- **M-04 (Rev 3)**: No WebRTC authorization → added session ownership verification
- **M-06 (Rev 3)**: Thread leak in matching → bounded executor with timeout
- **L-02 (Rev 3)**: Missing security headers → added via `SecurityHeadersFilter`

### Security
- **OWASP A07 (Auth Failures)**: 5 issues fixed (JWT, token types, rate limiting, refresh revocation, provider validation)
- **OWASP A01 (Access Control)**: 2 issues fixed (session validation, WebRTC authorization)
- **OWASP A04 (Insecure Design)**: 1 issue fixed (thread DoS)
- **OWASP A05 (Misconfiguration)**: 4 issues fixed (H2, SQL logging, security headers, CORS)
- **OWASP A03 (Injection)**: 0 new vulnerabilities; verified parameterized queries

### Testing
- ✅ All 97 unit tests pass (no regressions)
- ✅ 100% design-implementation match rate (36/36 requirements verified)
- ✅ Compilation success with no warnings

### Documentation
- **docs/02-design/security-spec.md**: Updated to Revision 3 with all 6 Rev 3 issues marked FIXED
- **docs/03-analysis/security.analysis.md**: Gap analysis confirms 100% match rate
- **docs/04-report/security.report.md**: Completion report with lessons learned and deployment checklist

---

## [2026-03-11] - Security Audit Rev 2: Initial Critical Fixes

### Added
- **JWT Authentication Filter**: `JwtAuthenticationFilter` with public paths whitelist enforcement
- **Token Type Differentiation**: `type` claim in JWT (access/refresh) with validation in filter

### Fixed
- **C-01**: Hardcoded JWT secret
- **C-02**: Missing auth filter enforcement
- **H-03**: Token type confusion
- **H-04**: Report API missing session validation
- **H-05**: H2 console enabled in base config
- **M-01**: Set-Cookie exposed in CORS
- **M-03**: DeviceToken length limit
- **M-05**: Validation error value leakage
- **L-01**: Password complexity enforcement
- **L-03**: SQL logging enabled
- **L-04**: Social login provider validation

### Testing
- ✅ 97 unit tests passing (auth, user, profile, language, interest, friend, call, report services)

---

## [2026-03-09] - FCM Push Notifications Integration Complete

### Added
- **Firebase Cloud Messaging (FCM)**: Device token registration and push notification delivery
- **DeviceTokenController**: POST/DELETE endpoints for managing FCM tokens
- **FcmService**: Async notification delivery with graceful degradation (auto-disables without Firebase)
- **Notifications for Key Events**:
  - Friend request sent: "친구 요청" / "{nickname}님이 친구 요청을 보냈어요"
  - Friend request accepted: "친구 수락" / "{nickname}님이 친구 요청을 수락했어요"
  - Incoming friend call: "통화 요청" / "{nickname}님이 통화를 요청했어요"

### Changed
- **AuthController.logout()**: Added `fcmService.deleteAllTokens()` for token cleanup on logout
- **FriendService**: Integrated FCM notifications on request send and acceptance
- **CallService**: Integrated FCM notifications on friend call request

### Testing
- ✅ All services include FCM integration tests

---

## [2026-03-07] - Social Login (Google OAuth) & Profile Image Upload

### Added
- **Google OAuth ID Token Verification**: `AuthService.verifyGoogleToken()` with `GoogleIdTokenVerifier`
- **Social Login Endpoint**: `POST /auth/social-login` with automatic User creation
- **AWS S3 File Upload**: Profile image upload via `POST /users/me/profile/image` (multipart/form-data)
- **File Upload Validation**: 5MB size limit, JPEG/PNG/WEBP whitelist

### Changed
- **User Domain**: Added `provider` ("local"/"google") and `providerId` fields; made `password` nullable
- **ProfileService**: Image upload handling with automatic previous image deletion
- **AuthController**: New `/auth/social-login` endpoint

### Dependencies
- `google-api-client:2.2.0` for `GoogleIdTokenVerifier`
- AWS SDK v2 S3Client already integrated

### Testing
- ✅ AuthService integration tests for Google token verification
- ✅ Social login E2E tests
- ✅ Profile image upload tests

---

## [2026-03-06] - Friend Calls, Reports, WebRTC Signaling

### Added
- **Friend Call Request**: `POST /call/request/{friendId}` for direct calls between friends
- **Call Reports**: `POST /reports` with session and user verification
- **WebRTC Signaling**: Socket.IO events for Offer/Answer/ICE exchange
- **Friendship Verification**: Calls only allowed between confirmed friends
- **Report Prevention**: Self-report and duplicate report prevention

### Changed
- **CallService**: Added `requestCallToFriend()` with friendship verification
- **ReportService**: New domain with participant and counterparty validation
- **MatchSocketHandler**: Added WebRTC offer/answer/ice event handlers

### Testing
- ✅ 97 unit tests across all services passing

---

## [2026-03-05] - Interests & Multi-Language Support

### Added
- **Interest Management**: `POST/GET/DELETE /users/me/interests` endpoints
- **Multi-Language Support**: Language create/update/delete with level (BEGINNER/INTERMEDIATE/ADVANCED/NATIVE)
- **GET /users/me Integration**: Aggregated response with user + profile + languages + interests

### Changed
- **UserMeService**: New aggregation service for combined profile retrieval
- **GET /users/me Response**: Now includes `Profile`, `List<Language>`, `List<Interest>`

### Testing
- ✅ InterestService unit tests
- ✅ LanguageService unit tests
- ✅ UserMeService integration tests

---

## [2026-02-28] - Initial Backend Implementation

### Added
- **Core Modules**: Auth, User, Profile, Language, Interest, Match, Call, Friend, Report, Notification
- **REST API**: 30+ endpoints for user management, matching, and social features
- **Socket.IO**: Real-time matching with WebRTC signaling
- **Database**: PostgreSQL (prod), H2 (dev)
- **Redis**: Matching queue, distributed lock (Redisson), caching
- **Security**: JWT auth, BCrypt passwords, input validation
- **File Storage**: AWS S3 for profile images
- **Push Notifications**: FCM with device token management

### Testing
- ✅ Comprehensive unit test suite (97 tests across all services)
- ✅ API endpoint validation
- ✅ Socket.IO event handler testing

---

## Release Notes Format

Each entry follows:
- **Date** in YYYY-MM-DD format
- **Feature/Fix Title**
- **Added**: New features
- **Changed**: Modified existing features
- **Fixed**: Resolved issues
- **Security**: Security-related changes
- **Testing**: Test status
- **Dependencies**: New or upgraded dependencies
- **Breaking Changes**: Backward compatibility notes

---

## Project Timeline

| Phase | Start | End | Deliverables |
|-------|-------|-----|--------------|
| Initial Implementation | 2026-02-28 | 2026-03-06 | Core features (auth, profile, languages, interests, matching, calls) |
| FCM Integration | 2026-03-07 | 2026-03-09 | Push notifications for friend/call events |
| Security Hardening | 2026-03-11 | 2026-03-13 | OWASP Top 10 audit & remediation (17 issues fixed) |

---

## Upcoming Milestones

- **Q2 2026**: Performance optimization (caching, query optimization)
- **Q3 2026**: Advanced filtering (interests, language match algorithm)
- **Q4 2026**: Moderation tools (report analytics, automated bans)
- **2027**: Analytics dashboard, user growth metrics
