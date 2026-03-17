# Design-Implementation Gap Analysis Report

> **Summary**: Connecto backend gap analysis focusing on 2026-03-16 new features (call:ended, call:rematch socket events, friend request FCM text change) and full API/Socket.IO coverage
>
> **Author**: gap-detector
> **Created**: 2026-03-16
> **Last Modified**: 2026-03-16
> **Status**: Approved

---

## Analysis Overview
- Analysis Target: Connecto Backend (full project + 2026-03-16 changes)
- Design Document: CLAUDE.md (Sections 5, 7, 8, 11)
- Implementation Path: src/main/java/com/pm/connecto/
- Analysis Date: 2026-03-16

## Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| API Endpoint Match | 100% | OK |
| Socket.IO Event Match | 82% | WARN |
| Data Model Match | 100% | OK |
| Response Format Match | 90% | WARN |
| FCM Notification Match | 85% | WARN |
| Convention Compliance | 97% | OK |
| **Overall** | **92%** | OK |

---

## 1. API Endpoint Verification (37/37 = 100%)

All 37 endpoints from CLAUDE.md Section 5 are implemented:

| Domain | Endpoints | Implemented | Status |
|--------|:---------:|:-----------:|:------:|
| Auth (/auth) | 5 | 5 | OK |
| User (/users) | 4 | 4 | OK |
| Profile (/users/me/profile, /profiles) | 5 | 5 | OK |
| Language (/users/me/languages) | 4 | 4 | OK |
| Interest (/users/me/interests) | 3 | 3 | OK |
| Match (/match) | 4 | 4 | OK |
| Call (/call) | 3 | 3 | OK |
| Friend (/friends) | 5 | 5 | OK |
| Report (/reports) | 1 | 1 | OK |
| Device Token (/users/me/device-token) | 2 | 2 | OK |
| TURN Credentials (/webrtc/turn-credentials) | 1 | 1 | OK |
| Health (/health) | 1 | 1 | OK |

---

## 2. Socket.IO Event Verification

### CLAUDE.md Section 8 Specification vs Implementation

| Direction | Event | Spec | Impl | Status |
|-----------|-------|:----:|:----:|:------:|
| on | `match:start` | O | O | OK |
| on | `match:cancel` | O | O | OK |
| emit | `match:success` | O | O | OK |
| emit | `match:error` | O | O | OK |
| on | `webrtc:join` | O | O | OK |
| on | `webrtc:offer` | O | O | OK |
| on | `webrtc:answer` | O | O | OK |
| on | `webrtc:ice` | O | O | OK |
| emit | `match:cancelled` | O (Section 11) | O | OK |
| emit | **`call:ended`** | **X** | **O** | MISSING IN SPEC |
| emit | **`call:rematch`** | **X** | **O** | MISSING IN SPEC |
| emit | `webrtc:error` | X | O | MISSING IN SPEC |

### Detailed Findings for New Events (2026-03-16)

#### `call:ended` (Design X, Implementation O)
- **Location**: CallService.java:84-88
- **Trigger**: `endCall()` -- when a user ends a call, emits to the other participant
- **Payload**: `{ sessionId: Long }`
- **Mechanism**: `MatchSocketHandler.emitToUser(otherUserId, "call:ended", data)`
- **Impact**: High -- clients need this event to handle remote hangup

#### `call:rematch` (Design X, Implementation O)
- **Location**: CallService.java:158-170
- **Trigger**: `expressCallAgain()` -- when both users express wantAgain=true, creates new session and emits to both
- **Payload**: `{ sessionId: Long, webrtcChannelId: String, isOfferer: Boolean }`
- **Mechanism**: `MatchSocketHandler.emitToUser()` to both users with different `isOfferer` values
- **Impact**: High -- enables automatic rematch without re-entering the queue

#### `webrtc:error` (Design X, Implementation O)
- **Location**: MatchSocketHandler.java:347
- **Trigger**: Unauthorized WebRTC channel access attempt
- **Payload**: `{ message: String }`

#### `emitToUser` Utility Method (New)
- **Location**: MatchSocketHandler.java:404-420
- **Purpose**: Public method allowing external services (CallService) to emit socket events to specific users by userId
- **Design implication**: This is an infrastructure method not documented in CLAUDE.md

---

## 3. FCM Notification Text Verification

### CLAUDE.md Section 11 vs Implementation

| Event | Spec Title | Spec Body | Impl Title | Impl Body | Match |
|-------|-----------|-----------|------------|-----------|:-----:|
| Friend Request | "친구 요청" | "{nickname}님이 친구 요청을 보냈어요" | "친구 요청" | "{nickname}님이 친구 신청을 보냈습니다" | DIFFER |
| Friend Accept | "친구 수락" | "{nickname}님이 친구 요청을 수락했어요" | "친구 수락" | "{nickname}님이 친구 요청을 수락했어요" | OK |
| Call Request | "통화 요청" | "{nickname}님이 통화를 요청했어요" | "통화 요청" | "{nickname}님이 통화를 요청했어요" | OK |
| Call Again | (Section 11 note) | "{nickname}님이 다시 통화하고 싶어해요" | "다시 통화 요청" | "{nickname}님이 다시 통화하고 싶어합니다" | DIFFER |

**Details**:

1. **Friend Request body text**: Spec says "친구 요청을 보냈어요", implementation says "친구 신청을 보냈습니다"
   - File: FriendService.java:82
   - Wording change: "요청" -> "신청", "보냈어요" -> "보냈습니다" (formal tone)

2. **Call Again body text**: Spec says "다시 통화하고 싶어해요", implementation says "다시 통화하고 싶어합니다"
   - File: CallService.java:143
   - Title also differs: Spec says "재통화 요청", impl says "다시 통화 요청"

---

## 4. Response Format Verification

### CLAUDE.md Section 5 Spec
```java
{ "success": true/false, "data": T, "message": "..." }
```

### Actual ApiResponse Fields
```java
{ success, code, message, data, errors, timestamp }
```

| Field | Spec | Impl | Status |
|-------|:----:|:----:|:------:|
| success | O | O | OK |
| data | O | O | OK |
| message | O | O | OK |
| code | X | O | EXTRA |
| errors | X | O | EXTRA |
| timestamp | X | O | EXTRA |

Extra fields are additive (backward compatible), no breaking change. Uses `@JsonInclude(NON_NULL)` so null fields are omitted.

---

## 5. Known Persistent Drifts (from previous analysis)

| Item | CLAUDE.md Says | Implementation | Impact |
|------|---------------|----------------|--------|
| UserContext mechanism | "ThreadLocal userId" (Section 6) | `@RequestScope` + `HttpServletRequest` attribute | Low (functional equivalent) |
| Socket.IO auth | "auth.token field" (Section 8) | Authorization header + `?token=` URL param | Low (netty-socketio limitation) |
| TURN credentials in Section 11 | Listed under "미구현 항목" | Implemented at `GET /webrtc/turn-credentials` | Section 11 not updated |

---

## 6. Section 11 Internal Inconsistency

CLAUDE.md Section 11 "백엔드 미구현 항목" still lists:
> TURN 서버 자격증명 API -- `GET /webrtc/turn-credentials`

But Section 11 "백엔드 완료" table already includes:
> TURN 자격증명 -- `GET /webrtc/turn-credentials` -- **2026-03-14 구현 완료**

The "미구현" section contradicts the "완료" table. The "알려진 미해결 이슈" section at the bottom also says this API is "미구현" which is incorrect.

---

## Differences Summary

### Missing from Spec (Design X, Implementation O) -- 3 items

| Item | Implementation Location | Description |
|------|------------------------|-------------|
| `call:ended` socket event | CallService.java:84-88 | Emits to other user when call ends |
| `call:rematch` socket event | CallService.java:158-170 | Emits to both users when mutual rematch |
| `webrtc:error` socket event | MatchSocketHandler.java:347 | Error on unauthorized channel access |

### Changed from Spec (Design != Implementation) -- 3 items

| Item | Design (CLAUDE.md) | Implementation | Impact |
|------|---------------------|----------------|--------|
| Friend request FCM body | "친구 요청을 보냈어요" | "친구 신청을 보냈습니다" | Low |
| Call again FCM title | "재통화 요청" | "다시 통화 요청" | Low |
| Call again FCM body | "다시 통화하고 싶어해요" | "다시 통화하고 싶어합니다" | Low |

### Self-Contradictions in CLAUDE.md -- 1 item

| Item | Section | Issue |
|------|---------|-------|
| TURN credentials | Section 11 | Listed in both "완료" and "미구현" tables |

---

## Recommended Actions

### Immediate: Update CLAUDE.md Section 8 (Socket.IO Events)

Add these rows to the Socket.IO event table:

```
| emit → client | `call:ended`   | 통화 종료 알림 → { sessionId }                                    |
| emit → client | `call:rematch` | 상호 재매칭 → { sessionId, webrtcChannelId, isOfferer }            |
| emit → client | `match:cancelled` | 매칭 취소 확인 → { success }                                   |
| emit → client | `webrtc:error` | WebRTC 채널 오류 → { message }                                    |
```

### Immediate: Update CLAUDE.md Section 11

1. Remove `GET /webrtc/turn-credentials` from "미구현 항목" table
2. Remove the "알려진 미해결 이슈" bullet about `GET /webrtc/turn-credentials API 미구현`
3. Add `call:ended`, `call:rematch` socket events to Section 11 "완료" notes
4. Update FCM notification text in Section 11 to match implementation

### Immediate: Decide FCM Text Normalization

Choose one consistent style for FCM body text:
- Casual: "보냈어요" / "싶어해요" (current spec style)
- Formal: "보냈습니다" / "싶어합니다" (current impl style)

Then synchronize spec and code to the chosen style.

---

## Architecture & Convention Compliance

| Check | Status |
|-------|:------:|
| Controller -> Service -> Repository layering | OK |
| UserContext for auth (no @RequestHeader) | OK |
| Exceptions via common/exception/ classes | OK |
| All responses wrapped in ApiResponse.success() | OK |
| @ConditionalOnProperty for Redis-dependent beans | OK |
| @Autowired(required=false) for optional deps | OK |
| Commit convention (feat/fix/refactor/test/docs/chore) | OK |

---

## Match Rate: 92%

Deductions:
- -4%: 3 undocumented socket events (call:ended, call:rematch, webrtc:error)
- -2%: FCM text mismatches (3 items)
- -1%: Section 11 self-contradiction (TURN credentials)
- -1%: ApiResponse extra fields not documented

All gaps are documentation-only; no missing functionality. Implementation exceeds spec.
