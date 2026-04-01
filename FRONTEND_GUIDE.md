# 프론트엔드 Socket.io 구현 가이드

## 설치 필요 패키지

```bash
npm install socket.io-client
npm install moti  # Ripple 애니메이션용
```

## 파일 구조

```
src/
├── api/
│   └── socket.ts              # Socket.io 클라이언트 설정
├── hooks/
│   └── useSocketMatching.ts   # 매칭 훅 (Socket.io + Fallback)
└── screens/
    └── HomeScreen.tsx         # 홈 화면 (Ripple 애니메이션 포함)
```

## 환경 변수 (.env)

```env
EXPO_PUBLIC_API_URL=http://localhost:8080
EXPO_PUBLIC_SOCKET_URL=http://localhost:9092
```

---

## 1. src/api/socket.ts

```typescript
import { io, Socket } from 'socket.io-client';
import { authStore } from '../store/authStore';

const SOCKET_URL = process.env.EXPO_PUBLIC_SOCKET_URL || 'http://localhost:9092';

let socket: Socket | null = null;

/**
 * Socket.io 클라이언트 인스턴스 생성 및 연결
 * - authStore의 accessToken을 사용하여 인증
 */
export const connectSocket = (): Socket | null => {
  if (socket?.connected) {
    return socket;
  }

  const accessToken = authStore.getState().accessToken;
  if (!accessToken) {
    console.warn('No access token available for socket connection');
    return null;
  }

  socket = io(SOCKET_URL, {
    auth: {
      token: accessToken,
    },
    query: {
      token: accessToken,  // 백엔드 호환을 위해 쿼리 파라미터로도 전송
    },
    transports: ['websocket', 'polling'],
    reconnection: true,
    reconnectionDelay: 1000,
    reconnectionAttempts: 5,
  });

  socket.on('connect', () => {
    console.log('Socket connected:', socket?.id);
  });

  socket.on('disconnect', (reason) => {
    console.log('Socket disconnected:', reason);
  });

  socket.on('connect_error', (error) => {
    console.error('Socket connection error:', error);
  });

  return socket;
};

/**
 * Socket.io 연결 해제
 */
export const disconnectSocket = () => {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
};

/**
 * 현재 Socket 인스턴스 가져오기
 */
export const getSocket = (): Socket | null => {
  return socket;
};

/**
 * Socket 재연결 (토큰 갱신 후)
 */
export const reconnectSocket = () => {
  disconnectSocket();
  return connectSocket();
};
```

---

## 2. src/hooks/useSocketMatching.ts

```typescript
import { useEffect, useRef, useState, useCallback } from 'react';
import { connectSocket, disconnectSocket, getSocket } from '../api/socket';
import { authStore } from '../store/authStore';

const API_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080';

interface MatchSuccessData {
  sessionId: number;
  webrtcChannelId: string;
}

interface UseSocketMatchingReturn {
  isMatching: boolean;
  isConnected: boolean;
  startMatching: () => void;
  cancelMatching: () => void;
  onMatchSuccess: (callback: (data: MatchSuccessData) => void) => void;
  onMatchError: (callback: (error: { code: string; message: string }) => void) => void;
}

/**
 * Socket.io 기반 매칭 훅
 * - Socket.io 실시간 통신 사용
 * - 실패 시 HTTP Polling으로 자동 전환 (Fallback)
 */
export const useSocketMatching = (): UseSocketMatchingReturn => {
  const [isMatching, setIsMatching] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [useFallback, setUseFallback] = useState(false);

  const socketRef = useRef<ReturnType<typeof getSocket> | null>(null);
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const matchSuccessCallbackRef = useRef<((data: MatchSuccessData) => void) | null>(null);
  const matchErrorCallbackRef = useRef<((error: { code: string; message: string }) => void) | null>(null);

  // Socket 연결
  useEffect(() => {
    const socket = connectSocket();
    socketRef.current = socket;

    if (socket) {
      socket.on('connect', () => {
        setIsConnected(true);
        setUseFallback(false);
      });

      socket.on('disconnect', () => {
        setIsConnected(false);
        // 연결 끊김 시 Fallback으로 전환
        if (isMatching) {
          console.warn('Socket disconnected, switching to polling fallback');
          setUseFallback(true);
          startPollingFallback();
        }
      });

      socket.on('connect_error', () => {
        setIsConnected(false);
        // 연결 실패 시 Fallback으로 전환
        if (isMatching) {
          console.warn('Socket connection failed, switching to polling fallback');
          setUseFallback(true);
          startPollingFallback();
        }
      });

      // 매칭 성공 이벤트
      socket.on('match:success', (data: MatchSuccessData) => {
        setIsMatching(false);
        stopPollingFallback();
        matchSuccessCallbackRef.current?.(data);
      });

      // 매칭 에러 이벤트
      socket.on('match:error', (error: { code: string; message: string }) => {
        setIsMatching(false);
        stopPollingFallback();
        matchErrorCallbackRef.current?.(error);
      });

      // 매칭 취소 확인
      socket.on('match:cancelled', () => {
        setIsMatching(false);
        stopPollingFallback();
      });
    }

    return () => {
      disconnectSocket();
      stopPollingFallback();
    };
  }, []);

  // Polling Fallback (HTTP GET /match/status)
  const startPollingFallback = useCallback(() => {
    if (pollingIntervalRef.current) {
      return; // 이미 실행 중
    }

    console.log('Starting polling fallback...');
    const accessToken = authStore.getState().accessToken;

    pollingIntervalRef.current = setInterval(async () => {
      try {
        const response = await fetch(`${API_URL}/match/status`, {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
        });

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        if (data.success && data.data.status === 'MATCHED') {
          setIsMatching(false);
          stopPollingFallback();
          matchSuccessCallbackRef.current?.({
            sessionId: data.data.sessionId,
            webrtcChannelId: data.data.webrtcChannelId,
          });
        }
      } catch (error) {
        console.error('Polling fallback error:', error);
      }
    }, 2000); // 2초마다 확인
  }, []);

  const stopPollingFallback = useCallback(() => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  }, []);

  // 매칭 시작
  const startMatching = useCallback(() => {
    const socket = socketRef.current;
    
    if (!socket || !socket.connected) {
      // Socket 연결 실패 시 즉시 Fallback으로 전환
      console.warn('Socket not connected, using polling fallback');
      setUseFallback(true);
      setIsMatching(true);
      
      // HTTP POST /match/start로 시작
      const accessToken = authStore.getState().accessToken;
      fetch(`${API_URL}/match/start`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
      })
        .then((response) => response.json())
        .then((data) => {
          if (data.success && data.data.matched) {
            // 즉시 매칭 성공
            setIsMatching(false);
            matchSuccessCallbackRef.current?.({
              sessionId: data.data.sessionId,
              webrtcChannelId: data.data.webrtcChannelId,
            });
          } else {
            // 대기 중 - Polling 시작
            startPollingFallback();
          }
        })
        .catch((error) => {
          console.error('Failed to start matching:', error);
          setIsMatching(false);
          matchErrorCallbackRef.current?.({
            code: 'MATCHING_FAILED',
            message: '매칭 시작에 실패했습니다.',
          });
        });
      return;
    }

    // Socket.io로 매칭 시작
    setIsMatching(true);
    setUseFallback(false);
    stopPollingFallback();
    socket.emit('match:start');
  }, [startPollingFallback, stopPollingFallback]);

  // 매칭 취소
  const cancelMatching = useCallback(() => {
    const socket = socketRef.current;

    setIsMatching(false);
    stopPollingFallback();

    if (socket && socket.connected && !useFallback) {
      socket.emit('match:cancel');
    } else {
      // Fallback 모드일 때 HTTP로 취소
      const accessToken = authStore.getState().accessToken;
      fetch(`${API_URL}/match/cancel`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
      }).catch((error) => {
        console.error('Failed to cancel matching:', error);
      });
    }
  }, [useFallback, stopPollingFallback]);

  // 매칭 성공 콜백 등록
  const onMatchSuccess = useCallback((callback: (data: MatchSuccessData) => void) => {
    matchSuccessCallbackRef.current = callback;
  }, []);

  // 매칭 에러 콜백 등록
  const onMatchError = useCallback((callback: (error: { code: string; message: string }) => void) => {
    matchErrorCallbackRef.current = callback;
  }, []);

  return {
    isMatching,
    isConnected,
    startMatching,
    cancelMatching,
    onMatchSuccess,
    onMatchError,
  };
};
```

---

## 3. src/screens/HomeScreen.tsx (예시)

```typescript
import React, { useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { MotiView } from 'moti';
import { useSocketMatching } from '../hooks/useSocketMatching';

export const HomeScreen = () => {
  const { isMatching, isConnected, startMatching, cancelMatching, onMatchSuccess, onMatchError } = useSocketMatching();

  useEffect(() => {
    // 매칭 성공 시 처리
    onMatchSuccess((data) => {
      console.log('Match successful!', data);
      // 네비게이션 또는 상태 업데이트
      // navigation.navigate('CallScreen', { sessionId: data.sessionId, channelId: data.webrtcChannelId });
    });

    // 매칭 에러 시 처리
    onMatchError((error) => {
      console.error('Match error:', error);
      // 에러 메시지 표시
    });
  }, [onMatchSuccess, onMatchError]);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Connecto</Text>
      <Text style={styles.subtitle}>
        {isConnected ? 'Socket.io 연결됨' : 'Socket.io 연결 안됨 (Polling 사용)'}
      </Text>

      <View style={styles.buttonContainer}>
        {isMatching ? (
          <>
            {/* Ripple 애니메이션 */}
            <MotiView
              from={{ scale: 1, opacity: 1 }}
              animate={{ scale: 4, opacity: 0 }}
              transition={{
                type: 'timing',
                duration: 2000,
                loop: true,
                repeatReverse: false,
              }}
              style={styles.ripple}
            />
            <MotiView
              from={{ scale: 1, opacity: 1 }}
              animate={{ scale: 4, opacity: 0 }}
              transition={{
                type: 'timing',
                duration: 2000,
                delay: 1000,
                loop: true,
                repeatReverse: false,
              }}
              style={styles.ripple}
            />

            <TouchableOpacity
              style={[styles.button, styles.cancelButton]}
              onPress={cancelMatching}
            >
              <Text style={styles.buttonText}>매칭 취소</Text>
            </TouchableOpacity>
            <Text style={styles.matchingText}>매칭 중...</Text>
          </>
        ) : (
          <TouchableOpacity
            style={[styles.button, styles.findButton]}
            onPress={startMatching}
          >
            <Text style={styles.buttonText}>Find Partner</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
    padding: 20,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 40,
  },
  buttonContainer: {
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
  },
  button: {
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 25,
    minWidth: 200,
    alignItems: 'center',
  },
  findButton: {
    backgroundColor: '#007AFF',
  },
  cancelButton: {
    backgroundColor: '#FF3B30',
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
  matchingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  ripple: {
    position: 'absolute',
    width: 200,
    height: 200,
    borderRadius: 100,
    backgroundColor: '#007AFF',
    opacity: 0.3,
  },
});
```

---

## 사용 방법

1. **패키지 설치**
   ```bash
   npm install socket.io-client moti
   ```

2. **환경 변수 설정** (.env)
   ```env
   EXPO_PUBLIC_API_URL=http://localhost:8080
   EXPO_PUBLIC_SOCKET_URL=http://localhost:9092
   ```

3. **HomeScreen에서 사용**
   ```typescript
   const { isMatching, startMatching, cancelMatching, onMatchSuccess } = useSocketMatching();
   
   useEffect(() => {
     onMatchSuccess((data) => {
       // 매칭 성공 처리
     });
   }, [onMatchSuccess]);
   ```

---

## 주요 특징

✅ **Socket.io 실시간 통신** - 즉시 매칭 알림  
✅ **Fallback 자동 전환** - Socket 실패 시 HTTP Polling으로 전환  
✅ **Ripple 애니메이션** - Moti로 매칭 중 시각적 피드백  
✅ **Cleanup 보장** - 화면 벗어날 때 소켓 및 Polling 정리
