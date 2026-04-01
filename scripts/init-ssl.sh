#!/bin/bash
# Let's Encrypt 인증서 최초 발급 스크립트
# 사용법: bash scripts/init-ssl.sh api.connecto.app socket.connecto.app admin@example.com
#
# 선결 조건:
#   - api.connecto.app, socket.connecto.app → 이 서버 IP로 DNS A 레코드 설정 완료
#   - 80, 443 포트 인바운드 오픈
#   - Docker + Docker Compose 설치

set -e

API_DOMAIN=${1:?"Usage: $0 <api-domain> <socket-domain> <email>"}
SOCKET_DOMAIN=${2:?"Usage: $0 <api-domain> <socket-domain> <email>"}
EMAIL=${3:?"Usage: $0 <api-domain> <socket-domain> <email>"}

echo "=== Connecto SSL 초기화 ==="
echo "  API 도메인:    $API_DOMAIN"
echo "  Socket 도메인: $SOCKET_DOMAIN"
echo "  이메일:        $EMAIL"
echo ""

# 1. Certbot 챌린지 디렉토리 생성
mkdir -p ./certbot/www

# 2. Nginx를 HTTP only 모드로 먼저 시작 (ACME 챌린지용 임시 설정)
echo "[1/4] HTTP-only Nginx 임시 시작..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d nginx

# 3. 인증서 발급
echo "[2/4] Let's Encrypt 인증서 발급 중..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml run --rm certbot \
  certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d "$API_DOMAIN" \
  -d "$SOCKET_DOMAIN"

echo "[3/4] 인증서 발급 완료. Nginx 재시작..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec nginx nginx -s reload

echo "[4/4] 전체 스택 시작..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

echo ""
echo "=== 완료 ==="
echo "  https://$API_DOMAIN/health 에서 동작 확인"
echo ""
echo "=== 인증서 자동 갱신 crontab 설정 (권장) ==="
echo "  crontab -e 실행 후 아래 추가:"
echo "  0 3 1 * * cd $(pwd) && docker compose -f docker-compose.yml -f docker-compose.prod.yml run --rm certbot renew && docker compose -f docker-compose.yml -f docker-compose.prod.yml exec nginx nginx -s reload"
