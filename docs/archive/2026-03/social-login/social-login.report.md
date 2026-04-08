# Report: social-login

> Feature: 소셜 로그인 — Google OAuth
> 기간: 2026-03-07
> Match Rate: 97%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | social-login |
| 시작일 | 2026-03-07 |
| 완료일 | 2026-03-07 |
| Match Rate | **97%** |
| 구현 파일 | 신규 1개 + 수정 4개 |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 이메일/비밀번호 가입만 지원 → Google 계정 사용자 진입 장벽 |
| Solution | Google ID Token 검증 후 기존 계정 연동 또는 자동 가입, JWT 발급 |
| Function UX Effect | Google 계정으로 1탭 로그인, 첫 사용자 자동 가입 |
| Core Value | 소셜 계정 기반 간편 온보딩으로 신규 사용자 유입 확대 |

---

## 1. 구현 내용

### 신규 생성 (1개)

| 파일 | 내용 |
|------|------|
| `auth/dto/SocialLoginRequest.java` | `{ provider, token }` 요청 DTO |

### 수정 (4개)

| 파일 | 변경 내용 |
|------|-----------|
| `user/domain/User.java` | `provider`, `providerId` 필드 추가, password nullable |
| `auth/service/AuthService.java` | `socialLogin()`, `verifyGoogleToken()` 추가 |
| `auth/controller/AuthController.java` | `POST /auth/social-login` 엔드포인트 추가 |
| `common/response/ErrorCode.java` | `INVALID_PROVIDER`, `INVALID_SOCIAL_TOKEN` 추가 |
| `build.gradle` | `google-api-client:2.2.0` 의존성 추가 |

---

## 2. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| `google-api-client:2.2.0` | Plan의 `google-auth-library` 대신 — `GoogleIdTokenVerifier`가 여기 있음 | 컴파일 가능한 유일한 선택 |
| FilterConfig 미수정 | `JwtAuthenticationFilter`는 Bearer 헤더 없으면 pass-through | 화이트리스트 추가 불필요 |
| 이메일 기반 자동 연동 | 동일 이메일 소셜 로그인 → 기존 계정 반환 | 계정 중복 방지 |
| `User.createSocialUser()` | password=null, provider="google" 팩토리 메서드 | 소셜 전용 사용자 명확히 구분 |

---

## 3. Success Criteria 최종

| 기준 | 상태 |
|------|------|
| Google 계정 → accessToken + refreshToken 발급 | ✅ Met |
| 첫 소셜 로그인 → User 자동 생성 (email 기반) | ✅ Met |
| 기존 이메일 계정 + 동일 Google 이메일 → 기존 계정 사용 | ✅ Met |
| 유효하지 않은 토큰 → 401 | ✅ Met |
| Kakao/Line → 400 INVALID_PROVIDER | ✅ Met |

**5/5 (100%)**

---

## 4. Gap 및 잔여 사항

| 심각도 | 항목 | 내용 |
|--------|------|------|
| ℹ️ Minor | 의존성 라이브러리 변경 | Plan 명시 라이브러리에 GoogleIdTokenVerifier 미존재 — 기술적으로 불가피 |
| ℹ️ Minor | FilterConfig 미수정 | JWT 필터 설계상 Bearer 없는 요청은 자동 pass-through — 화이트리스트 추가 불필요 |

> 두 gap 모두 정당한 기술적 결정. 기능 영향 없음.

---

## 5. Match Rate

| Category | Score |
|----------|:-----:|
| FR (8/8) | 100% |
| NFR (5/5 functionally) | 100% |
| Acceptance Criteria (5/5) | 100% |
| **Overall** | **97%** |
