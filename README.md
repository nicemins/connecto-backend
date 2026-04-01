# Connecto Backend

> **"지금, 누군가와 5분만 이야기해요."**
> *"Talk to someone for just 5 minutes, right now."*
> *「今、誰かと5分だけ話しましょう。」*

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.5.9-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/Redis-6.x-DC382D?style=flat-square&logo=redis&logoColor=white" />
  <img src="https://img.shields.io/badge/Socket.IO-2.0.3-010101?style=flat-square&logo=socket.io" />
</p>

---

## 언어 / Language / 言語

- [한국어](#한국어)
- [English](#english)
- [日本語](#日本語)

---

# 한국어

## 프로젝트 소개

**Connecto 백엔드**는 5분 익명 보이스 채팅 기반 실시간 매칭 및 언어 교환 플랫폼의 서버 구현체입니다.  
Redis 기반 매칭 큐, Socket.IO WebRTC 시그널링, JWT 인증, 1:1 채팅, FCM 푸시 알림을 제공합니다.

## 주요 기능

| 기능 | 설명 |
|------|------|
| 실시간 매칭 | Redis FIFO 큐 + Redisson 분산 락으로 공정하고 안전한 매칭 |
| WebRTC 시그널링 | Socket.IO 기반 Offer/Answer/ICE 릴레이, STUN/TURN 자격증명 발급 |
| 1:1 채팅 | 텍스트·이미지 메시지, 읽음 처리, 미읽음 카운트 |
| 친구 시스템 | 친구 요청·수락·거절·삭제, 온라인 상태 알림 |
| 차단 | 친구 차단·해제, 매칭 대기열 자동 제외 |
| 프로필 | 닉네임, 소개글, 프로필 이미지 (AWS S3) |
| 언어 / 관심사 | 언어 설정(ISO 코드, 최대 10개), 관심사 태그 |
| 소셜 로그인 | Google OAuth 2.0 ID Token 검증 |
| 푸시 알림 | Firebase Cloud Messaging (친구 요청·통화 요청 알림) |
| 신고 | 세션 기반 사용자 신고 |
| Rate Limit | IP 기반 — 로그인 분당 10회, 회원가입 시간당 5회 |

## 기술 스택

| 분류 | 기술 | 비고 |
|------|------|------|
| Language | Java 17 | |
| Framework | Spring Boot 3.5.9 | |
| Build | Gradle | |
| DB (운영) | PostgreSQL 17 | port 5432 |
| Cache / 분산 락 | Redis 6.x + Redisson | port 6379 |
| Auth | JWT (jjwt 0.12.3) | Spring Security 없음, ThreadLocal UserContext |
| Realtime | Socket.IO (netty-socketio 2.0.3) | port 9092 |
| 파일 스토리지 | AWS S3 (SDK v2) | 프로필·채팅 이미지 |
| 소셜 로그인 | Google API Client 2.2.0 | |
| 푸시 알림 | Firebase Admin SDK 9.2.0 | 미설정 시 FCM 비활성 |
| API 문서 | Springdoc OpenAPI 3 (Swagger UI) | |
| 컨테이너 | Docker + docker-compose | |

## 아키텍처

```
클라이언트
  ├── REST API (port 8080)  ←→  Spring Boot
  │                               ├── JwtAuthenticationFilter
  │                               ├── AuthRateLimitInterceptor (IP 기반)
  │                               ├── GlobalExceptionHandler
  │                               └── Controller → Service → Repository
  │
  └── Socket.IO (port 9092) ←→  netty-socketio
                                  ├── MatchSocketHandler  (매칭 + WebRTC 시그널링)
                                  └── ChatSocketHandler   (1:1 채팅)

인프라
  ├── PostgreSQL  — 영속 데이터
  ├── Redis       — 매칭 큐 + Rate Limit
  └── AWS S3      — 프로필 이미지 + 채팅 이미지
```

## 프로젝트 구조

```
src/main/java/com/pm/connecto/
├── auth/           # JWT 필터, 토큰 프로바이더, 인증 서비스
├── call/           # 통화 요청·종료·재연결
├── chat/           # 1:1 채팅 (REST + Socket.IO)
├── common/         # 공통 응답, 예외, S3, 소켓 유틸
├── friend/         # 친구 요청·수락·차단
├── health/         # 헬스 체크
├── interest/       # 관심사 태그
├── language/       # 언어 설정
├── match/          # 매칭 큐, WebRTC 시그널링, Socket.IO
├── notification/   # FCM 푸시 알림
├── profile/        # 프로필·이미지
├── report/         # 신고
└── user/           # 회원가입·로그인·소셜 로그인
```

## 시작하기

### 사전 요구사항

- Java 17 (Eclipse Adoptium 권장)
- PostgreSQL 17 (로컬, port 5432)
- Redis (로컬 또는 Docker, port 6379)
- Git Bash (Windows)

### 설치 및 실행

```bash
# 1. 저장소 클론
git clone https://github.com/your-org/connecto.git
cd connecto

# 2. .env.local 파일 생성 (아래 환경 변수 섹션 참조)

# 3. DB 생성
psql -U postgres -c "CREATE DATABASE connecto;"

# 4. Redis 컨테이너 시작 (매칭 기능 포함)
docker-compose up -d

# 5. 앱 실행 (Git Bash)
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.16.8-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
bash run-local.sh
```

> **주의:** `run-local.sh`는 CRLF 줄바꿈 문제로 PowerShell에서 직접 실행하면 오류 납니다. **Git Bash**를 사용하세요.

### 환경 변수 (.env.local)

```env
DB_PASSWORD=your_postgres_password

GOOGLE_ANDROID_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_WEB_CLIENT_ID=xxx.apps.googleusercontent.com

AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_S3_BUCKET=connecto-dev
AWS_REGION=ap-northeast-2

# 미설정 시 FCM 비활성 (로컬 개발 OK)
FIREBASE_SERVICE_ACCOUNT_JSON=
```

### 접속 확인

| 서비스 | URL |
|--------|-----|
| REST API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Socket.IO | ws://localhost:9092 |
| Health Check | http://localhost:8080/health |

## 주요 API

전체 명세는 **http://localhost:8080/swagger-ui.html** 에서 확인할 수 있습니다.

| 도메인 | 경로 | 설명 |
|--------|------|------|
| 인증 | `POST /auth/signup` | 회원가입 |
| 인증 | `POST /auth/login` | 로그인 (accessToken + refreshToken 쿠키) |
| 인증 | `POST /auth/social-login` | Google OAuth 로그인 |
| 사용자 | `GET /users/me` | 내 정보 통합 조회 |
| 프로필 | `PATCH /users/me/profile/image` | 프로필 이미지 수정 (S3) |
| 매칭 | `POST /match/start` | 매칭 대기열 진입 |
| 통화 | `POST /call/request/{friendId}` | 친구에게 통화 요청 |
| 친구 | `POST /friends/request` | 친구 요청 |
| 채팅 | `GET /chat/rooms/{id}/messages` | 메시지 히스토리 (페이징) |
| 알림 | `POST /users/me/device-token` | FCM 토큰 등록 |

## Socket.IO 이벤트

소켓 연결 시 JWT 전달:
```
Authorization: Bearer <accessToken>  또는  ?token=<accessToken>
```

| 이벤트 | 방향 | 설명 |
|--------|------|------|
| `match:start` | 클→서 | 매칭 요청 |
| `match:success` | 서→클 | 매칭 완료 `{ sessionId, webrtcChannelId, isOfferer }` |
| `webrtc:offer/answer/ice` | 클→서 | WebRTC 시그널링 |
| `call:incoming` | 서→클 | 친구 통화 수신 |
| `call:ended` | 서→클 | 상대방 통화 종료 |
| `chat:send` | 클→서 | 메시지 전송 `{ roomId, content }` |
| `chat:receive` | 서→룸 | 메시지 브로드캐스트 |
| `chat:read` | 양방향 | 읽음 처리 / 읽음 알림 |
| `chat:typing` | 양방향 | 타이핑 인디케이터 |

## 프론트엔드 연동

| 서버 | 포트 | 설명 |
|------|------|------|
| REST API | 8080 | 모든 HTTP 엔드포인트 |
| Socket.IO | 9092 | 매칭, WebRTC, 채팅, 친구 상태 |

> 프론트엔드: React Native + Expo, [connecto-app](../connecto-app)

---

# English

## Overview

**Connecto Backend** is the server implementation of a real-time matching and language exchange platform based on 5-minute anonymous voice chat.  
It provides Redis-based matching queue, Socket.IO WebRTC signaling, JWT authentication, 1:1 chat, and FCM push notifications.

## Key Features

| Feature | Description |
|---------|-------------|
| Real-time Matching | Redis FIFO queue + Redisson distributed lock for fair, safe matching |
| WebRTC Signaling | Socket.IO Offer/Answer/ICE relay, STUN/TURN credential issuance |
| 1:1 Chat | Text & image messages, read receipts, unread count |
| Friend System | Friend requests, accept/reject/remove, online status notifications |
| Block | Block/unblock users, auto-excluded from matching queue |
| Profile | Nickname, bio, profile image (AWS S3) |
| Language / Interests | Language settings (ISO codes, up to 10), interest tags |
| Social Login | Google OAuth 2.0 ID Token verification |
| Push Notifications | Firebase Cloud Messaging (friend request / call request alerts) |
| Reports | Session-based user reporting |
| Rate Limiting | IP-based: 10 logins/min, 5 signups/hour |

## Tech Stack

| Category | Technology | Note |
|----------|------------|------|
| Language | Java 17 | |
| Framework | Spring Boot 3.5.9 | |
| Build | Gradle | |
| DB (Production) | PostgreSQL 17 | port 5432 |
| Cache / Distributed Lock | Redis 6.x + Redisson | port 6379 |
| Auth | JWT (jjwt 0.12.3) | No Spring Security, ThreadLocal UserContext |
| Realtime | Socket.IO (netty-socketio 2.0.3) | port 9092 |
| File Storage | AWS S3 (SDK v2) | Profile & chat images |
| Social Login | Google API Client 2.2.0 | |
| Push Notifications | Firebase Admin SDK 9.2.0 | Disabled if not configured |
| API Docs | Springdoc OpenAPI 3 (Swagger UI) | |
| Container | Docker + docker-compose | |

## Architecture

```
Client
  ├── REST API (port 8080)  ←→  Spring Boot
  │                               ├── JwtAuthenticationFilter
  │                               ├── AuthRateLimitInterceptor (IP-based)
  │                               ├── GlobalExceptionHandler
  │                               └── Controller → Service → Repository
  │
  └── Socket.IO (port 9092) ←→  netty-socketio
                                  ├── MatchSocketHandler  (matching + WebRTC signaling)
                                  └── ChatSocketHandler   (1:1 chat)

Infrastructure
  ├── PostgreSQL  — persistent data
  ├── Redis       — matching queue + rate limit
  └── AWS S3      — profile images + chat images
```

## Project Structure

```
src/main/java/com/pm/connecto/
├── auth/           # JWT filter, token provider, auth service
├── call/           # Call request, end, rematch
├── chat/           # 1:1 chat (REST + Socket.IO)
├── common/         # Shared response, exceptions, S3, socket utils
├── friend/         # Friend requests, accept, block
├── health/         # Health check endpoint
├── interest/       # Interest tags
├── language/       # Language settings
├── match/          # Matching queue, WebRTC signaling, Socket.IO
├── notification/   # FCM push notifications
├── profile/        # Profile and image management
├── report/         # User reports
└── user/           # Signup, login, social login
```

## Getting Started

### Prerequisites

- Java 17 (Eclipse Adoptium recommended)
- PostgreSQL 17 (local, port 5432)
- Redis (local or Docker, port 6379)
- Git Bash (Windows)

### Installation & Running

```bash
# 1. Clone the repository
git clone https://github.com/your-org/connecto.git
cd connecto

# 2. Create .env.local (see Environment Variables section below)

# 3. Create the database
psql -U postgres -c "CREATE DATABASE connecto;"

# 4. Start Redis container (required for matching)
docker-compose up -d

# 5. Run the app (Git Bash)
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.16.8-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
bash run-local.sh
```

> Do **not** run `run-local.sh` in PowerShell — it will fail due to CRLF line endings. Use **Git Bash**.

### Environment Variables (.env.local)

```env
DB_PASSWORD=your_postgres_password

GOOGLE_ANDROID_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_WEB_CLIENT_ID=xxx.apps.googleusercontent.com

AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_S3_BUCKET=connecto-dev
AWS_REGION=ap-northeast-2

# Leave empty to disable FCM (OK for local development)
FIREBASE_SERVICE_ACCOUNT_JSON=
```

### Verify

| Service | URL |
|---------|-----|
| REST API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Socket.IO | ws://localhost:9092 |
| Health Check | http://localhost:8080/health |

## Key APIs

Full documentation available at **http://localhost:8080/swagger-ui.html**

| Domain | Path | Description |
|--------|------|-------------|
| Auth | `POST /auth/signup` | Register |
| Auth | `POST /auth/login` | Login (accessToken + refreshToken cookie) |
| Auth | `POST /auth/social-login` | Google OAuth login |
| User | `GET /users/me` | Get full profile |
| Profile | `PATCH /users/me/profile/image` | Update profile image (S3) |
| Match | `POST /match/start` | Enter matching queue |
| Call | `POST /call/request/{friendId}` | Request a call to a friend |
| Friend | `POST /friends/request` | Send friend request |
| Chat | `GET /chat/rooms/{id}/messages` | Get message history (paginated) |
| Notification | `POST /users/me/device-token` | Register FCM token |

## Socket.IO Events

Pass JWT when connecting:
```
Authorization: Bearer <accessToken>  or  ?token=<accessToken>
```

| Event | Direction | Description |
|-------|-----------|-------------|
| `match:start` | Client → Server | Request matching |
| `match:success` | Server → Client | Match found `{ sessionId, webrtcChannelId, isOfferer }` |
| `webrtc:offer/answer/ice` | Client → Server | WebRTC signaling |
| `call:incoming` | Server → Client | Incoming friend call |
| `call:ended` | Server → Client | Other party ended the call |
| `chat:send` | Client → Server | Send message `{ roomId, content }` |
| `chat:receive` | Server → Room | Broadcast message |
| `chat:read` | Both ways | Read receipt |
| `chat:typing` | Both ways | Typing indicator |

## Frontend Integration

| Server | Port | Description |
|--------|------|-------------|
| REST API | 8080 | All HTTP endpoints |
| Socket.IO | 9092 | Matching, WebRTC, chat, friend status |

> Frontend: React Native + Expo, [connecto-app](../connecto-app)

---

# 日本語

## プロジェクト概要

**Connectoバックエンド**は、5分間の匿名ボイスチャットをベースにしたリアルタイムマッチング・語学交換プラットフォームのサーバー実装です。  
Redisベースのマッチングキュー、Socket.IO WebRTCシグナリング、JWT認証、1対1チャット、FCMプッシュ通知を提供します。

## 主な機能

| 機能 | 説明 |
|------|------|
| リアルタイムマッチング | Redis FIFOキュー + Redisson分散ロックによる公平・安全なマッチング |
| WebRTCシグナリング | Socket.IO Offer/Answer/ICEリレー、STUN/TURNクレデンシャル発行 |
| 1対1チャット | テキスト・画像メッセージ、既読処理、未読数カウント |
| フレンドシステム | フレンド申請・承認・拒否・削除、オンライン状態通知 |
| ブロック | ユーザーのブロック・解除、マッチングキューからの自動除外 |
| プロフィール | ニックネーム、自己紹介、プロフィール画像（AWS S3） |
| 言語・興味関心 | 言語設定（ISOコード、最大10件）、興味タグ |
| ソーシャルログイン | Google OAuth 2.0 IDトークン検証 |
| プッシュ通知 | Firebase Cloud Messaging（フレンド申請・通話リクエスト通知） |
| 通報 | セッションベースのユーザー通報 |
| レート制限 | IPベース：ログイン10回/分、会員登録5回/時 |

## 技術スタック

| カテゴリ | 技術 | 備考 |
|----------|------|------|
| 言語 | Java 17 | |
| フレームワーク | Spring Boot 3.5.9 | |
| ビルド | Gradle | |
| DB（本番） | PostgreSQL 17 | port 5432 |
| キャッシュ / 分散ロック | Redis 6.x + Redisson | port 6379 |
| 認証 | JWT（jjwt 0.12.3） | Spring Securityなし、ThreadLocal UserContext |
| リアルタイム | Socket.IO（netty-socketio 2.0.3） | port 9092 |
| ファイルストレージ | AWS S3（SDK v2） | プロフィール・チャット画像 |
| ソーシャルログイン | Google API Client 2.2.0 | |
| プッシュ通知 | Firebase Admin SDK 9.2.0 | 未設定でFCM無効 |
| APIドキュメント | Springdoc OpenAPI 3（Swagger UI） | |
| コンテナ | Docker + docker-compose | |

## アーキテクチャ

```
クライアント
  ├── REST API（port 8080） ←→  Spring Boot
  │                               ├── JwtAuthenticationFilter
  │                               ├── AuthRateLimitInterceptor（IPベース）
  │                               ├── GlobalExceptionHandler
  │                               └── Controller → Service → Repository
  │
  └── Socket.IO（port 9092）←→  netty-socketio
                                  ├── MatchSocketHandler（マッチング + WebRTCシグナリング）
                                  └── ChatSocketHandler（1対1チャット）

インフラ
  ├── PostgreSQL  — 永続データ
  ├── Redis       — マッチングキュー + レート制限
  └── AWS S3      — プロフィール画像 + チャット画像
```

## プロジェクト構成

```
src/main/java/com/pm/connecto/
├── auth/           # JWTフィルター、トークンプロバイダー、認証サービス
├── call/           # 通話リクエスト・終了・再通話
├── chat/           # 1対1チャット（REST + Socket.IO）
├── common/         # 共通レスポンス、例外、S3、ソケットユーティリティ
├── friend/         # フレンド申請・承認・ブロック
├── health/         # ヘルスチェックエンドポイント
├── interest/       # 興味・関心タグ
├── language/       # 言語設定
├── match/          # マッチングキュー、WebRTCシグナリング、Socket.IO
├── notification/   # FCMプッシュ通知
├── profile/        # プロフィール・画像管理
├── report/         # ユーザー通報
└── user/           # 会員登録・ログイン・ソーシャルログイン
```

## はじめ方

### 前提条件

- Java 17（Eclipse Adoptium推奨）
- PostgreSQL 17（ローカル、port 5432）
- Redis（ローカルまたはDocker、port 6379）
- Git Bash（Windows）

### インストール・起動

```bash
# 1. リポジトリのクローン
git clone https://github.com/your-org/connecto.git
cd connecto

# 2. .env.localを作成（以下の環境変数セクション参照）

# 3. データベースの作成
psql -U postgres -c "CREATE DATABASE connecto;"

# 4. Redisコンテナ起動（マッチング機能に必要）
docker-compose up -d

# 5. アプリ起動（Git Bash）
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.16.8-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
bash run-local.sh
```

> PowerShellで `run-local.sh` を直接実行するとCRLF改行コードの問題でエラーになります。必ず **Git Bash** を使用してください。

### 環境変数（.env.local）

```env
DB_PASSWORD=your_postgres_password

GOOGLE_ANDROID_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_WEB_CLIENT_ID=xxx.apps.googleusercontent.com

AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_S3_BUCKET=connecto-dev
AWS_REGION=ap-northeast-2

# 未設定の場合はFCM無効（ローカル開発では問題なし）
FIREBASE_SERVICE_ACCOUNT_JSON=
```

### 動作確認

| サービス | URL |
|----------|-----|
| REST API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Socket.IO | ws://localhost:9092 |
| ヘルスチェック | http://localhost:8080/health |

## 主なAPI

完全なAPIドキュメントは **http://localhost:8080/swagger-ui.html** で確認できます。

| ドメイン | パス | 説明 |
|----------|------|------|
| 認証 | `POST /auth/signup` | 会員登録 |
| 認証 | `POST /auth/login` | ログイン（accessToken + refreshTokenクッキー） |
| 認証 | `POST /auth/social-login` | Google OAuthログイン |
| ユーザー | `GET /users/me` | マイプロフィール統合取得 |
| プロフィール | `PATCH /users/me/profile/image` | プロフィール画像更新（S3） |
| マッチング | `POST /match/start` | マッチングキューに参加 |
| 通話 | `POST /call/request/{friendId}` | フレンドへの通話リクエスト |
| フレンド | `POST /friends/request` | フレンド申請 |
| チャット | `GET /chat/rooms/{id}/messages` | メッセージ履歴取得（ページング） |
| 通知 | `POST /users/me/device-token` | FCMトークン登録 |

## Socket.IOイベント

接続時にJWTを渡します：
```
Authorization: Bearer <accessToken>  または  ?token=<accessToken>
```

| イベント | 方向 | 説明 |
|----------|------|------|
| `match:start` | クライアント→サーバー | マッチングリクエスト |
| `match:success` | サーバー→クライアント | マッチング完了 `{ sessionId, webrtcChannelId, isOfferer }` |
| `webrtc:offer/answer/ice` | クライアント→サーバー | WebRTCシグナリング |
| `call:incoming` | サーバー→クライアント | フレンドからの着信 |
| `call:ended` | サーバー→クライアント | 相手が通話を終了 |
| `chat:send` | クライアント→サーバー | メッセージ送信 `{ roomId, content }` |
| `chat:receive` | サーバー→ルーム | メッセージブロードキャスト |
| `chat:read` | 双方向 | 既読処理 / 既読通知 |
| `chat:typing` | 双方向 | タイピングインジケーター |

## フロントエンド連携

| サーバー | ポート | 説明 |
|---------|--------|------|
| REST API | 8080 | 全HTTPエンドポイント |
| Socket.IO | 9092 | マッチング、WebRTC、チャット、フレンド状態 |

> フロントエンド: React Native + Expo、[connecto-app](../connecto-app)

---

<p align="center">
  Made with ❤️ by the Connecto Team
</p>
