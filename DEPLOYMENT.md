# Connecto 프로덕션 배포 가이드

## 환경 변수 설정

### 필수 환경 변수

```bash
# Database
DB_HOST=your-postgres-host
DB_PORT=5432
DB_NAME=connecto
DB_USERNAME=your-db-username
DB_PASSWORD=your-db-password

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# JWT
JWT_SECRET=your-secret-key-minimum-256-bits-for-hs256-algorithm
JWT_ACCESS_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

# CORS
FRONTEND_URL=https://your-frontend-domain.com

# Logging
LOG_PATH=/app/logs
```

## Docker Compose로 배포

```bash
# 환경 변수 설정
cp .env.example .env
# .env 파일 수정

# 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f app

# 서비스 중지
docker-compose down
```

## 수동 배포

### 1. 빌드
```bash
./gradlew clean build -x test
```

### 2. JAR 실행
```bash
java -jar build/libs/connecto-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_HOST=your-host \
  --REDIS_HOST=your-redis-host \
  --JWT_SECRET=your-secret
```

## 모니터링

### 헬스 체크
```bash
curl http://localhost:8080/health
```

### 로그 확인
- 개발: 콘솔 출력
- 프로덕션: `./logs/connecto.log`, `./logs/connecto-error.log`

## 성능 최적화

### 데이터베이스
- Connection Pool: HikariCP (기본 설정)
- 인덱스: CallSession 테이블에 최적화된 인덱스 적용됨

### Redis
- Connection Pool: Lettuce (비동기)
- 분산 락: Redisson (동시성 제어)

### 매칭 엔진
- Sorted Set 기반 FIFO 대기열
- 분산 락으로 Race Condition 방지
- 타임아웃: 5분

## 보안 체크리스트

- [x] JWT 토큰 검증 (모든 보호된 API)
- [x] 통화 세션 권한 검증 (이중 체크)
- [x] CORS 설정 (프로덕션 도메인만 허용)
- [x] 비밀번호 BCrypt 암호화
- [x] Soft Delete 구현
- [x] 예외 처리 및 로깅

## 트러블슈팅

### Redis 연결 실패
- Redis 서버 상태 확인
- 네트워크 연결 확인
- 환경 변수 확인

### 매칭 실패
- Redis 대기열 상태 확인: `redis-cli ZRANGE match:queue 0 -1`
- 로그 확인: `docker-compose logs app | grep match`
