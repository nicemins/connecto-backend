# Report: deployment-final

> Feature: 배포 최종 점검 (report/interest/language Gap + 프로덕션 안전성)
> 기간: 2026-04-04
> Match Rate: 100%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | deployment-final |
| 분석일 | 2026-04-04 |
| 완료일 | 2026-04-04 |
| Match Rate | **100%** (수정 후) |
| 수정 파일 | 4개 (application-prod.yaml, UserController.java, CallSessionScheduler.java, LanguageService.java) |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 프로덕션 배포 전 잠재적 데이터 손상·보안 취약점·UX 결함 3건 미해결 |
| Solution | ddl-auto 교체 + 탈퇴 시 FCM 정리 + 5분 만료 소켓 이벤트 전송 구현 |
| Function UX Effect | 통화 5분 만료 즉시 앱에서 인식, 탈퇴 후 orphan 토큰 없음, 프로덕션 DB 스키마 안전 |
| Core Value | 앱스토어 심사 및 실서비스 운영 요건 충족 — 데이터 무결성 + UX 완결성 확보 |

---

## 1. 점검 범위

### 1.1 미구현 도메인 Gap 분석 (report / interest / language)

| 도메인 | API | 상태 | 비고 |
|--------|-----|------|------|
| report | `POST /reports` | ✅ 완전 구현 | 세션 참여자 검증, 상대방 확인, 중복 방지 |
| interest | `POST/GET/DELETE /users/me/interests` | ✅ 완전 구현 | 최대 10개 제한 포함 |
| language | `POST/GET/PUT/DELETE /users/me/languages` | ✅ 완전 구현 | replaceLanguages JPQL flush 처리 포함 |

### 1.2 프로덕션 배포 안전성 점검

| 분류 | 항목 | 심각도 | 상태 |
|------|------|--------|------|
| C1 | `ddl-auto: update` in prod | 🔴 Critical | ✅ 수정 |
| C2 | 회원 탈퇴 시 FCM 토큰 미삭제 | 🔴 Critical | ✅ 수정 |
| C3 | 5분 통화 만료 시 소켓 이벤트 미전송 | 🔴 Critical | ✅ 수정 |
| M1 | `updateLanguageLevel` dead code | 🟡 Minor | ✅ 수정 |

---

## 2. 수정 내용

### C1 — `ddl-auto: validate` (application-prod.yaml)

```yaml
# 변경 전
ddl-auto: update

# 변경 후
ddl-auto: validate
```

**이유**: `update`는 프로덕션 DB 스키마를 자동으로 수정할 수 있어 의도치 않은 컬럼 추가/변경 위험.
`validate`는 엔티티와 DB 스키마 불일치 시 서버 기동 실패 → 배포 전 스키마 검증 강제.

---

### C2 — 탈퇴 시 FCM 토큰 삭제 (UserController.java)

```java
// 변경 전
public void deleteMe() {
    userService.deleteUser(userContext.getUserId());
}

// 변경 후
public void deleteMe() {
    Long userId = userContext.getUserId();
    fcmService.deleteAllTokens(userId);
    userService.deleteUser(userId);
}
```

**이유**: 로그아웃은 `AuthController.logout()`에서 `fcmService.deleteAllTokens()` 호출하지만,
탈퇴 경로에서는 미호출 → orphan FCM 토큰 잔류, 탈퇴 사용자에게 푸시 발송 가능.

---

### C3 — 5분 통화 만료 소켓 이벤트 전송 (CallSessionScheduler.java)

```java
// 변경 전
session.end();
// TODO: 프론트엔드에 종료 이벤트 전송

// 변경 후
session.end();
Map<String, Object> payload = Map.of("sessionId", session.getId());
matchSocketHandler.emitToUser(session.getUser1().getId(), "call:expired", payload);
matchSocketHandler.emitToUser(session.getUser2().getId(), "call:expired", payload);
```

**이유**: DB만 종료하고 클라이언트 미통보 → 앱에서 통화가 종료됐는지 알 수 없음.
기존 `emitToUser` 패턴 활용, 이벤트명 `call:expired`로 `call:ended`(수동 종료)와 구분.

---

### M1 — `updateLanguageLevel` dead code 제거 (LanguageService.java)

`PATCH /users/me/languages/{id}` endpoint 없이 서비스 메서드만 존재. 호출 경로 없는 코드 제거.

---

## 3. Success Criteria

| 기준 | 상태 |
|------|------|
| report/interest/language 구현 완전성 확인 | ✅ Met (3/3 도메인 정상) |
| 프로덕션 DB 스키마 안전성 (`validate`) | ✅ Met |
| 탈퇴 시 FCM 토큰 정리 | ✅ Met |
| 통화 5분 만료 시 양측 `call:expired` emit | ✅ Met |
| Dead code 제거 | ✅ Met |
| 컴파일 성공 (`./gradlew compileJava`) | ✅ Met |

**6/6 (100%)**

---

## 4. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| FCM 삭제 Controller에서 처리 | Service가 아닌 Controller 레이어에서 `fcmService` 직접 호출 | UserService 단순 유지, AuthController 패턴 일관성 |
| `call:expired` vs `call:ended` 이벤트 분리 | 수동 종료와 자동 만료를 다른 이벤트로 구분 | 클라이언트에서 만료 UX 별도 처리 가능 |
| `MatchSocketHandler` 직접 주입 | 두 빈 모두 `@ConditionalOnProperty(redis.host)` — 순환 의존성 없음 | 기존 `emitToUser` 패턴 재사용 |
| `ddl-auto: validate` | 스키마 변경은 Flyway/Liquibase 또는 수동 마이그레이션으로 관리 권장 | 프로덕션 DB 보호 |
