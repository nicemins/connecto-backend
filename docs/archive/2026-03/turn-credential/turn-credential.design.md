# TURN Credential API — Design Document

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | turn-credential |
| Plan 문서 | `docs/01-plan/features/turn-credential.plan.md` |
| 설계일 | 2026-03-14 |
| 담당 | Backend + Frontend |

---

## 1. 패키지 구조

기존 `match/` 패키지에 합류하지 않고 **`webrtc/`** 신규 패키지로 분리.
(TURN 자격증명은 매칭과 독립적으로 친구 통화에서도 사용됨)

```
com.pm.connecto/
└── webrtc/
    ├── controller/
    │   └── TurnCredentialController.java   # GET /webrtc/turn-credentials
    ├── dto/
    │   └── TurnCredentialResponse.java     # IceServer 목록 + ttl
    └── service/
        └── TurnCredentialService.java      # HMAC-SHA1 자격증명 생성
```

---

## 2. API 명세

### GET /webrtc/turn-credentials

| 항목 | 내용 |
|------|------|
| 메서드 | GET |
| 경로 | `/webrtc/turn-credentials` |
| 인증 | 필수 (JWT Bearer) |
| 설명 | 단기 TURN 자격증명 반환 (TTL 1시간) |

**응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "iceServers": [
      {
        "urls": "stun:stun.l.google.com:19302"
      },
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

**TURN 미설정 시 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "iceServers": [
      { "urls": "stun:stun.l.google.com:19302" }
    ],
    "ttl": 3600
  }
}
```

---

## 3. 도메인 모델 (DTO)

### TurnCredentialResponse

```java
public record TurnCredentialResponse(
    List<IceServer> iceServers,
    int ttl
) {
    public record IceServer(
        String urls,
        String username,   // nullable — STUN은 null
        String credential  // nullable — STUN은 null
    ) {}
}
```

---

## 4. 서비스 로직

### HMAC-SHA1 자격증명 생성 (Coturn `--use-auth-secret` 호환)

```
TTL = 3600 (1시간)
expireTimestamp = System.currentTimeMillis() / 1000 + TTL

username = "{expireTimestamp}:{userId}"
// 예: "1710003600:42"

password = Base64.encode(HMAC-SHA1(TURN_SECRET, username))
```

### TurnCredentialService 의사코드

```
generateCredentials(userId):
    if TURN_SECRET 미설정:
        return iceServers = [STUN only]

    expireTimestamp = now() + 3600
    username = expireTimestamp + ":" + userId
    password = Base64(HmacSHA1(TURN_SECRET, username))

    iceServers = [
        IceServer(STUN_URL),
        IceServer(TURN_URL, username, password)
    ]
    return TurnCredentialResponse(iceServers, 3600)
```

---

## 5. 설정 (application.yaml 추가)

```yaml
turn:
  secret: ${TURN_SECRET:}        # 미설정 시 TURN 비활성
  url: ${TURN_URL:}              # 예: turn:example.com:3478
  stun-url: stun:stun.l.google.com:19302  # 기본 STUN
```

### .env.local 추가 항목

```env
TURN_SECRET=         # Coturn --static-auth-secret 값 (로컬 개발 시 비워도 됨)
TURN_URL=            # 예: turn:your-server.com:3478 (로컬 개발 시 비워도 됨)
```

---

## 6. 인증 필터 변경

`JwtAuthenticationFilter.PUBLIC_PATHS`에 `/webrtc/turn-credentials` **추가하지 않음**.
→ 인증된 사용자만 TURN 자격증명 취득 가능 (오남용 방지).

---

## 7. 프론트엔드 연동 설계

### 신규 API 함수 (`src/api/webrtc.ts` 신규 파일)

```typescript
// GET /webrtc/turn-credentials
export async function getTurnCredentials(): Promise<{
  iceServers: RTCIceServer[];
  ttl: number;
}> {
  const res = await apiClient.get('/webrtc/turn-credentials');
  return res.data.data;
}
```

### useWebRTC.ts 변경점

```typescript
// Before (환경변수 직접 사용)
const iceServers = buildIceServers(); // EXPO_PUBLIC_TURN_* 읽기

// After (API 호출)
const { iceServers } = await getTurnCredentials();
const pc = new RTCPeerConnection({ iceServers });
```

### .env 제거 항목

```
# 제거
EXPO_PUBLIC_TURN_URL=
EXPO_PUBLIC_TURN_USERNAME=
EXPO_PUBLIC_TURN_CREDENTIAL=
```

---

## 8. 구현 순서

### 백엔드

- [ ] `application.yaml` — `turn:` 설정 블록 추가
- [ ] `TurnCredentialResponse.java` — DTO (IceServer nested record)
- [ ] `TurnCredentialService.java` — HMAC-SHA1 자격증명 생성
- [ ] `TurnCredentialController.java` — `GET /webrtc/turn-credentials`
- [ ] `.env.local` — TURN 관련 주석/예시 추가
- [ ] CLAUDE.md 구현 현황 업데이트

### 프론트엔드 (별도 세션)

- [ ] `src/api/webrtc.ts` — `getTurnCredentials()` 신규
- [ ] `src/hooks/useWebRTC.ts` — API 호출로 변경
- [ ] `.env` — `EXPO_PUBLIC_TURN_*` 제거
- [ ] 프론트엔드 CLAUDE.md SEC-H1 → ✅ 업데이트
