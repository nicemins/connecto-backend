# TURN Credential API — Plan Document

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | turn-credential |
| 시작일 | 2026-03-14 |
| 담당 | Backend + Frontend |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | TURN 서버 자격증명이 `.env`에 하드코딩되어 JS 번들에 포함 → 앱 추출 시 크레덴셜 노출 (SEC-H1) |
| **Solution** | 백엔드에서 HMAC 기반 단기 TURN 자격증명(TTL 1시간)을 생성하는 API 제공 |
| **Function UX Effect** | 프론트엔드는 WebRTC 초기화 직전 API를 호출해 자격증명을 취득 — 번들 노출 완전 차단 |
| **Core Value** | TURN 자격증명 보안 강화로 WebRTC 인프라 오남용 방지 + SEC-H1 이슈 해결 |

---

## 1. 요구사항

### 1.1 기능 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | 인증된 사용자가 단기 TURN 자격증명을 조회할 수 있는 API가 필요하다 | 높음 |
| FR-02 | 자격증명은 HMAC-SHA1 기반 (Coturn `--use-auth-secret` 호환) 으로 생성되어야 한다 | 높음 |
| FR-03 | 자격증명의 유효 기간(TTL)은 1시간이어야 한다 | 높음 |
| FR-04 | TURN 서버 미설정 시 빈 응답(`iceServers: []`)을 반환하고 앱은 STUN only로 동작해야 한다 | 중간 |
| FR-05 | 프론트엔드는 WebRTC 피어 연결 초기화 전에 해당 API를 호출하여 자격증명을 취득해야 한다 | 높음 |
| FR-06 | 기존 `EXPO_PUBLIC_TURN_*` 환경변수 의존을 제거해야 한다 | 높음 |

### 1.2 비기능 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| NFR-01 | TURN secret은 서버 환경변수(`TURN_SECRET`)로만 관리하고 클라이언트에 노출하지 않는다 | 높음 |
| NFR-02 | API 응답 지연이 WebRTC 연결 시작을 1초 이상 지연시키지 않아야 한다 | 중간 |
| NFR-03 | TURN 미설정 환경(로컬 개발)에서도 정상 동작해야 한다 | 높음 |

---

## 2. 스코프

### In Scope
- `GET /webrtc/turn-credentials` 백엔드 API 구현
- 프론트엔드 `useWebRTC.ts` — API 호출로 TURN 자격증명 취득으로 변경
- 백엔드 `application.yaml` / `.env.local` TURN 관련 설정 추가
- 프론트엔드 `.env`에서 `EXPO_PUBLIC_TURN_*` 제거

### Out of Scope
- Coturn 서버 설치/운영
- STUN 서버 변경
- WebRTC 연결 로직 자체 수정

---

## 3. 구현 계획

### 3.1 백엔드

| 순서 | 작업 | 파일 |
|------|------|------|
| 1 | `TurnCredentialController` 생성 | `webrtc/controller/TurnCredentialController.java` |
| 2 | `TurnCredentialService` 생성 (HMAC-SHA1 자격증명 생성 로직) | `webrtc/service/TurnCredentialService.java` |
| 3 | `TurnCredentialResponse` DTO 생성 | `webrtc/dto/TurnCredentialResponse.java` |
| 4 | `application.yaml`에 TURN 설정 추가 | `application.yaml` |
| 5 | `.env.local`에 `TURN_SECRET`, `TURN_URL` 예시 추가 | `.env.local` |
| 6 | `JwtAuthenticationFilter` PUBLIC_PATHS에 TURN 엔드포인트 **비포함** (인증 필수) | `JwtAuthenticationFilter.java` |

**HMAC-SHA1 자격증명 생성 알림:**
```
username = "{expireTimestamp}:{userId}"   // expireTimestamp = now + 3600s (Unix)
password = Base64(HMAC-SHA1(TURN_SECRET, username))
```

**응답 형식:**
```json
{
  "success": true,
  "data": {
    "iceServers": [
      { "urls": "stun:stun.l.google.com:19302" },
      {
        "urls": "turn:your-turn-server.com:3478",
        "username": "1710000000:42",
        "credential": "base64encodedHMAC=="
      }
    ],
    "ttl": 3600
  }
}
```

### 3.2 프론트엔드

| 순서 | 작업 | 파일 |
|------|------|------|
| 1 | `getTurnCredentials()` API 함수 추가 | `src/api/match.ts` 또는 `src/api/webrtc.ts` (신규) |
| 2 | `useWebRTC.ts` — RTCPeerConnection 초기화 전 API 호출 | `src/hooks/useWebRTC.ts` |
| 3 | `.env`에서 `EXPO_PUBLIC_TURN_*` 제거 | `.env` |
| 4 | 프론트엔드 CLAUDE.md SEC-H1 상태 → ✅ 완료 업데이트 | `C:\connecto-app\CLAUDE.md` |

---

## 4. 기술 상세

### HMAC-SHA1 (Coturn `--use-auth-secret` 호환)

Coturn은 `--use-auth-secret` 모드에서 아래 방식으로 자격증명을 검증합니다:
- `username`: `{unix_timestamp}:{임의 식별자}` — timestamp가 만료 시간
- `password`: `Base64(HMAC-SHA1(secret, username))`

Java 구현:
```java
Mac mac = Mac.getInstance("HmacSHA1");
mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA1"));
String password = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes()));
```

### 환경변수 추가 (백엔드)

```yaml
# application.yaml
turn:
  secret: ${TURN_SECRET:}       # 미설정 시 TURN 비활성
  url: ${TURN_URL:}             # 예: turn:your-server.com:3478
```

### 로컬 개발 대응

`TURN_SECRET` 미설정 시 → `iceServers`에 STUN만 포함하여 반환 (TURN 항목 없음).
프론트엔드는 항상 API 응답을 그대로 사용하므로 별도 fallback 로직 불필요.

---

## 5. 완료 기준

- [ ] `GET /webrtc/turn-credentials` — 인증된 사용자에게 단기 자격증명 반환
- [ ] TURN_SECRET 미설정 시 STUN only 응답 정상 반환
- [ ] 프론트엔드 WebRTC 초기화 시 API 호출로 자격증명 취득
- [ ] `EXPO_PUBLIC_TURN_*` 환경변수 완전 제거
- [ ] 자격증명 TTL 1시간 검증
