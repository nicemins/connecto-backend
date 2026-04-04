# Report: https-deployment

> Feature: HTTPS/WSS Nginx 배포 인프라 (SEC-C1/C2 해결)
> 기간: 2026-03-14
> Match Rate: 100%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | https-deployment |
| 시작일 | 2026-03-14 |
| 완료일 | 2026-04-04 |
| Match Rate | **100%** |
| 구현 파일 | 신규 4개 (nginx.conf, docker-compose.prod.yml, init-ssl.sh, application-prod.yaml) |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | API/Socket.IO HTTP 평문 통신 → MITM 취약, refreshToken 노출 (SEC-C1/C2) |
| Solution | Nginx reverse proxy + Let's Encrypt TLS 종료, Docker Compose prod 오버라이드 |
| Function UX Effect | API/소켓 전구간 암호화 — 앱스토어 심사 요건 충족 |
| Core Value | 프로덕션 배포 전 필수 보안 요건 달성 + 사용자 신뢰 확보 |

---

## 1. 구현 내용

| 파일 | 내용 |
|------|------|
| `nginx/nginx.conf` | HTTP→HTTPS 리다이렉트, REST API + Socket.IO WSS 설정, HSTS 헤더 |
| `docker-compose.prod.yml` | Nginx + Certbot 서비스, 앱 포트 내부화 (`ports: !reset []`) |
| `scripts/init-ssl.sh` | Let's Encrypt 초초 발급 + cron 갱신 안내 |
| `application-prod.yaml` | `forward-headers-strategy: native` + 전체 프로덕션 설정 통합 |

---

## 2. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| TLS 종료 Nginx | Spring Boot는 HTTP 그대로 | 백엔드 코드 변경 없음 |
| 서브도메인 분리 | api.connecto.app / socket.connecto.app | 라우팅 명확, SAN 인증서 1개 관리 |
| SAN 인증서 | 두 도메인 단일 인증서 | 갱신 관리 단순화 |
| Certbot 12h loop | 컨테이너 내 자동 갱신 + nginx reload | 서비스 중단 없는 갱신 |
| `ports: !reset []` | prod 오버라이드로 dev compose 무수정 | 개발/프로덕션 환경 분리 |
| HSTS + client_max_body_size | 설계 외 추가 | 보안 강화 + 이미지 업로드 지원 |

---

## 3. Success Criteria 최종

| 기준 | 상태 |
|------|------|
| HTTPS REST API (443) | ✅ Met |
| WSS Socket.IO (443) | ✅ Met (Upgrade 헤더) |
| HTTP → HTTPS 301 리다이렉트 | ✅ Met |
| Let's Encrypt 인증서 자동 갱신 | ✅ Met (12h loop + cron) |
| SEC-C1, SEC-C2 해결 | ✅ Met |

**5/5 (100%)**

---

## 4. Match Rate

| Category | Score |
|----------|:-----:|
| Structural | 100% |
| Functional | 100% |
| **Overall** | **100%** |

> Gap 없음. 추가 구현 2건 (HSTS, client_max_body_size) — 보안/기능 향상.
