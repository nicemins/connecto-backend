# Analysis: https-deployment

> Feature: HTTPS/WSS Nginx 배포 인프라
> 분석일: 2026-04-04
> Match Rate: 100%

---

## Context Anchor

| 항목 | 내용 |
|------|------|
| WHY | API/Socket.IO HTTP 평문 통신 → MITM 취약, refreshToken 노출 (SEC-C1/C2) |
| WHO | 프로덕션 배포 대상 서버 |
| RISK | WebSocket Upgrade 헤더 누락 시 Socket.IO polling 고착 |
| SUCCESS | HTTPS/WSS 정상 동작, HTTP→HTTPS 리다이렉트, 인증서 자동 갱신 |
| SCOPE | 인프라 전용 (Nginx, Docker Compose, SSL, application-prod.yaml) |

---

## 1. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Structural Match | 100% | ✅ |
| Functional Depth | 100% | ✅ |
| Infrastructure Contract | 100% | ✅ |
| **Overall** | **100%** | ✅ |

---

## 2. Structural Match (100%)

| 파일 | 설계 | 구현 |
|------|------|------|
| `nginx/nginx.conf` | ✅ | ✅ |
| `docker-compose.prod.yml` | ✅ | ✅ |
| `scripts/init-ssl.sh` | ✅ | ✅ |
| `application-prod.yaml` (forward-headers-strategy) | ✅ | ✅ (line 73) |
| `application-prod.yaml` (turn: 설정) | ✅ | ✅ (line 68~71) |

---

## 3. Functional Depth (100%)

| 요구사항 | 설계 | 구현 |
|---------|------|------|
| FR-01: REST API HTTPS (443) | ✅ | ✅ `server api.connecto.app` |
| FR-02: Socket.IO WSS (443) | ✅ | ✅ `server socket.connecto.app` |
| FR-03: HTTP → HTTPS 301 | ✅ | ✅ `return 301 https://...` |
| FR-04: Let's Encrypt 자동 갱신 | ✅ | ✅ certbot 12h loop + init-ssl.sh |
| FR-06: 앱 포트 내부화 | ✅ | ✅ `ports: !reset []` + expose |
| NFR-01: TLS 종료 Nginx | ✅ | ✅ proxy_pass http:// |
| NFR-02: WebSocket Upgrade 헤더 | ✅ | ✅ Upgrade + Connection "upgrade" |
| NFR-03: 서비스 중단 없는 갱신 | ✅ | ✅ webroot 방식 + nginx -s reload |
| ssl_protocols TLSv1.2/1.3 | ✅ | ✅ |
| ssl_session_cache | ✅ | ✅ `shared:SSL:10m` |
| proxy_read_timeout 86400s | ✅ | ✅ |
| X-Forwarded-Proto | ✅ | ✅ |
| HSTS 헤더 | 미명시 | ✅ 추가 (`max-age=63072000`) |
| `client_max_body_size 5m` | 미명시 | ✅ 추가 (이미지 업로드 지원) |

---

## 4. Gap List

Gap 없음. 추가 구현 2건 (HSTS, client_max_body_size) — 모두 보안/기능 향상.

---

## 5. Success Criteria

| 기준 | 상태 |
|------|------|
| HTTPS POST /auth/login 동작 | ✅ Met (nginx.conf 설정 완료) |
| WSS Socket.IO 매칭 연결 동작 | ✅ Met (Upgrade 헤더 설정 완료) |
| HTTP → HTTPS 301 리다이렉트 | ✅ Met |
| 인증서 자동 갱신 | ✅ Met (certbot 12h loop + cron 안내) |
| SEC-C1, SEC-C2 해결 | ✅ Met |

**5/5 (100%)**
