# Design: friend-block

> Plan 참조: `docs/01-plan/features/friend-block.plan.md`
> 작성일: 2026-03-18

---

## 1. 신규 파일 목록

| 파일 | 역할 |
|------|------|
| `friend/domain/Block.java` | 차단 관계 엔티티 |
| `friend/repository/BlockRepository.java` | 차단 조회/존재여부 쿼리 |
| `friend/dto/FriendCheckResponse.java` | 친구/차단 여부 확인 응답 DTO |

---

## 2. 수정 파일 목록

| 파일 | 수정 내용 |
|------|----------|
| `friend/service/FriendService.java` | deleteFriend, blockFriend, unblockFriend, checkFriend 추가 |
| `friend/controller/FriendController.java` | DELETE /friends/{id}, POST /friends/{id}/block, GET /friends/check 추가 |
| `user/controller/UserController.java` | DELETE /users/me/blocks/{blockedUserId} 추가 |
| `friend/repository/FriendRequestRepository.java` | 차단 여부 체크 쿼리 추가 (sendFriendRequest 시) |
| `match/service/MatchQueueService.java` | findMatch() 루프에서 BlockRepository 차단 필터링 추가 |
| `common/response/ErrorCode.java` | FRIENDSHIP_NOT_FOUND, ALREADY_BLOCKED, BLOCK_NOT_FOUND 추가 |

---

## 3. 도메인 설계

### Block.java
```java
@Entity
@Table(name = "blocks",
    uniqueConstraints = @UniqueConstraint(name = "uk_block", columnNames = {"blocker_id", "blocked_id"}),
    indexes = {
        @Index(name = "idx_block_blocker", columnList = "blocker_id"),
        @Index(name = "idx_block_blocked", columnList = "blocked_id")
    }
)
public class Block {
    @Id @GeneratedValue(strategy = IDENTITY)
    Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    User blocker;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    User blocked;

    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    @Builder
    public Block(User blocker, User blocked) { ... }
}
```

---

## 4. Repository 설계

### BlockRepository.java
```java
public interface BlockRepository extends JpaRepository<Block, Long> {

    // 단방향 차단 존재 여부
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 차단 관계 조회 (해제용)
    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 양방향 차단 존재 여부 (매칭 필터링용)
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Block b " +
           "WHERE (b.blocker.id = :userId1 AND b.blocked.id = :userId2) " +
           "OR (b.blocker.id = :userId2 AND b.blocked.id = :userId1)")
    boolean existsBlockBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
```

---

## 5. DTO 설계

### FriendCheckResponse.java
```java
public record FriendCheckResponse(
    boolean isFriend,
    Long friendshipId,   // null if not friends
    boolean isBlocked    // 내가 상대를 차단했는지
) {
    public static FriendCheckResponse of(boolean isFriend, Long friendshipId, boolean isBlocked) {
        return new FriendCheckResponse(isFriend, friendshipId, isBlocked);
    }
}
```

---

## 6. Service 메서드 설계

### FriendService 추가 메서드

```java
// 친구 삭제
@Transactional
public void deleteFriend(Long userId, Long friendshipId) {
    Friendship friendship = friendshipRepository.findById(friendshipId)
        .orElseThrow(() -> new ResourceNotFoundException(FRIENDSHIP_NOT_FOUND));
    // 권한 확인: 요청자가 해당 friendship의 멤버인지
    if (!friendship.getUser1().getId().equals(userId) &&
        !friendship.getUser2().getId().equals(userId)) {
        throw new ForbiddenException(ACCESS_DENIED);
    }
    friendshipRepository.delete(friendship);
}

// 친구 차단 (friendship 기반 → 차단 대상 식별)
@Transactional
public void blockFriend(Long userId, Long friendshipId) {
    Friendship friendship = friendshipRepository.findById(friendshipId)
        .orElseThrow(() -> new ResourceNotFoundException(FRIENDSHIP_NOT_FOUND));
    if (!friendship.getUser1().getId().equals(userId) &&
        !friendship.getUser2().getId().equals(userId)) {
        throw new ForbiddenException(ACCESS_DENIED);
    }
    Long targetId = friendship.getOtherUser(userId).getId();

    if (blockRepository.existsByBlockerIdAndBlockedId(userId, targetId)) {
        throw new DuplicateResourceException(ALREADY_BLOCKED);
    }
    // 1. Friendship 삭제
    friendshipRepository.delete(friendship);
    // 2. Block 생성
    User blocker = userRepository.getReferenceById(userId);
    User blocked = userRepository.getReferenceById(targetId);
    blockRepository.save(Block.builder().blocker(blocker).blocked(blocked).build());
}

// 차단 해제
@Transactional
public void unblockUser(Long blockerId, Long blockedUserId) {
    Block block = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedUserId)
        .orElseThrow(() -> new ResourceNotFoundException(BLOCK_NOT_FOUND));
    blockRepository.delete(block);
}

// 친구/차단 여부 확인
@Transactional(readOnly = true)
public FriendCheckResponse checkFriend(Long userId, Long targetUserId) {
    Optional<Friendship> friendship = friendshipRepository.findBetween(userId, targetUserId);
    boolean isBlocked = blockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId);
    return FriendCheckResponse.of(
        friendship.isPresent(),
        friendship.map(Friendship::getId).orElse(null),
        isBlocked
    );
}
```

---

## 7. Controller 설계

### FriendController 추가
```java
// 친구 삭제
@DeleteMapping("/{friendshipId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteFriend(@PathVariable Long friendshipId) {
    friendService.deleteFriend(userContext.getUserId(), friendshipId);
}

// 친구 차단
@PostMapping("/{friendshipId}/block")
public ApiResponse<Void> blockFriend(@PathVariable Long friendshipId) {
    friendService.blockFriend(userContext.getUserId(), friendshipId);
    return ApiResponse.success(null);
}

// 친구/차단 여부 확인
@GetMapping("/check")
public ApiResponse<FriendCheckResponse> checkFriend(@RequestParam Long userId) {
    return ApiResponse.success(friendService.checkFriend(userContext.getUserId(), userId));
}
```

### UserController 추가
```java
// 차단 해제
@DeleteMapping("/me/blocks/{blockedUserId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void unblockUser(@PathVariable Long blockedUserId) {
    friendService.unblockUser(userContext.getUserId(), blockedUserId);
}
```

---

## 8. 매칭 차단 필터링

### MatchQueueService.findMatch() 수정
`findMatch()` 루프 내 자신 제외 조건 다음에 차단 체크 추가:

```java
// 차단 관계 확인 (양방향)
if (blockRepository.existsBlockBetween(userId, candidateUserId)) {
    continue; // 차단된 사용자 건너뜀
}
```

`MatchQueueService`에 `BlockRepository` 주입 필요.

---

## 9. 친구 신청 차단 체크

### FriendService.sendFriendRequest() 수정
기존 중복 체크 이후에 차단 체크 추가:

```java
// 차단 여부 확인 (내가 차단했거나 상대가 나를 차단한 경우 모두 불가)
if (blockRepository.existsBlockBetween(senderId, receiverId)) {
    throw new ForbiddenException(BLOCKED_USER);
}
```

---

## 10. ErrorCode 추가

```java
// 404 Not Found (Friend)
FRIENDSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIENDSHIP_NOT_FOUND", "친구 관계를 찾을 수 없습니다."),
BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "차단 관계를 찾을 수 없습니다."),

// 409 Conflict
ALREADY_BLOCKED(HttpStatus.CONFLICT, "ALREADY_BLOCKED", "이미 차단한 사용자입니다."),
```

---

## 11. 구현 순서

1. `ErrorCode` 추가 (FRIENDSHIP_NOT_FOUND, BLOCK_NOT_FOUND, ALREADY_BLOCKED)
2. `Block` 도메인 + `BlockRepository`
3. `FriendCheckResponse` DTO
4. `FriendService` 메서드 4개 추가
5. `FriendController` 엔드포인트 3개 추가
6. `UserController` 차단 해제 엔드포인트 추가
7. `FriendService.sendFriendRequest()` 차단 체크 추가
8. `MatchQueueService.findMatch()` 차단 필터링 추가
