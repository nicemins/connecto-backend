# Push Notification Analysis Report

> **Analysis Type**: Gap Analysis (Design vs Implementation)
>
> **Project**: Connecto
> **Version**: 0.0.1-SNAPSHOT
> **Analyst**: Gap Detector Agent
> **Date**: 2026-03-09
> **Design Doc**: [push-notification.design.md](../02-design/features/push-notification.design.md)
> **Plan Doc**: [push-notification.plan.md](../01-plan/features/push-notification.plan.md)

---

## 1. Analysis Overview

### 1.1 Analysis Purpose

Push Notification 기능의 Design 문서(11개 항목)와 실제 구현 코드 간의 일치율을 측정하고 차이점을 식별한다.

### 1.2 Analysis Scope

- **Design Document**: `docs/02-design/features/push-notification.design.md`
- **Implementation Path**: `src/main/java/com/pm/connecto/notification/` + 수정 파일 4개
- **Analysis Date**: 2026-03-09
- **Comparison Items**: 11개 (Design 문서 Section 1~11)

---

## 2. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 100% | ✅ |
| Architecture Compliance | 100% | ✅ |
| Convention Compliance | 97% | ✅ |
| **Overall** | **99%** | ✅ |

---

## 3. Gap Analysis (Design vs Implementation)

### 3.1 Checklist (11 Design Items)

| # | Design Item | Status | Notes |
|:-:|------------|:------:|-------|
| 1 | `build.gradle` -- `firebase-admin:9.2.0` | ✅ Match | Line 52 |
| 2 | `application.yaml` -- `firebase.service-account-json` | ✅ Match | Line 64 |
| 3 | `DeviceToken.java` -- Entity, fields, indexes | ✅ Match | Lombok 추가 (개선) |
| 4 | `DeviceTokenRepository.java` -- 4 methods | ✅ Match | 완전 일치 |
| 5 | `FcmConfig.java` -- nullable FirebaseApp | ✅ Match | try-catch 개선 |
| 6 | `FcmService.java` -- register/delete/sendAsync/UNREGISTERED | ✅ Match | 로깅 추가 |
| 7 | `DeviceTokenRequest.java` -- token, platform validation | ✅ Match | 완전 일치 |
| 8 | `DeviceTokenController.java` -- POST/DELETE /users/me/device-token | ✅ Match | Swagger 추가 |
| 9 | `FriendService.java` -- sendFriendRequest, acceptFriendRequest FCM | ✅ Match | 완전 일치 |
| 10 | `CallService.java` -- requestCallToFriend FCM | ✅ Match | 완전 일치 |
| 11 | `AuthController.java` -- logout deleteAllTokens | ✅ Match | 완전 일치 |

### 3.2 API Endpoints

| Design | Implementation | Status |
|--------|---------------|:------:|
| `POST /users/me/device-token` | `POST /users/me/device-token` | ✅ |
| `DELETE /users/me/device-token` | `DELETE /users/me/device-token` | ✅ |

### 3.3 Data Model (DeviceToken)

| Field | Design | Implementation | Status |
|-------|--------|---------------|:------:|
| id | Long, IDENTITY | Long, IDENTITY | ✅ |
| user | ManyToOne, LAZY, nullable=false | ManyToOne, LAZY, nullable=false | ✅ |
| token | String, unique, length=500 | String, unique, length=500 | ✅ |
| platform | String, length=20 | String, length=20 | ✅ |
| createdAt | LocalDateTime, @PrePersist | LocalDateTime, @PrePersist | ✅ |
| **Index** idx_device_token_user_id | user_id | user_id | ✅ |
| **Index** idx_device_token_token | token, unique=true | token, unique=true | ✅ |

### 3.4 Repository Methods

| Design Method | Implementation | Status |
|--------------|---------------|:------:|
| `findAllByUserId(Long)` | Present | ✅ |
| `findByToken(String)` | Present | ✅ |
| `deleteByUserIdAndToken(Long, String)` | Present | ✅ |
| `deleteAllByUserId(Long)` | Present | ✅ |

### 3.5 FCM Trigger Points

| Event | Design Trigger | Impl Trigger | Title/Body Match | Status |
|-------|---------------|-------------|:----------------:|:------:|
| Friend Request | `FriendService.sendFriendRequest()` | Line 82 | "친구 요청" / "{nickname}님이 친구 요청을 보냈어요" | ✅ |
| Friend Accept | `FriendService.acceptFriendRequest()` | Line 117 | "친구 수락" / "{nickname}님이 친구 요청을 수락했어요" | ✅ |
| Friend Call | `CallService.requestCallToFriend()` | Line 163 | "통화 요청" / "{nickname}님이 통화를 요청했어요" | ✅ |

### 3.6 Match Rate Summary

```
+---------------------------------------------+
|  Overall Match Rate: 100%                    |
+---------------------------------------------+
|  ✅ Match:           11/11 items (100%)      |
|  ⚠️ Missing design:   0 items (0%)           |
|  ❌ Not implemented:   0 items (0%)           |
+---------------------------------------------+
```

---

## 4. Implementation Improvements Over Design

구현이 Design보다 개선된 부분 (불일치가 아닌 양성 차이).

| # | Item | Design | Implementation | Impact |
|:-:|------|--------|---------------|--------|
| 1 | FcmConfig 에러 처리 | `throws Exception` 선언 | `try-catch` + 로그 + null 반환 | 안정성 향상 |
| 2 | FirebaseApp DI 방식 | 생성자 주입 | `@Autowired(required=false)` 필드 주입 | null 빈 허용 용이 |
| 3 | FcmService 로깅 | 없음 | register/delete/send 시 debug 로그 | 운영 가시성 향상 |
| 4 | DeviceToken Lombok | 수동 getter/constructor | `@Getter`, `@NoArgsConstructor` | 보일러플레이트 감소 |
| 5 | Controller Swagger | 없음 | `@Tag`, `@Operation` 어노테이션 | API 문서화 자동화 |
| 6 | sendToUserAsync | `@Transactional` 없음 | `@Transactional` 추가 | 토큰 삭제 시 트랜잭션 보장 |

---

## 5. Convention Compliance

### 5.1 Naming Convention

| Category | Convention | Files | Compliance |
|----------|-----------|:-----:|:----------:|
| Entity class | PascalCase | 1 | 100% |
| Service class | PascalCase + Service suffix | 1 | 100% |
| Controller class | PascalCase + Controller suffix | 1 | 100% |
| Repository | PascalCase + Repository suffix | 1 | 100% |
| DTO record | PascalCase + Request/Response suffix | 1 | 100% |
| Config class | PascalCase + Config suffix | 1 | 100% |

### 5.2 Architecture Compliance

| Rule | Status | Notes |
|------|:------:|-------|
| Controller -> Service -> Repository | ✅ | DeviceTokenController -> FcmService -> DeviceTokenRepository |
| UserContext for auth userId | ✅ | `userContext.getUserId()` 사용 |
| ApiResponse wrapping | ✅ | `ApiResponse.success(null)` |
| Exception via common/exception | N/A | FCM 실패는 로깅만 (설계 의도) |
| Package placement (notification/) | ✅ | 프로젝트 패키지 컨벤션 준수 |

### 5.3 Convention Score

```
+---------------------------------------------+
|  Convention Compliance: 97%                  |
+---------------------------------------------+
|  Naming:           100%                      |
|  Layer Structure:  100%                      |
|  DI Pattern:        90% (field injection 1건)|
|  Response Format:  100%                      |
+---------------------------------------------+
```

**Minor Note**: `FcmService.firebaseApp`이 `@Autowired(required=false)` 필드 주입을 사용한다. 프로젝트의 다른 서비스는 생성자 주입을 사용하므로 일관성 차이가 있으나, nullable 빈을 다루기 위한 합리적 선택이다.

---

## 6. Plan DoD (Definition of Done) Verification

Plan 문서 Section 9의 완료 기준 대비 구현 상태.

| DoD Item | Status | Evidence |
|----------|:------:|---------|
| POST /users/me/device-token 토큰 저장/갱신 | ✅ | DeviceTokenController.registerToken() |
| DELETE /users/me/device-token 토큰 삭제 | ✅ | DeviceTokenController.deleteToken() |
| 친구 요청 시 수신자에게 FCM 전송 | ✅ | FriendService.java:82 |
| 친구 수락 시 발신자에게 FCM 전송 | ✅ | FriendService.java:117 |
| 통화 요청 시 수신자에게 FCM 전송 | ✅ | CallService.java:163 |
| FCM 전송 실패 시 API 응답 영향 없음 | ✅ | @Async + try-catch + log only |
| 로그아웃 시 토큰 무효화 | ✅ | AuthController.java:136 |
| 서비스 계정 JSON 소스코드 미포함 | ✅ | 환경변수 `${FIREBASE_SERVICE_ACCOUNT_JSON:}` |

**DoD 달성률: 8/8 (100%)**

---

## 7. Missing Features (Design O, Implementation X)

없음.

---

## 8. Added Features (Design X, Implementation O)

없음.

---

## 9. Changed Features (Design != Implementation)

| Item | Design | Implementation | Impact | Verdict |
|------|--------|---------------|--------|---------|
| FcmConfig error handling | `throws Exception` | try-catch + null return | Low | Improvement |
| FirebaseApp injection | Constructor | `@Autowired(required=false)` field | Low | Acceptable |
| FcmService.sendToUserAsync | No @Transactional | @Transactional added | Low | Improvement |

모두 구현이 설계보다 개선된 방향이며, 기능적 불일치는 없다.

---

## 10. Recommended Actions

### 10.1 Documentation Update (Optional)

| Priority | Item | Description |
|----------|------|-------------|
| Low | Design 문서 FcmConfig 업데이트 | try-catch 패턴 반영 |
| Low | Design 문서 Swagger annotation 추가 | Controller에 @Tag/@Operation 반영 |
| Low | CLAUDE.md Section 4/5 업데이트 | notification 패키지 및 device-token API 추가 |

### 10.2 Immediate Actions

없음. 모든 설계 항목이 구현 완료됨.

---

## 11. Next Steps

- [x] Gap Analysis 완료 (Match Rate 100%)
- [ ] CLAUDE.md Section 4 (패키지 구조)에 notification 패키지 추가
- [ ] CLAUDE.md Section 5 (API 명세)에 device-token API 추가
- [ ] CLAUDE.md Section 7 (도메인 모델)에 DeviceToken 엔티티 추가
- [ ] CLAUDE.md Section 11 (구현 현황)에 Push Notification 완료 기록

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-03-09 | Initial gap analysis | Gap Detector Agent |
