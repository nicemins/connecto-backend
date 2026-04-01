# Design: push-notification

## 참조

- Plan: `docs/01-plan/features/push-notification.plan.md`
- 작성일: 2026-03-08

---

## 아키텍처 다이어그램

```
[이벤트 발생]                         [FCM 전송 흐름]
FriendService.sendFriendRequest()
FriendService.acceptFriendRequest()  →  FcmService.sendToUserAsync(userId, title, body)
CallService.requestCallToFriend()           │
                                            ├─ DeviceTokenRepository.findAllByUserId(userId)
                                            ├─ 토큰 없음 → 조용히 종료
                                            ├─ FirebaseMessaging.send(message)
                                            │     └─ 실패 시 로그만 기록 (예외 전파 X)
                                            └─ UNREGISTERED 응답 → 토큰 자동 삭제

[토큰 관리 API]
POST /users/me/device-token  →  DeviceTokenController  →  FcmService.registerToken()
DELETE /users/me/device-token →  DeviceTokenController  →  FcmService.deleteToken()
```

---

## 신규 파일

| 파일 | 역할 |
|------|------|
| `notification/config/FcmConfig.java` | Firebase Admin SDK 초기화 (`FirebaseApp` 빈) |
| `notification/domain/DeviceToken.java` | FCM 토큰 엔티티 |
| `notification/dto/DeviceTokenRequest.java` | `{ token, platform }` DTO |
| `notification/repository/DeviceTokenRepository.java` | 토큰 CRUD |
| `notification/service/FcmService.java` | 토큰 등록/삭제 + 비동기 FCM 전송 |
| `notification/controller/DeviceTokenController.java` | `POST/DELETE /users/me/device-token` |

## 수정 파일

| 파일 | 변경 내용 |
|------|---------|
| `build.gradle` | `firebase-admin:9.2.0` 의존성 추가 |
| `application.yaml` | `firebase.service-account-json` 설정 추가 |
| `.env.local` | `FIREBASE_SERVICE_ACCOUNT_JSON` 환경변수 추가 안내 |
| `friend/service/FriendService.java` | `sendFriendRequest()`, `acceptFriendRequest()` 에 FCM 트리거 추가 |
| `call/service/CallService.java` | `requestCallToFriend()` 에 FCM 트리거 추가 |
| `user/controller/AuthController.java` | `POST /auth/logout` 에 토큰 삭제 연동 |
| `common/response/ErrorCode.java` | (변경 없음 — FCM 실패는 로깅만) |

---

## 상세 설계

### 1. build.gradle 의존성

```gradle
// Firebase Admin SDK
implementation 'com.google.firebase:firebase-admin:9.2.0'
```

### 2. application.yaml 설정 추가

```yaml
firebase:
  service-account-json: ${FIREBASE_SERVICE_ACCOUNT_JSON:}
```

### 3. DeviceToken 엔티티

```java
@Entity
@Table(name = "device_tokens", indexes = {
    @Index(name = "idx_device_token_user_id", columnList = "user_id"),
    @Index(name = "idx_device_token_token", columnList = "token", unique = true)
})
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(nullable = false, length = 20)
    private String platform;   // "android" | "ios"

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
```

### 4. DeviceTokenRepository

```java
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findAllByUserId(Long userId);
    Optional<DeviceToken> findByToken(String token);
    void deleteByUserIdAndToken(Long userId, String token);
    void deleteAllByUserId(Long userId);
}
```

### 5. FcmConfig.java

```java
@Configuration
public class FcmConfig {

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            // 로컬 개발 환경: FCM 비활성 (로그만)
            return null;
        }
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream stream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build();
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }
}
```

> `FIREBASE_SERVICE_ACCOUNT_JSON`이 비어있으면 `FirebaseApp` 빈이 null — FcmService는 null 체크 후 skip

### 6. FcmService.java

```java
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final FirebaseApp firebaseApp;   // nullable

    // === 토큰 관리 ===

    @Transactional
    public void registerToken(Long userId, String token, String platform) {
        // 이미 존재하면 upsert (동일 token이면 skip)
        deviceTokenRepository.findByToken(token).ifPresentOrElse(
            existing -> { /* 이미 있음 — 무시 */ },
            () -> {
                User user = userRepository.getReferenceById(userId);
                DeviceToken deviceToken = new DeviceToken(user, token, platform);
                deviceTokenRepository.save(deviceToken);
            }
        );
    }

    @Transactional
    public void deleteToken(Long userId, String token) {
        deviceTokenRepository.deleteByUserIdAndToken(userId, token);
    }

    @Transactional
    public void deleteAllTokens(Long userId) {
        deviceTokenRepository.deleteAllByUserId(userId);
    }

    // === 알림 전송 ===

    @Async
    public void sendToUserAsync(Long userId, String title, String body) {
        if (firebaseApp == null) {
            log.debug("FCM not configured. Skipping notification to userId={}", userId);
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(userId);
        if (tokens.isEmpty()) return;

        for (DeviceToken deviceToken : tokens) {
            sendMessage(deviceToken, title, body);
        }
    }

    private void sendMessage(DeviceToken deviceToken, String title, String body) {
        try {
            Message message = Message.builder()
                .setToken(deviceToken.getToken())
                .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build())
                .build();
            FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.debug("FCM sent to userId={}", deviceToken.getUser().getId());
        } catch (FirebaseMessagingException e) {
            if ("UNREGISTERED".equals(e.getMessagingErrorCode().name())) {
                // 만료된 토큰 자동 삭제
                deviceTokenRepository.delete(deviceToken);
                log.info("Removed expired FCM token for userId={}", deviceToken.getUser().getId());
            } else {
                log.warn("FCM send failed for userId={}: {}", deviceToken.getUser().getId(), e.getMessage());
            }
        }
    }
}
```

### 7. DeviceTokenController.java

```java
@RestController
@RequestMapping("/users/me")
public class DeviceTokenController {

    private final FcmService fcmService;
    private final UserContext userContext;

    // POST /users/me/device-token
    @PostMapping("/device-token")
    public ApiResponse<Void> registerToken(@Valid @RequestBody DeviceTokenRequest request) {
        fcmService.registerToken(userContext.getUserId(), request.token(), request.platform());
        return ApiResponse.success(null);
    }

    // DELETE /users/me/device-token
    @DeleteMapping("/device-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteToken(@Valid @RequestBody DeviceTokenRequest request) {
        fcmService.deleteToken(userContext.getUserId(), request.token());
    }
}
```

### 8. DeviceTokenRequest DTO

```java
public record DeviceTokenRequest(
    @NotBlank String token,
    @NotBlank @Pattern(regexp = "android|ios") String platform
) {}
```

### 9. FriendService 트리거 연동

**sendFriendRequest() 변경:**
```java
// 기존 코드 끝에 추가
Profile senderProfile = profileRepository.findByUserId(senderId).orElse(null);
String senderNickname = senderProfile != null ? senderProfile.getNickname() : "누군가";
fcmService.sendToUserAsync(receiverId, "친구 요청", senderNickname + "님이 친구 요청을 보냈어요");
```

**acceptFriendRequest() 변경:**
```java
// 기존 코드 끝에 추가
Profile receiverProfile = profileRepository.findByUserId(receiverId).orElse(null);
String receiverNickname = receiverProfile != null ? receiverProfile.getNickname() : "누군가";
fcmService.sendToUserAsync(request.getSender().getId(), "친구 수락", receiverNickname + "님이 친구 요청을 수락했어요");
```

### 10. CallService 트리거 연동

**requestCallToFriend() 변경:**
```java
// callSessionRepository.save(session) 이후 추가
Profile callerProfile = profileRepository.findByUserId(callerId).orElse(null);
String callerNickname = callerProfile != null ? callerProfile.getNickname() : "누군가";
fcmService.sendToUserAsync(friendId, "통화 요청", callerNickname + "님이 통화를 요청했어요");
```

> CallService에 `ProfileRepository`와 `FcmService` 의존성 추가 필요

### 11. AuthController 로그아웃 연동

**POST /auth/logout 변경:**
```java
// 기존 로그아웃 처리 후 추가
// Request Body에 token 포함 시 삭제 (선택적)
// 또는 userId 기준 전체 토큰 삭제
fcmService.deleteAllTokens(userContext.getUserId());
```

---

## DB 스키마 (신규 테이블)

```sql
CREATE TABLE device_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    token       VARCHAR(500) NOT NULL UNIQUE,
    platform    VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_device_token_user_id ON device_tokens(user_id);
```

---

## 검증 규칙

| 항목 | 규칙 | 처리 |
|------|------|------|
| token 필수 | 빈 문자열 불가 | 400 Bad Request |
| platform | "android" 또는 "ios"만 허용 | 400 Bad Request |
| FCM 전송 실패 | 비즈니스 로직에 영향 없음 | 로그만 기록 |
| 만료 토큰 | UNREGISTERED 응답 시 자동 삭제 | 자동 처리 |

---

## 구현 순서 (Do 페이즈용)

1. `build.gradle` — `firebase-admin:9.2.0` 추가
2. `application.yaml` — `firebase.service-account-json` 설정
3. `DeviceToken.java` — 엔티티
4. `DeviceTokenRepository.java` — Repository
5. `FcmConfig.java` — Firebase 초기화 (nullable 처리 포함)
6. `FcmService.java` — 토큰 관리 + `@Async` 전송 + 만료 토큰 삭제
7. `DeviceTokenRequest.java` — DTO
8. `DeviceTokenController.java` — API 핸들러
9. `FriendService.java` — sendFriendRequest, acceptFriendRequest 트리거 추가
10. `CallService.java` — requestCallToFriend 트리거 추가 (ProfileRepository, FcmService 주입)
11. `AuthController.java` — logout 시 토큰 전체 삭제 연동
12. `FilterConfig.java` — 인증 불필요 경로 확인 (device-token은 인증 필요 → 기존 필터로 처리)
