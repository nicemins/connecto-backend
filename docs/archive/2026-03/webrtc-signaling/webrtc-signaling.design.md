# Design: WebRTC 시그널링 + isOfferer

## 1. 구현 대상

**파일**: `src/main/java/com/pm/connecto/match/handler/MatchSocketHandler.java`

---

## 2. 변경 사항 상세

### 2.1 필드 추가

```java
// 채널별 소켓 클라이언트 추적 (WebRTC 시그널 릴레이용)
private final Map<String, Set<SocketIOClient>> channelRoomMap = new ConcurrentHashMap<>();
```

Set은 `ConcurrentHashMap.newKeySet()`으로 thread-safe 처리.

### 2.2 isOfferer 결정 규칙

| 상황 | client (현재) | matchedClient (상대) |
|------|--------------|---------------------|
| 즉시 매칭 (startMatching 반환 matched=true) | `isOfferer: true` | N/A (폴링으로 알게 됨) |
| 비동기 매칭 (startAsyncMatching) | `isOfferer: true` (user1, 나중에 매칭된 쪽) | `isOfferer: false` (user2, 큐에서 기다리던 쪽) |

### 2.3 match:success 수정 (2곳)

**즉시 매칭 (line 185):**
```java
client.sendEvent("match:success", Map.of(
    "sessionId", response.sessionId(),
    "webrtcChannelId", response.webrtcChannelId(),
    "isOfferer", true
));
```

**비동기 매칭 - 현재 클라이언트 (line 249):**
```java
client.sendEvent("match:success", Map.of(
    "sessionId", session.getId(),
    "webrtcChannelId", session.getWebrtcChannelId(),
    "isOfferer", true
));
```

**비동기 매칭 - 상대 클라이언트 (line 263):**
```java
matchedClient.sendEvent("match:success", Map.of(
    "sessionId", session.getId(),
    "webrtcChannelId", session.getWebrtcChannelId(),
    "isOfferer", false
));
```

### 2.4 webrtc:join 핸들러

```java
@OnEvent("webrtc:join")
public void onWebrtcJoin(SocketIOClient client, Map<String, Object> data) {
    Long userId = getUserId(client);
    if (userId == null) return;

    String channelId = (String) data.get("channelId");
    if (channelId == null) return;

    channelRoomMap.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(client);
    log.info("User {} joined WebRTC channel {}", userId, channelId);
}
```

### 2.5 webrtc:offer / answer / ice 핸들러 (공통 릴레이 패턴)

```java
@OnEvent("webrtc:offer")
public void onWebrtcOffer(SocketIOClient client, Map<String, Object> data) {
    relayTopeer(client, "webrtc:offer", data);
}

@OnEvent("webrtc:answer")
public void onWebrtcAnswer(SocketIOClient client, Map<String, Object> data) {
    relayTopeer(client, "webrtc:answer", data);
}

@OnEvent("webrtc:ice")
public void onWebrtcIce(SocketIOClient client, Map<String, Object> data) {
    relayTopeer(client, "webrtc:ice", data);
}

private void relayTopeer(SocketIOClient client, String eventName, Map<String, Object> data) {
    Long userId = getUserId(client);
    if (userId == null) return;

    String channelId = (String) data.get("channelId");
    if (channelId == null) return;

    Set<SocketIOClient> peers = channelRoomMap.get(channelId);
    if (peers == null) return;

    peers.stream()
        .filter(peer -> !peer.getSessionId().equals(client.getSessionId()))
        .filter(SocketIOClient::isChannelOpen)
        .findFirst()
        .ifPresent(peer -> peer.sendEvent(eventName, data));
}
```

### 2.6 onDisconnect 정리 추가

```java
// channelRoomMap에서 해당 클라이언트 제거
channelRoomMap.values().forEach(clients -> clients.remove(client));
// 빈 채널 정리
channelRoomMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
```

---

## 3. Import 추가

```java
import java.util.Set;
```

---

## 4. 구현 파일 목록

| 파일 | 변경 유형 |
|------|----------|
| `match/handler/MatchSocketHandler.java` | 수정 |
