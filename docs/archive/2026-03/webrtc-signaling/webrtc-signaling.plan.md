# Plan: WebRTC 시그널링 + isOfferer

## 1. 목표 (Goal)

`MatchSocketHandler`에 WebRTC 시그널링 릴레이 핸들러를 구현하고, `match:success` 이벤트에 `isOfferer` 필드를 추가한다.

**현재 상태:**
- `match:success` → `{ sessionId, webrtcChannelId }` (isOfferer 없음)
- `webrtc:join / offer / answer / ice` 핸들러 미구현
- 프론트엔드가 타이밍 기반(< 800ms) isOfferer 추정 사용 중 (workaround)

**완료 기준:**
- `match:success` → `{ sessionId, webrtcChannelId, isOfferer: boolean }` 전송
- `webrtc:join`, `webrtc:offer`, `webrtc:answer`, `webrtc:ice` 핸들러 동작
- 프론트엔드 workaround 제거 가능한 상태

---

## 2. 범위 (Scope)

### 수정 파일
- `src/main/java/com/pm/connecto/match/handler/MatchSocketHandler.java` (주 수정 대상)

### 신규 추가 없음
- 새 도메인, 엔티티, 서비스, 컨트롤러 불필요
- `MatchSocketHandler` 내부 로직 확장만으로 완성

---

## 3. 설계 방향 (Design Direction)

### 3.1 isOfferer 결정 로직

매칭 완료 시 두 클라이언트에게 각각 다른 `isOfferer` 값을 전송:

```
startAsyncMatching() 흐름:
  - matchingClient (현재 소켓, match:start 발생 시점) → isOfferer: true (Offerer)
  - waitingClient  (큐에 이미 있던 소켓)              → isOfferer: false (Answerer)
```

즉시 매칭(자신이 바로 매칭됨)의 경우도 동일 패턴 적용.

### 3.2 채널 룸 맵 (Channel Room Map)

WebRTC 시그널 릴레이를 위한 채널별 클라이언트 추적:

```java
// MatchSocketHandler 내부 필드 추가
ConcurrentHashMap<String, Set<SocketIOClient>> channelRoomMap
// key: webrtcChannelId, value: 해당 채널의 클라이언트 집합 (최대 2명)
```

### 3.3 webrtc:join 핸들러

- 클라이언트를 `channelRoomMap`에 등록
- 인증 확인 (clientUserIdMap 기반)
- 양쪽 모두 join 완료 시 로그

### 3.4 webrtc:offer / answer / ice 핸들러 (릴레이 패턴)

```
클라이언트A → 서버 수신 → channelRoomMap에서 상대방 찾기 → 상대방에게 전달
```

상대방 = 같은 channelId에 있는 나 자신이 아닌 클라이언트.

### 3.5 연결 종료 정리

`@OnDisconnect` 시 `channelRoomMap`에서 해당 클라이언트 제거.

---

## 4. Socket 이벤트 계약 (Frontend Contract)

### match:success (수정)
```json
{
  "sessionId": 123,
  "webrtcChannelId": "uuid-string",
  "isOfferer": true
}
```

### webrtc:join (클라이언트 → 서버)
```json
{
  "channelId": "uuid-string",
  "sessionId": 123
}
```

### webrtc:offer (클라이언트 → 서버 → 피어)
```json
{
  "channelId": "uuid-string",
  "sdp": { "type": "offer", "sdp": "..." }
}
```

### webrtc:answer (클라이언트 → 서버 → 피어)
```json
{
  "channelId": "uuid-string",
  "sdp": { "type": "answer", "sdp": "..." }
}
```

### webrtc:ice (클라이언트 → 서버 → 피어)
```json
{
  "channelId": "uuid-string",
  "candidate": { "candidate": "...", "sdpMid": "...", "sdpMLineIndex": 0 }
}
```

---

## 5. 구현 순서 (Implementation Order)

1. `channelRoomMap` 필드 추가
2. `match:success` 전송부에 `isOfferer` 추가 (startAsyncMatching 내부)
3. `webrtc:join` 핸들러 구현
4. `webrtc:offer` 핸들러 구현 (릴레이)
5. `webrtc:answer` 핸들러 구현 (릴레이)
6. `webrtc:ice` 핸들러 구현 (릴레이)
7. `@OnDisconnect`에 channelRoomMap 정리 로직 추가

---

## 6. 리스크 및 고려사항

| 항목 | 내용 |
|------|------|
| 동시성 | `ConcurrentHashMap` + `CopyOnWriteArraySet` 사용으로 thread-safe 보장 |
| 채널 정리 | 연결 종료 시 channelRoomMap 정리 필수 (메모리 누수 방지) |
| 인증 | webrtc:* 이벤트는 clientUserIdMap에 등록된 클라이언트만 처리 |
| 상대방 없음 | join된 클라이언트가 1명뿐일 때 offer/answer/ice 수신 시 무시 처리 |

---

## 7. 완료 조건 체크리스트

- [ ] `match:success`에 `isOfferer` 포함 여부 확인
- [ ] `webrtc:join` 수신 후 channelRoomMap 등록 확인
- [ ] `webrtc:offer` 릴레이 (A→B 전달) 동작 확인
- [ ] `webrtc:answer` 릴레이 (B→A 전달) 동작 확인
- [ ] `webrtc:ice` 릴레이 양방향 동작 확인
- [ ] 소켓 연결 종료 시 channelRoomMap 정리 확인
- [ ] 프론트엔드 타이밍 기반 workaround 제거 가능 확인

---

## 메타데이터

| 항목 | 내용 |
|------|------|
| Feature | webrtc-signaling |
| Phase | Plan |
| 작성일 | 2026-03-06 |
| 대상 파일 | MatchSocketHandler.java |
| 우선순위 | 높음 (프론트엔드 블로킹) |
