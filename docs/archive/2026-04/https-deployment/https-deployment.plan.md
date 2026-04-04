# HTTPS Deployment — Plan Document

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | https-deployment |
| 시작일 | 2026-03-14 |
| 담당 | Backend (Infra) + Frontend |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | 모든 API/Socket.IO가 HTTP로 통신 → MITM 공격 취약, refreshToken 평문 전송 (SEC-C1/C2) |
| **Solution** | Nginx reverse proxy + Let's Encrypt SSL로 HTTPS/WSS 전환, Docker Compose에 Nginx 서비스 추가 |
| **Function UX Effect** | API/소켓 통신 전구간 암호화 → HTTPS 해결 시 SEC-C2·SEC-M6도 연쇄 해결 |
| **Core Value** | 프로덕션 배포 전 필수 보안 요건 충족 — 앱스토어 심사 및 사용자 신뢰 확보 |

---

## 1. 요구사항

### 1.1 기능 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | REST API (`api.도메인`) HTTPS (443) 제공 | Critical |
| FR-02 | Socket.IO (`socket.도메인`) WSS (443) 제공 | Critical |
| FR-03 | HTTP(80) → HTTPS(301) 자동 리다이렉트 | Critical |
| FR-04 | Let's Encrypt 인증서 자동 갱신 (90일) | 높음 |
| FR-05 | 프론트엔드 `.env` URL → HTTPS/WSS 교체 | Critical |
| FR-06 | 앱 컨테이너는 내부 네트워크만 노출 (포트 직접 바인딩 제거) | 높음 |

### 1.2 비기능 요구사항

| ID | 요구사항 |
|----|---------|
| NFR-01 | Nginx → Spring Boot 내부 통신은 HTTP 유지 (TLS 종료는 Nginx에서) |
| NFR-02 | Socket.IO Upgrade (polling→websocket) Nginx에서 헤더 처리 필수 |
| NFR-03 | 인증서 갱신 시 서비스 중단 없어야 함 (Certbot webroot 방식) |

---

## 2. 아키텍처

```
인터넷
  ↓ HTTPS :443 / HTTP :80
[Nginx] ─── SSL 종료 ───→ REST API :8080 (Spring Boot, 내부)
         └──────────────→ Socket.IO :9092 (Spring Boot, 내부)

도메인 예시:
  api.connecto.app   → :8080
  socket.connecto.app → :9092
  (또는 단일 도메인 경로 분기)
```

---

## 3. 구현 범위

### 3.1 백엔드 (인프라)

| 순서 | 작업 | 파일 |
|------|------|------|
| 1 | Nginx 설정 파일 작성 (HTTP→HTTPS 리다이렉트, proxy_pass, WebSocket 헤더) | `nginx/nginx.conf` |
| 2 | `docker-compose.yml` — Nginx 서비스 추가, 앱 포트 내부화 | `docker-compose.yml` |
| 3 | `docker-compose.prod.yml` — 프로덕션 오버라이드 (Certbot 볼륨 마운트) | `docker-compose.prod.yml` |
| 4 | Certbot 초기 인증서 발급 스크립트 | `scripts/init-ssl.sh` |
| 5 | `application-prod.yaml` — CORS allowed-origins HTTPS로 변경 | `src/main/resources/application-prod.yaml` |
| 6 | CLAUDE.md 배포 섹션 업데이트 | `CLAUDE.md` |

### 3.2 프론트엔드

| 순서 | 작업 | 파일 |
|------|------|------|
| 1 | `.env.production` 생성 — HTTPS/WSS URL | `.env.production` |
| 2 | 프론트엔드 CLAUDE.md SEC-C1/C2 → ✅ 업데이트 | `C:\connecto-app\CLAUDE.md` |

---

## 4. Nginx 핵심 설정

### REST API (api.connecto.app)
```nginx
server {
    listen 80;
    server_name api.connecto.app;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name api.connecto.app;
    ssl_certificate     /etc/letsencrypt/live/api.connecto.app/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.connecto.app/privkey.pem;

    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Socket.IO (socket.connecto.app) — WebSocket 필수 헤더
```nginx
server {
    listen 443 ssl;
    server_name socket.connecto.app;

    location / {
        proxy_pass http://app:9092;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }
}
```

---

## 5. docker-compose 변경사항

**기존 (포트 직접 노출):**
```yaml
app:
  ports:
    - "8080:8080"
    - "9092:9092"
```

**변경 후 (내부 네트워크만, Nginx가 외부 노출):**
```yaml
app:
  expose:
    - "8080"
    - "9092"

nginx:
  image: nginx:alpine
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    - certbot_certs:/etc/letsencrypt:ro
    - certbot_www:/var/www/certbot:ro
  depends_on:
    - app
```

---

## 6. 도메인 선결 조건

| 항목 | 내용 |
|------|------|
| 도메인 | 서버 IP에 A 레코드 등록 필요 |
| 서버 | 공인 IP 필요 (Let's Encrypt HTTP 챌린지) |
| 방화벽 | 80, 443 포트 인바운드 오픈 |

> 도메인/서버가 없는 경우: 자체 서명 인증서(self-signed)로 로컬 테스트 가능

---

## 7. 완료 기준

- [ ] HTTPS로 `POST /auth/login` 정상 동작
- [ ] WSS로 Socket.IO 매칭 연결 정상 동작
- [ ] HTTP → HTTPS 301 리다이렉트 동작
- [ ] 인증서 자동 갱신 cron 설정
- [ ] 프론트엔드 `.env.production` HTTPS/WSS URL 반영
- [ ] SEC-C1, SEC-C2 → ✅ 완료 처리
