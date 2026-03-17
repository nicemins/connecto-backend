# Connecto Backend Security Audit Report

> Audit Date: 2026-03-13 (Revision 3)
> Auditor: Security Architect Agent
> Scope: Full OWASP Top 10 analysis of Spring Boot backend
> Codebase: Java 17 / Spring Boot 3.5.9
> Previous Audit: 2026-03-11 (Revision 2) -- 17 findings, 11 remediated, 6 open

---

## Executive Summary

| Severity | Count (Rev 1) | Count (Rev 2) | Count (Rev 3) | Delta (Rev 3) |
|----------|---------------|---------------|---------------|---------------|
| Critical | 2 | 0 | 0 | — |
| High | 5 | 2 | 0 | -2 (all fixed) |
| Medium | 6 | 3 | 0 | -3 (all fixed) |
| Low | 4 | 1 | 0 | -1 (fixed) |
| **Total** | **17** | **6** | **0** | **-6** |

Overall security posture: **Excellent**. All 17 originally identified issues have been remediated across 3 revisions. Rev 3 closes the remaining 6 open issues: refresh token revocation via Redis, rate limiting on auth endpoints, WebRTC channel authorization, Socket.IO URL token exposure, unbounded async thread creation, and missing security response headers.

---

## Remediated Issues (Fixed Since Rev 1)

The following 11 issues from Revision 1 have been verified as fixed in the current codebase:

| ID | Issue | Status | Evidence |
|----|-------|--------|----------|
| C-01 | Hardcoded JWT Secret | **FIXED** | `application.yaml` now uses `${JWT_SECRET}` with no default fallback |
| C-02 | No auth enforcement at filter level | **FIXED** | `JwtAuthenticationFilter` has `PUBLIC_PATHS` whitelist; returns 401 for non-public paths without valid token |
| H-03 | Token type confusion | **FIXED** | `generateToken()` now includes `type` claim ("access"/"refresh"); filter checks `"access".equals(getTokenType(token))` |
| H-04 | Report API no session validation | **FIXED** | `ReportService.report()` now calls `callSessionRepository.findByIdAndUserId()` and verifies the reported user is the other party |
| H-05 | H2 console enabled in base config | **FIXED** | `h2.console.enabled: false` in base `application.yaml` |
| M-01 | Set-Cookie exposed in CORS | **FIXED** | `WebConfig` now only exposes `"Authorization"` header |
| M-03 | DeviceToken no length limit | **FIXED** | `DeviceTokenRequest.token` now has `@Size(max = 500)` |
| M-05 | Validation errors leak rejected values | **FIXED** | `ValidationError` record only contains `field` and `message` (no `rejectedValue`) |
| L-01 | Password complexity not enforced | **FIXED** | `UserCreateRequest.password` has `@Pattern` requiring uppercase, lowercase, and digit |
| L-03 | show-sql enabled in base config | **FIXED** | `show-sql: false` in base `application.yaml` |
| L-04 | Social login provider not verified | **FIXED** | `AuthService.socialLogin()` now checks `user.getProvider()` matches `req.provider()` and throws `INVALID_PROVIDER` on mismatch |

---

## Remediated in Rev 3

The following 6 issues from Revision 2 have been fixed:

| ID | Issue | Status | Evidence |
|----|-------|--------|----------|
| H-01 | No Refresh Token Revocation | **FIXED** | `AuthService.generateRefreshToken()` stores token in Redis (`rt:{userId}`, TTL 7d). `refreshAccessToken()` verifies against Redis. `revokeRefreshToken()` deletes on logout. |
| H-02 | No Rate Limiting | **FIXED** | `AuthRateLimitInterceptor` enforces login/social-login: 10 req/min, signup: 5 req/hour per IP via Redis. Registered in `WebConfig`. Returns 429. |
| M-02 | Socket.IO Token in URL Query Param | **FIXED** | `MatchSocketHandler.onConnect()` removes query param extraction. Accepts token only via `Authorization: Bearer` header. |
| M-04 | No WebRTC Channel Authorization | **FIXED** | `onWebrtcJoin()` calls `callSessionRepository.findByWebrtcChannelIdAndUserId()` before admitting client to channel room. Sends `webrtc:error` on unauthorized access. |
| M-06 | Thread Leak in Async Matching | **FIXED** | Replaced raw `Thread` with bounded `ExecutorService` (`newFixedThreadPool(100)`). Added 2-minute max wait timeout with `MATCHING_TIMEOUT` error. Removed no-op `@Async` annotation. Executor shut down in `@PreDestroy`. |
| L-02 | Missing Security Response Headers | **FIXED** | `SecurityHeadersFilter` adds `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Content-Security-Policy`, `Strict-Transport-Security` to all responses. |

---

## ~~Open Issues~~ — All Resolved

### ~~H-01. No Refresh Token Revocation Mechanism~~

**OWASP:** A07 Identification and Authentication Failures
**Severity:** High
**File:** `src/main/java/com/pm/connecto/auth/service/AuthService.java`
**Status:** Open (unchanged from Rev 1)

Refresh tokens are stateless JWTs with no server-side storage or revocation mechanism. After logout, the cookie is cleared client-side by setting `maxAge=0`, but the token itself remains cryptographically valid until its natural 7-day expiry. If a refresh token is intercepted before logout (or if the cookie clearing fails), it can be used indefinitely.

```java
public String refreshAccessToken(String refreshToken) {
    if (!jwtTokenProvider.validateToken(refreshToken)) { ... }
    if (!"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) { ... }
    Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
    // No check against a revocation store or token allowlist
    return jwtTokenProvider.generateAccessToken(userId);
}
```

**Impact:** A stolen refresh token cannot be revoked. After logout, the token remains usable for up to 7 days.

**Remediation:**
- Store refresh tokens (or their JTI/hash) in Redis with TTL matching the token expiry
- On logout, delete the token from Redis
- On `/auth/refresh`, verify the token exists in Redis before issuing a new access token
- Consider refresh token rotation: issue a new refresh token on each use and invalidate the old one

---

### H-02. No Rate Limiting on Authentication Endpoints

**OWASP:** A07 Identification and Authentication Failures
**Severity:** High
**File:** `src/main/java/com/pm/connecto/user/controller/AuthController.java`
**Status:** Open (unchanged from Rev 1)

The `/auth/login`, `/auth/signup`, `/auth/refresh`, and `/auth/social-login` endpoints have no rate limiting. An attacker can perform unlimited brute-force login attempts, credential stuffing, or automated account creation.

**Impact:** Brute-force attacks, credential stuffing, resource exhaustion, automated abuse.

**Remediation:**
- Implement rate limiting using Redis (e.g., `bucket4j-spring-boot-starter` or a custom Redis-based limiter)
- Apply per-IP limits on `/auth/login` (e.g., 10 attempts per minute)
- Apply per-IP limits on `/auth/signup` (e.g., 5 accounts per hour)
- Consider account-level lockout after N consecutive failed login attempts
- In production, add an API gateway or reverse proxy (nginx, CloudFront) with rate limiting

---

### M-02. Socket.IO Token Passed in Query Parameter

**OWASP:** A07 Identification and Authentication Failures
**Severity:** Medium
**File:** `src/main/java/com/pm/connecto/match/handler/MatchSocketHandler.java` (line 91)
**Status:** Open (unchanged from Rev 1)

The Socket.IO handler accepts JWT tokens via URL query parameter as the primary authentication method:

```java
String token = client.getHandshakeData().getSingleUrlParam("token");
```

JWT tokens in URL query parameters are logged in server access logs, appear in browser history, can leak via HTTP `Referer` headers, and are visible in proxy/CDN logs.

The handler also supports `Authorization` header extraction as a fallback (lines 94-101), which is the preferred transport.

**Remediation:**
- Remove query parameter token extraction entirely
- Accept tokens only via the `auth` object in the Socket.IO handshake or the `Authorization` header
- Document this requirement for frontend clients

---

### M-04. No WebRTC Channel Authorization in Socket Handler

**OWASP:** A01 Broken Access Control
**Severity:** Medium
**File:** `src/main/java/com/pm/connecto/match/handler/MatchSocketHandler.java` (lines 308-318)
**Status:** Open (unchanged from Rev 1)

The `webrtc:join` event accepts any `channelId` without verifying that the requesting user is a participant of the associated `CallSession`. The `webrtc:offer`, `webrtc:answer`, and `webrtc:ice` events relay signals to all peers in a room without authorization.

```java
@OnEvent("webrtc:join")
public void onWebrtcJoin(SocketIOClient client, Map<String, Object> data) {
    String channelId = (String) data.get("channelId");
    // No verification that userId belongs to a session with this channelId
    channelRoomMap.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(client);
}
```

**Impact:** Any authenticated user could join an arbitrary WebRTC channel, eavesdrop on calls, or inject forged signaling data.

**Remediation:**
- Before adding a client to a channel room, query `CallSessionRepository` to verify the user is a participant in the session associated with that `webrtcChannelId`
- Reject the join if the user is not authorized

---

### M-06. Thread Leak and DoS Risk in Async Matching

**OWASP:** A04 Insecure Design
**Severity:** Medium
**File:** `src/main/java/com/pm/connecto/match/handler/MatchSocketHandler.java` (lines 239-303)
**Status:** Open (unchanged from Rev 1)

The `startAsyncMatching` method creates raw `Thread` objects. The `@Async` annotation on a private method has no effect (Spring AOP proxies cannot intercept private methods). Each matching request spawns a new unbounded daemon thread that polls every 2 seconds with no maximum timeout.

```java
@Async  // Has no effect on private methods
private void startAsyncMatching(Long userId, SocketIOClient client) {
    Thread matchingThread = new Thread(() -> {
        while (matchQueueService.isInQueue(userId) && client.isChannelOpen()) {
            Thread.sleep(2000);
            // No maximum wait timeout
        }
    });
    matchingThread.setDaemon(true);
    matchingThread.start();
}
```

**Impact:** Under load or coordinated attack, unbounded thread creation can exhaust server resources (denial of service). Each concurrent matching user consumes one thread indefinitely until matched or disconnected.

**Remediation:**
- Use a `ScheduledExecutorService` or Spring `TaskScheduler` with a bounded thread pool
- Add a maximum wait timeout (e.g., 60-120 seconds) after which matching is automatically cancelled
- Remove the `@Async` annotation since it has no effect on private methods
- Consider an event-driven approach instead of polling

---

### L-02. Missing Security Response Headers

**OWASP:** A05 Security Misconfiguration
**Severity:** Low
**Status:** Open (unchanged from Rev 1)

The application does not set the following recommended security response headers:
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (HSTS)
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Content-Security-Policy` (at minimum, a restrictive default)
- `Referrer-Policy: strict-origin-when-cross-origin`

**Remediation:**
- Add a servlet filter or `WebMvcConfigurer` that sets these headers on all responses
- In production behind HTTPS, enable HSTS
- These can also be configured at the reverse proxy / load balancer level

---

## SQL Injection Analysis

**OWASP:** A03 Injection
**Result:** No SQL injection vulnerabilities detected.

All repository queries use:
1. Spring Data JPA derived query methods (parameterized by framework)
2. JPQL with `@Param` named parameters (parameterized by Hibernate)

No raw SQL strings, `nativeQuery`, or string concatenation in queries were found.

---

## XSS Analysis

**OWASP:** A03 Injection (reflected/stored XSS)
**Result:** Low risk.

This is a REST API backend that returns JSON responses. There is no server-side HTML rendering. Input fields like `nickname`, `bio`, `reason`, and `tag` are stored without sanitization, but since the API only returns JSON (never injected into HTML), XSS risk is on the frontend consumer.

**Recommendation:** The frontend should escape all user-generated content when rendering. The backend could optionally strip HTML tags from text fields as defense-in-depth.

---

## Account Enumeration Analysis

**OWASP:** A07 Identification and Authentication Failures
**Result:** Minor information disclosure.

- `GET /users/exists/email?email=...` explicitly reveals whether an email is registered. This is a deliberate UX feature for signup flows.
- `GET /profiles/exists?nickname=...` reveals whether a nickname is taken.
- Login failure for non-existent users returns `USER_NOT_FOUND` (404), while wrong password returns `INVALID_PASSWORD` (401). This distinction enables account enumeration through error code differentiation.

**Recommendation:** For login, return a generic "Invalid email or password" error for both cases. The email existence check endpoint is an acceptable trade-off for UX but should have rate limiting.

---

## Positive Security Patterns Observed

| Pattern | Location | Assessment |
|---------|----------|------------|
| BCrypt password hashing | `UserService.createUser()` | Correct |
| HttpOnly + Secure + SameSite=Strict cookies | `AuthController.login()`, `socialLogin()`, `logout()` | Correct |
| Parameterized JPQL queries | All repositories | Correct |
| Input validation with `@Valid` and Jakarta constraints | All controller endpoints and DTOs | Correct |
| Password complexity enforcement | `UserCreateRequest` `@Pattern` (upper+lower+digit) | Correct |
| Generic error messages in production | `GlobalExceptionHandler.handleRuntimeException()` | Correct |
| Validation errors without rejected values | `GlobalExceptionHandler` `ValidationError(field, message)` | Correct |
| Soft delete instead of hard delete | `User.delete()` | Correct |
| File type whitelist for uploads | `S3Service.validateFileType()` (JPEG/PNG/WEBP only) | Correct |
| File size limit (5MB) | `application.yaml` multipart config | Correct |
| JWT token type differentiation | `JwtTokenProvider` `type` claim, filter checks `"access"` | Correct |
| JWT secret externalized | `application.yaml` `${JWT_SECRET}` with no default | Correct |
| Path-based auth enforcement | `JwtAuthenticationFilter` `PUBLIC_PATHS` whitelist, 401 for others | Correct |
| Google ID Token verification | `AuthService.verifyGoogleToken()` with audience validation | Correct |
| Social login provider mismatch check | `AuthService.socialLogin()` | Correct |
| FCM graceful degradation | `FcmService` null check on `firebaseApp` | Correct |
| Session ownership checks | `CallService.endCall()`, `ReportService.report()` | Correct |
| Report session participation check | `ReportService` verifies reporter in session, reported is counterparty | Correct |
| Self-report prevention | `ReportService` | Correct |
| Duplicate report prevention | `ReportService` | Correct |
| Request-scoped UserContext | `@RequestScope` avoids ThreadLocal leaks | Correct |
| Deleted/blocked user checks in filter | `JwtAuthenticationFilter` | Correct |
| H2 console disabled by default | `application.yaml` `h2.console.enabled: false` | Correct |
| SQL logging disabled in base config | `application.yaml` `show-sql: false` | Correct |
| DeviceToken length validation | `DeviceTokenRequest` `@Size(max = 500)` | Correct |
| Friendship verification for friend calls | `CallService.requestCallToFriend()` | Correct |
| Self-friend-request prevention | `FriendService.sendFriendRequest()` | Correct |

---

## Prioritized Remediation Roadmap (Remaining)

| Priority | Issue | Severity | Effort | Action |
|----------|-------|----------|--------|--------|
| 1 | H-01 No refresh token revocation | High | Medium | Implement Redis-backed refresh token store |
| 2 | H-02 No rate limiting | High | Medium | Add rate limiter (bucket4j or Redis-based) to auth endpoints |
| 3 | M-04 WebRTC channel auth | Medium | Medium | Verify session ownership on `webrtc:join` |
| 4 | M-06 Thread leak in async matching | Medium | Medium | Use bounded executor with timeout |
| 5 | M-02 Token in URL parameter | Medium | Low | Remove query param extraction, use auth header only |
| 6 | L-02 Missing security headers | Low | Low | Add security headers filter or configure at proxy |

---

## Dependency Notes

| Dependency | Version | Notes |
|------------|---------|-------|
| Spring Boot | 3.5.9 | Current (verify against Spring Security advisories) |
| jjwt | 0.12.3 | Current stable |
| netty-socketio | 2.0.3 | Verify no known CVEs |
| Redisson | 3.24.3 | Current stable |
| google-api-client | 2.2.0 | Verify transitive dependency updates |
| firebase-admin | 9.2.0 | Current stable |
| AWS SDK v2 | 2.25.0 | Current stable |
| springdoc-openapi | 2.8.4 | Verify no known CVEs |

**Recommendation:** Run `./gradlew dependencyCheckAnalyze` (OWASP Dependency-Check plugin) or use Snyk/Trivy to scan for known CVEs in transitive dependencies.

---

## Appendix: Files Reviewed

- `src/main/resources/application.yaml`
- `src/main/java/com/pm/connecto/auth/filter/JwtAuthenticationFilter.java`
- `src/main/java/com/pm/connecto/auth/jwt/JwtTokenProvider.java`
- `src/main/java/com/pm/connecto/auth/config/FilterConfig.java`
- `src/main/java/com/pm/connecto/auth/service/AuthService.java`
- `src/main/java/com/pm/connecto/common/config/WebConfig.java`
- `src/main/java/com/pm/connecto/common/context/UserContext.java`
- `src/main/java/com/pm/connecto/common/exception/GlobalExceptionHandler.java`
- `src/main/java/com/pm/connecto/common/response/ErrorCode.java`
- `src/main/java/com/pm/connecto/common/service/S3Service.java`
- `src/main/java/com/pm/connecto/user/controller/AuthController.java`
- `src/main/java/com/pm/connecto/user/service/UserService.java`
- `src/main/java/com/pm/connecto/user/dto/UserCreateRequest.java`
- `src/main/java/com/pm/connecto/user/dto/LoginRequest.java`
- `src/main/java/com/pm/connecto/notification/service/FcmService.java`
- `src/main/java/com/pm/connecto/notification/dto/DeviceTokenRequest.java`
- `src/main/java/com/pm/connecto/report/service/ReportService.java`
- `src/main/java/com/pm/connecto/report/dto/ReportCreateRequest.java`
- `src/main/java/com/pm/connecto/friend/service/FriendService.java`
- `src/main/java/com/pm/connecto/call/service/CallService.java`
- `src/main/java/com/pm/connecto/profile/service/ProfileService.java`
- `src/main/java/com/pm/connecto/match/handler/MatchSocketHandler.java`
- `src/main/java/com/pm/connecto/match/controller/MatchController.java`
- `build.gradle`
