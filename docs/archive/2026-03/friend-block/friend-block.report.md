# Report: friend-block

> Feature: 친구 삭제 + 차단/해제 + 친구 여부 확인
> 기간: 2026-03-18
> Match Rate: 97%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | friend-block |
| 시작일 | 2026-03-18 |
| 완료일 | 2026-03-18 |
| Match Rate | **97%** |
| 구현 파일 | 신규 3개 + 수정 5개 |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 원하지 않는 사용자와 계속 매칭되거나 친구 신청을 받는 상황을 제어할 수단이 없음 |
| Solution | 친구 삭제 + 차단/해제 API + 친구/차단 여부 확인 API, 매칭 큐에서 차단 유저 자동 제외 |
| Function UX Effect | 차단 즉시 매칭 제외, 통화 종료 후 친구 신청 버튼 상태 즉시 반영 |
| Core Value | 사용자가 안전하고 편안한 소통 환경을 직접 제어 |

---

## 1. 구현 내용

### 신규 생성 (3개)

| 파일 | 내용 |
|------|------|
| `friend/domain/Block.java` | 차단 엔티티 (UK, 인덱스, @Builder) |
| `friend/repository/BlockRepository.java` | existsByBlocker/Blocked, existsBlockBetween (양방향) |
| `friend/dto/FriendCheckResponse.java` | `{ isFriend, friendshipId, isBlocked }` |

### 수정 (5개)

| 파일 | 변경 내용 |
|------|-----------|
| `friend/service/FriendService.java` | deleteFriend, blockFriend, unblockUser, checkFriend 추가 |
| `friend/controller/FriendController.java` | DELETE/POST/GET 엔드포인트 추가 |
| `user/controller/UserController.java` | DELETE /me/blocks/{blockedUserId} 추가 |
| `match/service/MatchQueueService.java` | 매칭 루프 내 existsBlockBetween 필터링 추가 |
| `common/response/ErrorCode.java` | FRIENDSHIP_NOT_FOUND, ALREADY_BLOCKED, BLOCK_NOT_FOUND 추가 |

---

## 2. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| 차단 = Friendship 삭제 + Block 생성 | 단일 트랜잭션 | 차단 시 친구 관계 즉시 종료 |
| 차단 해제 API는 blockedUserId 기준 | friendshipId는 이미 삭제됨 | 일관된 API 설계 |
| 양방향 매칭 제외 | `existsBlockBetween` — A→B 또는 B→A 차단 모두 제외 | 단방향 차단도 매칭에서 상호 보호 |
| 차단 체크 순서 최적화 | sendFriendRequest에서 중복 체크 이전에 차단 체크 | 불필요한 DB 조회 감소 |

---

## 3. Success Criteria 최종

| 기준 | 상태 |
|------|------|
| DELETE /friends/{id} — 친구 삭제 204 | ✅ Met |
| POST /friends/{id}/block — 차단 (Friendship 삭제 + Block 생성) | ✅ Met |
| DELETE /users/me/blocks/{userId} — 차단 해제 204 | ✅ Met |
| GET /friends/check — 친구/차단 여부 확인 | ✅ Met |
| 매칭 큐에서 차단 유저 양방향 제외 | ✅ Met |
| 차단된 유저에게 친구 신청 불가 | ✅ Met |

**6/6 (100%)**

---

## 4. Gap 및 잔여 사항

| 심각도 | 항목 | 내용 |
|--------|------|------|
| ℹ️ Minor | FriendRequestRepository 수정 누락 | 설계 문서 오기 — BlockRepository를 FriendService에서 직접 사용하므로 불필요 |

---

## 5. Match Rate

| Category | Score |
|----------|:-----:|
| Structural | 100% |
| Functional | 96% |
| API Contract | 100% |
| **Overall** | **97%** |
