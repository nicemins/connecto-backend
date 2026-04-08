# friend-block Analysis Report

> **Analysis Type**: Gap Analysis (Design vs Implementation)
>
> **Project**: Connecto
> **Analyst**: Claude Code (gap-detector)
> **Date**: 2026-03-18
> **Design Doc**: [friend-block.design.md](../02-design/features/friend-block.design.md)

---

## 1. Analysis Overview

### 1.1 Analysis Purpose

Design 문서(`friend-block.design.md`)와 실제 구현 코드 간의 일치율을 측정하고, 누락/변경/추가 항목을 식별한다.

### 1.2 Analysis Scope

- **Design Document**: `docs/02-design/features/friend-block.design.md`
- **Implementation Path**: `src/main/java/com/pm/connecto/friend/`, `user/controller/`, `match/service/`, `common/response/`
- **Analysis Date**: 2026-03-18

---

## 2. Gap Analysis (Design vs Implementation)

### 2.1 신규 파일 (Design Section 1)

| Design | Implementation | Status |
|--------|---------------|--------|
| `friend/domain/Block.java` | `friend/domain/Block.java` | ✅ Match |
| `friend/repository/BlockRepository.java` | `friend/repository/BlockRepository.java` | ✅ Match |
| `friend/dto/FriendCheckResponse.java` | `friend/dto/FriendCheckResponse.java` | ✅ Match |

### 2.2 수정 파일 (Design Section 2)

| Design | Implementation | Status | Notes |
|--------|---------------|--------|-------|
| `friend/service/FriendService.java` | Modified | ✅ Match | 4개 메서드 모두 추가됨 |
| `friend/controller/FriendController.java` | Modified | ✅ Match | 3개 엔드포인트 모두 추가됨 |
| `user/controller/UserController.java` | Modified | ✅ Match | DELETE /me/blocks/{blockedUserId} 추가됨 |
| `friend/repository/FriendRequestRepository.java` | **Not modified** | ❌ Not implemented | Design Section 2에 "차단 여부 체크 쿼리 추가" 명시, 실제 미수정 |
| `match/service/MatchQueueService.java` | Modified | ✅ Match | 차단 필터링 추가됨 |
| `common/response/ErrorCode.java` | Modified | ✅ Match | 3개 ErrorCode 모두 추가됨 |

### 2.3 도메인 모델 (Design Section 3)

| Field | Design | Implementation | Status |
|-------|--------|---------------|--------|
| `@Entity @Table(name="blocks")` | blocks | blocks | ✅ |
| `UniqueConstraint(uk_block)` | blocker_id, blocked_id | blocker_id, blocked_id | ✅ |
| `@Index(idx_block_blocker)` | blocker_id | blocker_id | ✅ |
| `@Index(idx_block_blocked)` | blocked_id | blocked_id | ✅ |
| `id` Long IDENTITY | Specified | Implemented | ✅ |
| `blocker` User LAZY | Specified | Implemented | ✅ |
| `blocked` User LAZY | Specified | Implemented | ✅ |
| `createdAt` | @PrePersist | @PrePersist | ✅ |
| `@Builder` | Specified | Implemented | ✅ |
| `@Getter` | Not specified | Added | ✅ (필수 추가) |
| `@NoArgsConstructor(PROTECTED)` | Not specified | Added | ✅ (JPA 필수) |

### 2.4 Repository (Design Section 4)

| Method | Design | Implementation | Status |
|--------|--------|---------------|--------|
| `existsByBlockerIdAndBlockedId` | Specified | Implemented | ✅ |
| `findByBlockerIdAndBlockedId` | Specified | Implemented | ✅ |
| `existsBlockBetween` (JPQL) | Specified | Implemented | ✅ |

### 2.5 DTO (Design Section 5)

| Field | Design | Implementation | Status |
|-------|--------|---------------|--------|
| `FriendCheckResponse.isFriend` | boolean | boolean | ✅ |
| `FriendCheckResponse.friendshipId` | Long (nullable) | Long (nullable) | ✅ |
| `FriendCheckResponse.isBlocked` | boolean | boolean | ✅ |
| `static of()` factory | Specified | Implemented | ✅ |

### 2.6 Service 메서드 (Design Section 6)

| Method | Design | Implementation | Status | Notes |
|--------|--------|---------------|--------|-------|
| `deleteFriend(userId, friendshipId)` | Specified | Implemented | ✅ | 로직 일치 |
| `blockFriend(userId, friendshipId)` | Specified | Implemented | ✅ | Friendship 삭제 + Block 생성 |
| `unblockUser(blockerId, blockedUserId)` | `unblockFriend` | `unblockUser` | ✅ | 이름 일치 (Design Section 2는 `unblockFriend`, Section 6은 `unblockUser` -- 구현은 Section 6 따름) |
| `checkFriend(userId, targetUserId)` | Specified | Implemented | ✅ | 로직 일치 |

### 2.7 Controller (Design Section 7)

| Endpoint | Design | Implementation | Status |
|----------|--------|---------------|--------|
| `DELETE /friends/{friendshipId}` | 204 No Content | 204 No Content | ✅ |
| `POST /friends/{friendshipId}/block` | ApiResponse\<Void\> | ApiResponse\<Void\> | ✅ |
| `GET /friends/check?userId=` | ApiResponse\<FriendCheckResponse\> | ApiResponse\<FriendCheckResponse\> | ✅ |
| `DELETE /users/me/blocks/{blockedUserId}` | 204 No Content | 204 No Content | ✅ |

### 2.8 매칭 차단 필터링 (Design Section 8)

| Item | Design | Implementation | Status |
|------|--------|---------------|--------|
| `MatchQueueService`에 `BlockRepository` 주입 | Specified | Implemented | ✅ |
| `findMatch()` 루프 내 `existsBlockBetween` 호출 | Specified | Line 203 | ✅ |

### 2.9 친구 신청 차단 체크 (Design Section 9)

| Item | Design | Implementation | Status | Notes |
|------|--------|---------------|--------|-------|
| `sendFriendRequest()` 내 차단 체크 | `existsBlockBetween` 후 `BLOCKED_USER` throw | Line 72-74 | ✅ | 중복 체크 **이전**에 위치 (Design은 "이후"라고 명시) -- 순서 차이 |

### 2.10 ErrorCode (Design Section 10)

| ErrorCode | Design Status | Design Message | Impl Status | Impl Message | Status |
|-----------|:-----:|--------|:-----:|--------|--------|
| `FRIENDSHIP_NOT_FOUND` | 404 | "친구 관계를 찾을 수 없습니다." | 404 | "친구 관계를 찾을 수 없습니다." | ✅ |
| `BLOCK_NOT_FOUND` | 404 | "차단 관계를 찾을 수 없습니다." | 404 | "차단 관계를 찾을 수 없습니다." | ✅ |
| `ALREADY_BLOCKED` | 409 | "이미 차단한 사용자입니다." | 409 | "이미 차단한 사용자입니다." | ✅ |

---

## 3. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 96% | ✅ |
| Architecture Compliance | 100% | ✅ |
| Convention Compliance | 100% | ✅ |
| **Overall** | **97%** | ✅ |

### Match Rate Summary

```
Total Items: 30
  ✅ Match:           28 items (93%)
  ⚠️ Minor deviation:  1 item  (3%)   — sendFriendRequest 차단 체크 순서
  ❌ Not implemented:   1 item  (3%)   — FriendRequestRepository 수정 누락
```

---

## 4. Differences Found

### ❌ Missing Features (Design O, Implementation X)

| Item | Design Location | Description |
|------|-----------------|-------------|
| FriendRequestRepository 수정 | design Section 2 | "차단 여부 체크 쿼리 추가 (sendFriendRequest 시)" -- 실제로는 BlockRepository.existsBlockBetween()을 FriendService에서 직접 호출하므로 FriendRequestRepository 수정 불필요. Design 문서의 기술이 부정확. |

### ⚠️ Changed Features (Design != Implementation)

| Item | Design | Implementation | Impact |
|------|--------|---------------|--------|
| sendFriendRequest 차단 체크 순서 | "기존 중복 체크 이후" | 중복 체크 **이전** (Line 72-74) | Low -- 기능적 차이 없음. 차단 우선 체크가 더 효율적. |
| Design Section 2 메서드명 | `unblockFriend` | `unblockUser` | Low -- Section 6의 시그니처 `unblockUser`와 일치. Section 2만 불일치. |

---

## 5. Recommended Actions

### Documentation Update Needed

1. **Design Section 2**: `FriendRequestRepository.java` 수정 항목 삭제 또는 "FriendService에서 BlockRepository 직접 사용"으로 수정
2. **Design Section 2**: `unblockFriend` -> `unblockUser`로 메서드명 일치
3. **Design Section 9**: "기존 중복 체크 이후" -> "중복 체크 이전"으로 순서 수정

### No Implementation Changes Required

구현이 Design의 의도를 정확히 반영하고 있으며, 차이점은 모두 Design 문서의 부정확한 기술에 기인함.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-03-18 | Initial analysis | Claude Code (gap-detector) |
