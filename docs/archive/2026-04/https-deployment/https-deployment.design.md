# HTTPS Deployment — Design Document

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | https-deployment |
| Plan 문서 | `docs/01-plan/features/https-deployment.plan.md` |
| 설계일 | 2026-03-14 |
| 담당 | Backend (Infra) + Frontend |

---

## 1. 도메인 전략

**서브도메인 분리** 방식 채택:

| 서비스 | 도메인 (예시) | 내부 라우팅 |
|--------|-------------|------------|
| REST API | `api.connecto.app` | → `app:8080` |
| Socket.IO | `socket.connecto.app` | → `app:9092` |

> 단일 서버에 두 서브도메인 A 레코드를 같은 IP로 등록.
> Let's Encrypt SAN 인증서(두 도메인 동시 발급)로 인증서 1개 관리.

---

## 2. 전체 파일 구조

```
connecto/
├── nginx/
│   └── nginx.conf              # Nginx 전체 설정
├── scripts/
│   └── init-ssl.sh             # Let's Encrypt 최초 발급 스크립트
├── docker-compose.yml          # 기존 (개발용 — 변경 없음)
├── docker-compose.prod.yml     # 프로덕션 오버라이드 (Nginx + Certbot 추가)
└── src/main/resources/
    └── application-prod.yaml   # X-Forwarded-Proto 처리 추가
```

---

## 3. Nginx 설정 상세 (`nginx/nginx.conf`)

### 3.1 구조 개요

```
events { }

http {
    # 공통 upstream
    upstream api_backend   { server app:8080; }
    upstream socket_backend { server app:9092; }

    # 1. HTTP → HTTPS 리다이렉트 (모든 도메인 공통)
    server { listen 80; return 301 https://...; }

    # 2. Certbot ACME 챌린지 (HTTP 유지 필요)
    server { listen 80; location /.well-known/acme-challenge { ... } }

    # 3. REST API HTTPS
    server { listen 443 ssl; server_name api.connecto.app; ... }

    # 4. Socket.IO WSS
    server { listen 443 ssl; server_name socket.connecto.app; ... }
}
```

### 3.2 핵심 설정값

| 항목 | 값 | 이유 |
|------|-----|------|
| `proxy_read_timeout` (Socket) | 86400s | WebSocket 장시간 연결 유지 |
| `proxy_http_version` | 1.1 | WebSocket Upgrade 필수 |
| `Connection "upgrade"` | 헤더 설정 | WebSocket 핸드쉐이크 |
| `X-Forwarded-Proto` | `$scheme` | Spring이 HTTPS 인식 (redirect 루프 방지) |
| `ssl_protocols` | TLSv1.2 TLSv1.3 | TLS 1.0/1.1 비활성 |
| `ssl_session_cache` | shared:SSL:10m | 성능 최적화 |

---

## 4. Docker Compose 변경사항

### 기존 `docker-compose.yml` 변경점

```yaml
# app 서비스: ports → expose (외부 직접 노출 제거)
app:
  expose:        # ← ports에서 변경
    - "8080"
    - "9092"
```

### 신규 `docker-compose.prod.yml` (오버라이드)

```yaml
services:
  app:
    expose: ["8080", "9092"]   # ports 오버라이드

  nginx:
    image: nginx:1.25-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - certbot_certs:/etc/letsencrypt:ro
      - certbot_www:/var/www/certbot:ro
    depends_on: [app]
    restart: unless-stopped

  certbot:
    image: certbot/certbot
    volumes:
      - certbot_certs:/etc/letsencrypt
      - certbot_www:/var/www/certbot
    # 90일마다 갱신: 별도 cron 또는 --deploy-hook

volumes:
  certbot_certs:
  certbot_www:
```

**실행 명령:**
```bash
# 초기 SSL 발급
bash scripts/init-ssl.sh api.connecto.app socket.connecto.app admin@example.com

# 프로덕션 실행
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 인증서 갱신 (crontab: 매월 1일 03:00)
0 3 1 * * docker compose run --rm certbot renew && docker compose exec nginx nginx -s reload
```

---

## 5. application-prod.yaml 변경사항

```yaml
# 추가: Spring이 X-Forwarded-Proto를 신뢰 (리다이렉트 URL HTTPS 생성)
server:
  forward-headers-strategy: native

# 추가: TURN 설정 (프로덕션 환경변수)
turn:
  secret: ${TURN_SECRET:}
  url: ${TURN_URL:}
  stun-url: stun:stun.l.google.com:19302
```

---

## 6. 프론트엔드 변경사항

### `.env.production` (신규)

```env
EXPO_PUBLIC_API_URL=https://api.connecto.app
EXPO_PUBLIC_SOCKET_URL=https://socket.connecto.app
```

> `socket.ts`는 URL이 `https://`로 시작해도 Socket.IO 클라이언트가 자동으로 WSS로 업그레이드함 — 코드 변경 불필요.

---

## 7. SSL 인증서 초기 발급 흐름 (`scripts/init-ssl.sh`)

```
1. Nginx를 HTTP only 모드로 시작 (ACME 챌린지용)
2. certbot certonly --webroot 실행
   → /.well-known/acme-challenge/ 경로로 Let's Encrypt 검증
3. 인증서 발급 완료
4. Nginx를 HTTPS 설정으로 재시작 (nginx -s reload)
```

---

## 8. 구현 순서 체크리스트

### 백엔드 인프라

- [ ] `nginx/nginx.conf` — 전체 Nginx 설정 파일 작성
- [ ] `docker-compose.prod.yml` — Nginx + Certbot 서비스 추가
- [ ] `docker-compose.yml` — app `ports` → `expose` 변경
- [ ] `scripts/init-ssl.sh` — 인증서 발급 스크립트
- [ ] `application-prod.yaml` — `server.forward-headers-strategy`, `turn:` 설정 추가
- [ ] CLAUDE.md 업데이트

### 프론트엔드

- [ ] `.env.production` — HTTPS/WSS URL
- [ ] 프론트엔드 CLAUDE.md SEC-C1/C2 → ✅ 업데이트
