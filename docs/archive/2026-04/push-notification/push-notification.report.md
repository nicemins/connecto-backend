# Report: push-notification

> Feature: FCM 푸시 알림
> 기간: 2026-03-08 ~ 2026-04-04
> Match Rate: 99%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | push-notification |
| 시작일 | 2026-03-08 |
| 완료일 | 2026-04-04 |
| Match Rate | **99%** |
| 구현 파일 | 신규 6개 + 수정 3개 |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 앱 백그라운드/종료 상태에서 친구 요청·통화 수신 등 중요 이벤트를 놓침 |
| Solution | FCM `@Async` non-blocking 푸시, nullable FcmConfig으로 로컬 개발 무영향 |
| Function UX Effect | 앱 미사용 중에도 친구 요청·수락·통화 요청 즉시 수신 |
| Core Value | 플랫폼 이탈 없이 소셜 인터랙션 유지 — 매칭·친구 기능의 완성도 확보 |

---

## 1. 구현 내용

### 신규 생성 (6개)

| 파일 | 내용 |
|------|------|
| `notification/config/FcmConfig.java` | Firebase Admin SDK nullable 초기화 |
| `notification/domain/DeviceToken.java` | FCM 토큰 엔티티 (user FK, unique token, platform) |
| `notification/dto/DeviceTokenRequest.java` | `{ token, platform }` — `@Pattern(android\|ios)` |
| `notification/repository/DeviceTokenRepository.java` | findAllByUserId, findByToken, deleteBy* |
| `notification/service/FcmService.java` | 토큰 관리 + @Async 전송 + UNREGISTERED 자동 삭제 |
| `notification/controller/DeviceTokenController.java` | POST/DELETE /users/me/device-token |

### 수정 (3개)

| 파일 | 변경 내용 |
|------|-----------|
| `friend/service/FriendService.java` | sendFriendRequest, acceptFriendRequest FCM 트리거 |
| `call/service/CallService.java` | requestCallToFriend FCM 트리거 + sendToUserWithDataAsync (재통화) |
| `user/controller/AuthController.java` | logout 시 deleteAllTokens 연동 |

---

## 2. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| `@Async` non-blocking | FCM 전송이 API 응답 블로킹 안 함 | FR-07 달성 |
| `@Autowired(required = false)` | FirebaseApp nullable — 미설정 시 null | 로컬 개발 정상 동작 |
| UNREGISTERED 자동 삭제 | FCM 응답 코드 체크 후 만료 토큰 제거 | 토큰 테이블 자동 정리 |
| `sendToUserWithDataAsync` 추가 | 재통화 딥링크용 data payload 지원 | 설계 외 향상 |

---

## 3. Success Criteria 최종

| 기준 | 상태 |
|------|------|
| POST /users/me/device-token 토큰 저장 | ✅ Met |
| DELETE /users/me/device-token 토큰 삭제 | ✅ Met |
| 친구 요청 → 수신자 FCM | ✅ Met |
| 친구 수락 → 발신자 FCM | ✅ Met |
| 통화 요청 → 수신자 FCM | ✅ Met |
| FCM 실패 시 API 무영향 | ✅ Met |
| 로그아웃 시 토큰 삭제 | ✅ Met |
| 서비스 계정 JSON 소스 미포함 | ✅ Met |

**8/8 (100%)**

---

## 4. Match Rate

| Category | Score |
|----------|:-----:|
| Structural | 100% |
| Functional | 99% |
| API Contract | 100% |
| **Overall** | **99%** |

> Minor gap 1건: 알림 메시지 텍스트 "보냈어요" vs "보냈습니다" — 기능 무관
