# Redis 설치 및 실행 가이드

## Windows에서 Redis 실행 방법

### 방법 1: WSL2 사용 (권장)

```bash
# WSL2에서 Redis 설치
wsl
sudo apt-get update
sudo apt-get install redis-server

# Redis 실행
redis-server

# 또는 백그라운드 실행
redis-server --daemonize yes
```

### 방법 2: Docker 사용 (가장 간단)

```bash
# Docker로 Redis 실행
docker run -d -p 6379:6379 --name redis redis:7-alpine

# 실행 확인
docker ps

# 로그 확인
docker logs redis
```

### 방법 3: Windows 네이티브 Redis (Memurai)

1. [Memurai](https://www.memurai.com/) 다운로드 및 설치
2. Windows 서비스로 자동 실행됨
3. 포트 6379에서 실행

### 방법 4: Redis for Windows (비공식)

```bash
# Chocolatey 사용
choco install redis-64

# 또는 직접 다운로드
# https://github.com/microsoftarchive/redis/releases
```

## Redis 실행 확인

```bash
# Redis 클라이언트로 연결 테스트
redis-cli ping
# 응답: PONG

# 또는 Docker 사용 시
docker exec -it redis redis-cli ping
```

## 애플리케이션 실행

Redis가 실행된 후:

```bash
./gradlew bootRun
```

성공 시 다음 로그가 나타납니다:
```
Socket.io server started on port 9092
```

## 문제 해결

### 포트 충돌
```bash
# 포트 6379 사용 중인지 확인 (Windows)
netstat -ano | findstr :6379

# 프로세스 종료
taskkill /PID <PID> /F
```

### Redis 연결 설정 변경

`application.yaml`에서 Redis 호스트/포트 변경:

```yaml
spring:
  data:
    redis:
      host: localhost  # 또는 다른 호스트
      port: 6379       # 또는 다른 포트
```

## 개발 환경에서 Redis 없이 테스트하기

**주의**: 매칭 기능은 Redis가 필수이므로, Redis 없이는 매칭 관련 기능을 테스트할 수 없습니다.

다만, 다른 API (인증, 프로필 등)는 Redis 없이도 동작합니다.

Redis 없이 실행하려면:
- `application.yaml`에서 `spring.data.redis.host` 제거 또는 주석 처리
- 매칭 관련 Bean들이 자동으로 비활성화됨 (`@ConditionalOnProperty`)
