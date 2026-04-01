# Security Implementation Gap Analysis Report

> **Analysis Type**: Design vs Implementation Gap Analysis (OWASP Security Audit Rev 3)
>
> **Project**: Connecto Backend
> **Version**: Spring Boot 3.5.9 / Java 17
> **Analyst**: gap-detector
> **Date**: 2026-03-13
> **Design Doc**: [security-spec.md](../02-design/security-spec.md)

---

## 1. Analysis Overview

### 1.1 Analysis Purpose

Verify that the 6 security issues marked as "FIXED" in the OWASP Security Audit Revision 3 (`security-spec.md`) are actually implemented in the codebase.

### 1.2 Analysis Scope

| Issue ID | Severity | Description | Design Location | Implementation Files |
|----------|----------|-------------|-----------------|---------------------|
| H-01 | High | Refresh Token Revocation | security-spec.md:51 | AuthService.java, AuthController.java |
| H-02 | High | Rate Limiting | security-spec.md:52 | AuthRateLimitInterceptor.java, WebConfig.java |
| M-02 | Medium | Socket.IO Token URL Param | security-spec.md:53 | MatchSocketHandler.java |
| M-04 | Medium | WebRTC Channel Authorization | security-spec.md:54 | MatchSocketHandler.java, CallSessionRepository.java |
| M-06 | Medium | Thread Leak Fix | security-spec.md:55 | MatchSocketHandler.java |
| L-02 | Low | Security Headers | security-spec.md:56 | SecurityHeadersFilter.java |

---

## 2. Issue-by-Issue Gap Analysis

### H-01. Refresh Token Revocation -- MATCH

| Requirement | Design | Implementation | Status |
|-------------|--------|----------------|--------|
| Redis key format | `rt:{userId}` | `REFRESH_TOKEN_KEY_PREFIX = "rt:"` + userId | MATCH |
| TTL | 7 days | `Duration.ofMillis(jwtTokenProvider.getRefreshExpiration())` = 604800000ms = 7d | MATCH |
| Store on generate | `generateRefreshToken()` writes to Redis | Lines 104-114: `redisTemplate.opsForValue().set(...)` | MATCH |
| Verify on refresh | `refreshAccessToken()` checks Redis | Lines 142-147: `redisTemplate.opsForValue().get(...)`, equality check | MATCH |
| Delete on logout | `revokeRefreshToken()` deletes from Redis | Line 118: `redisTemplate.delete(...)` | MATCH |
| Logout calls revoke | `AuthController.logout()` calls `revokeRefreshToken()` | AuthController.java line 139: `authService.revokeRefreshToken(userId)` | MATCH |
| Redis-absent fallback | Graceful degradation | `@Autowired(required = false)` + null checks | MATCH |

**Verdict: MATCH (7/7 requirements satisfied)**

---

### H-02. Rate Limiting -- MATCH

| Requirement | Design | Implementation | Status |
|-------------|--------|----------------|--------|
| Interceptor class | `AuthRateLimitInterceptor` | `auth/interceptor/AuthRateLimitInterceptor.java` exists | MATCH |
| Login limit | 10 req/60s per IP | `checkLimit(ip, "login", 10, 60, response)` | MATCH |
| Social-login limit | 10 req/60s per IP | Same branch as login: `/auth/social-login` | MATCH |
| Signup limit | 5 req/3600s per IP | `checkLimit(ip, "signup", 5, 3600, response)` | MATCH |
| Redis-based | Redis counter with TTL | `redisTemplate.opsForValue().increment(key)` + `expire()` | MATCH |
| 429 response | HTTP 429 on exceed | `response.setStatus(429)` | MATCH |
| WebConfig registration | Interceptor registered in WebConfig | WebConfig.java lines 34-36: `addInterceptor(authRateLimitInterceptor).addPathPatterns(...)` | MATCH |
| Path patterns | `/auth/login`, `/auth/signup`, `/auth/social-login` | `addPathPatterns("/auth/login", "/auth/signup", "/auth/social-login")` | MATCH |

**Verdict: MATCH (8/8 requirements satisfied)**

---

### M-02. Socket.IO Token URL Param Removal -- MATCH

| Requirement | Design | Implementation | Status |
|-------------|--------|----------------|--------|
| Remove query param extraction | No `getSingleUrlParam("token")` | Not present in MatchSocketHandler.java | MATCH |
| Authorization header only | `Authorization: Bearer` header extraction | Lines 100-106: header-based extraction only | MATCH |
| Disconnect on missing token | Reject unauthenticated connections | Lines 108-112: `client.disconnect()` | MATCH |

**Verdict: MATCH (3/3 requirements satisfied)**

---

### M-04. WebRTC Channel Authorization -- MATCH

| Requirement | Design | Implementation | Status |
|-------------|--------|----------------|--------|
| Repository method | `findByWebrtcChannelIdAndUserId()` | CallSessionRepository.java lines 25-26: JPQL query checking both user1 and user2 | MATCH |
| Join verification | Check before admitting to channel room | MatchSocketHandler.java lines 330-334: `callSessionRepository.findByWebrtcChannelIdAndUserId()` | MATCH |
| Error on unauthorized | Send `webrtc:error` | Line 332: `client.sendEvent("webrtc:error", ...)` | MATCH |
| Reject non-participant | Return early without adding to room | Lines 330-334: `if (isEmpty())` returns before `channelRoomMap.computeIfAbsent()` | MATCH |

**Verdict: MATCH (4/4 requirements satisfied)**

---

### M-06. Thread Leak Fix -- MATCH

| Requirement | Design | Implementation | Status |
|-------------|--------|----------------|--------|
| Remove `@Async` | No `@Async` annotation | `startAsyncMatching` is private, no annotation | MATCH |
| Bounded ExecutorService | `newFixedThreadPool(100)` | Line 61: `Executors.newFixedThreadPool(100)` | MATCH |
| Max wait timeout | 120 seconds (2 minutes) | Line 52: `MAX_WAIT_MS = 120_000L`, line 247: timeout check | MATCH |
| Timeout error event | `MATCHING_TIMEOUT` error on expire | Lines 249-254: `sendEvent("match:error", ..."MATCHING_TIMEOUT"...)` | MATCH |
| `@PreDestroy` shutdown | Executor shutdown on destroy | Lines 86-87: `matchingExecutor.shutdown()` | MATCH |
| Submit via executor | `matchingExecutor.submit()` not raw Thread | Line 243: `matchingExecutor.submit(() -> { ... })` | MATCH |

**Verdict: MATCH (6/6 requirements satisfied)**

---

### L-02. Security Response Headers -- MATCH

| Requirement | Design | Implementation | Status |
|-------------|--------|----------------|--------|
| Filter class | `SecurityHeadersFilter` | `common/filter/SecurityHeadersFilter.java` exists | MATCH |
| `@Component` | Auto-discovered | Line 28: `@Component` | MATCH |
| `@Order(HIGHEST_PRECEDENCE)` | Runs first | Line 29: `@Order(Ordered.HIGHEST_PRECEDENCE)` | MATCH |
| X-Content-Type-Options | `nosniff` | Line 36: `setHeader("X-Content-Type-Options", "nosniff")` | MATCH |
| X-Frame-Options | `DENY` | Line 37: `setHeader("X-Frame-Options", "DENY")` | MATCH |
| Referrer-Policy | `strict-origin-when-cross-origin` | Line 38: matches | MATCH |
| Content-Security-Policy | Present | Line 39: `default-src 'none'` | MATCH |
| Strict-Transport-Security | HSTS | Line 40: `max-age=31536000; includeSubDomains` | MATCH |

**Verdict: MATCH (8/8 requirements satisfied)**

---

## 3. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| H-01 Refresh Token Revocation | 100% | MATCH |
| H-02 Rate Limiting | 100% | MATCH |
| M-02 Socket.IO Token URL Param | 100% | MATCH |
| M-04 WebRTC Channel Authorization | 100% | MATCH |
| M-06 Thread Leak Fix | 100% | MATCH |
| L-02 Security Headers | 100% | MATCH |
| **Overall Match Rate** | **100%** | **MATCH** |

```
Total requirements checked: 36
Requirements satisfied:      36
Requirements missing:         0
Match Rate:                 100%
```

---

## 4. Missing Features (Design O, Implementation X)

None.

---

## 5. Added Features (Design X, Implementation O)

| Item | Implementation Location | Description |
|------|------------------------|-------------|
| Redis-absent fallback (H-01) | AuthService.java:106 | `@Autowired(required = false)` + null checks allow stateless mode when Redis unavailable |
| Redis-absent fallback (H-02) | AuthRateLimitInterceptor.java:34 | Rate limiting auto-disabled without Redis |
| X-Forwarded-For parsing | AuthRateLimitInterceptor.java:66-69 | Proxy-aware IP extraction (not in design but best practice) |
| User status check in Socket.IO | MatchSocketHandler.java:131-135 | Blocks inactive/blocked users at WebSocket level |

These additions are improvements beyond the design spec and do not require design updates.

---

## 6. Changed Features (Design != Implementation)

None. All implementations precisely match their design specifications.

---

## 7. Recommended Actions

No immediate actions required. All 6 security issues from Rev 3 are correctly implemented.

### Optional Improvements (not gaps)

1. **Refresh token rotation**: Design mentions "Consider refresh token rotation" -- not implemented but was listed as optional.
2. **Account-level lockout**: Design mentions "Consider account-level lockout after N failed attempts" -- not implemented but was listed as optional.
3. **429 response body**: Rate limiter returns a plain JSON string; could use `ApiResponse.error()` for consistency with the global error format.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-03-13 | Initial security gap analysis (Rev 3 scope) | gap-detector |
