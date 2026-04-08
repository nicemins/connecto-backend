# Push Notification — Plan Document

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | push-notification |
| 시작일 | 2026-03-08 |
| 담당 | Backend |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | 앱이 백그라운드/종료 상태일 때 친구 요청·통화 수신 등 중요 이벤트를 사용자가 놓침 |
| **Solution** | FCM(Firebase Cloud Messaging) 기반 서버 푸시 알림으로 실시간 이벤트 전달 |
| **Function UX Effect** | 앱 미사용 중에도 친구 요청·수락·통화 수신 알림 즉시 수신 → 사용자 리텐션 향상 |
| **Core Value** | 플랫폼 이탈 없이 소셜 인터랙션 유지 — 매칭·친구 기능의 완성도 확보 |

---

## 1. 요구사항

### 1.1 기능 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | 사용자 기기의 FCM 디바이스 토큰을 서버에 등록/갱신할 수 있어야 한다 | 높음 |
| FR-02 | 친구 요청을 받으면 수신자에게 푸시 알림을 전송해야 한다 | 높음 |
| FR-03 | 친구 요청이 수락되면 발신자에게 푸시 알림을 전송해야 한다 | 높음 |
| FR-04 | 친구에게 통화 요청이 오면 수신자에게 푸시 알림을 전송해야 한다 | 높음 |
| FR-05 | 매칭 성공 시 양쪽 사용자에게 푸시 알림을 전송해야 한다 | 중간 |
| FR-06 | 사용자가 로그아웃하면 디바이스 토큰을 무효화해야 한다 | 높음 |
| FR-07 | FCM 전송 실패 시 서버 오류 없이 로깅만 수행해야 한다 (non-blocking) | 높음 |

### 1.2 비기능 요구사항

| 항목 | 내용 |
|------|------|
| 비동기 처리 | 푸시 알림 전송은 API 응답을 블로킹하지 않아야 함 (`@Async`) |
| 실패 내성 | FCM 오류가 메인 비즈니스 로직에 영향을 주지 않아야 함 |
| 토큰 관리 | 만료/무효 토큰 자동 제거 (FCM `UNREGISTERED` 에러 처리) |
| 보안 | FCM Service Account JSON은 환경변수로 관리, 소스 커밋 금지 |

---

## 2. 기술 선택

### 2.1 FCM vs APNs vs Unified

| 방식 | 이유 |
|------|------|
| **FCM 단독** ✅ | 현재 Android 앱 (React Native) 대상; iOS 확장 시 FCM이 APNs 브릿지 역할 가능 |
| Firebase Admin SDK | 서버에서 FCM 전송. Java SDK 공식 지원 |

### 2.2 Firebase Admin SDK

```gradle
// build.gradle 추가
implementation 'com.google.firebase:firebase-admin:9.2.0'
```

### 2.3 아키텍처 결정

```
이벤트 발생 (FriendService, CallService 등)
    → FcmService.sendAsync(userId, title, body, data)  // @Async, non-blocking
    → DeviceTokenRepository.findByUserId(userId)       // 토큰 조회
    → FirebaseMessaging.send(message)                  // FCM API 호출
    → 실패 시 로그 기록 (비즈니스 로직 미영향)
```

---

## 3. 구현 범위 (MVP)

### 포함
- FCM Admin SDK 설정 (`FcmConfig`)
- 디바이스 토큰 도메인 (`DeviceToken` 엔티티 + Repository)
- 토큰 등록/갱신/삭제 API (`POST /users/me/device-token`, `DELETE /users/me/device-token`)
- 알림 전송 서비스 (`FcmService`)
- 트리거 포인트 연동: 친구 요청, 친구 수락, 친구 통화 요청

### 제외 (추후)
- 알림 이력 저장 (Notification History)
- 알림 설정 (수신 켜기/끄기)
- 매칭 성공 알림 (Socket.IO로 이미 처리)
- iOS APNs 연동

---

## 4. 신규 파일 목록

```
notification/
├── config/FcmConfig.java              # Firebase Admin SDK 초기화
├── controller/DeviceTokenController.java  # POST/DELETE /users/me/device-token
├── domain/DeviceToken.java            # userId, token, platform, createdAt
├── dto/DeviceTokenRequest.java        # { token, platform }
├── repository/DeviceTokenRepository.java
└── service/FcmService.java            # sendAsync(), sendToUser()
```

---

## 5. API 명세 (신규)

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/users/me/device-token` | FCM 토큰 등록/갱신 | O |
| DELETE | `/users/me/device-token` | FCM 토큰 삭제 (로그아웃 시) | O |

**Request Body (POST):**
```json
{ "token": "fcm_device_token_string", "platform": "android" }
```

---

## 6. 알림 이벤트 정의

| 이벤트 | 트리거 위치 | 수신자 | title | body |
|--------|-----------|--------|-------|------|
| 친구 요청 | `FriendService.sendFriendRequest()` | receiver | "친구 요청" | "{nickname}님이 친구 요청을 보냈어요" |
| 친구 수락 | `FriendService.acceptFriendRequest()` | sender | "친구 수락" | "{nickname}님이 친구 요청을 수락했어요" |
| 통화 요청 | `CallService.requestCallToFriend()` | friend | "통화 요청" | "{nickname}님이 통화를 요청했어요" |

---

## 7. 환경변수 추가

```env
# .env.local 추가 항목
FIREBASE_SERVICE_ACCOUNT_JSON=<service_account_json_content 또는 파일 경로>
```

```yaml
# application.yaml 추가
firebase:
  service-account-json: ${FIREBASE_SERVICE_ACCOUNT_JSON:}
```

---

## 8. 의존성 관계

```
push-notification
  depends-on: friend (FriendService 트리거)
  depends-on: call (CallService 트리거)
  depends-on: user (DeviceToken ↔ User FK)
  no-new-redis-required: true
  no-new-db-required: false (DeviceToken 테이블 신규)
```

---

## 9. 완료 기준 (DoD)

- [ ] `POST /users/me/device-token` 토큰 저장/갱신 동작
- [ ] `DELETE /users/me/device-token` 토큰 삭제 동작
- [ ] 친구 요청 시 수신자에게 FCM 전송 (로그 확인)
- [ ] 친구 수락 시 발신자에게 FCM 전송 (로그 확인)
- [ ] 통화 요청 시 수신자에게 FCM 전송 (로그 확인)
- [ ] FCM 전송 실패 시 API 응답 영향 없음 (non-blocking 확인)
- [ ] 로그아웃 시 토큰 무효화 동작
- [ ] 서비스 계정 JSON이 소스코드에 포함되지 않음
