# Plan: social-login

## Overview

| Item | Content |
|------|---------|
| Feature | Social Login — Google OAuth |
| Endpoint | `POST /auth/social-login` |
| Source Spec | `C:\connecto-app\CLAUDE.md` Section 10.1 |
| Priority | Medium |
| Date | 2026-03-07 |

## Goal

Enable users to sign in with Google OAuth from the frontend.
The backend verifies the Google ID token, auto-creates a user on first login,
and returns the same accessToken + refreshToken cookie as email login.

## Requirements

### Functional

| ID | Requirement |
|----|-------------|
| FR-01 | `POST /auth/social-login` — public endpoint, no auth required |
| FR-02 | Accept `{ provider: "google", token: "<Google ID Token>" }` |
| FR-03 | Verify Google ID token against Android + Web client IDs |
| FR-04 | Extract email from verified token payload |
| FR-05 | If user with email exists → use existing account |
| FR-06 | If user does not exist → auto-create with `provider="google"`, no password |
| FR-07 | Issue accessToken (body) + refreshToken (HttpOnly cookie) — same as email login |
| FR-08 | Invalid token → 401 Unauthorized |

### Non-functional

| ID | Requirement |
|----|-------------|
| NFR-01 | `User.java` — add `provider` (default "local"), `providerId` (nullable) fields |
| NFR-02 | `password` field nullable for social users |
| NFR-03 | Google client IDs via env vars: `GOOGLE_ANDROID_CLIENT_ID`, `GOOGLE_WEB_CLIENT_ID` |
| NFR-04 | Gradle dependency: `com.google.auth:google-auth-library-oauth2-http:1.23.0` |
| NFR-05 | `/auth/social-login` added to JWT filter whitelist |

## Scope

### Included

- `POST /auth/social-login` endpoint (Google only)
- Google ID token verification (`GoogleIdTokenVerifier`)
- User auto-creation / lookup by email
- JWT issuance (reuse existing `jwtTokenProvider`)
- `User.java` domain model update (`provider`, `providerId`, nullable password)
- `SocialLoginRequest.java` DTO
- `application.yaml` Google client ID config
- `FilterConfig.java` whitelist update

### Excluded

- Kakao / Line social login (frontend shows "coming soon" alert)
- Account linking UI (same email → auto-merged silently)
- Social profile picture sync

## Implementation Order

1. `build.gradle` — google-auth-library dependency
2. `User.java` — add `provider`, `providerId` fields (nullable password)
3. `SocialLoginRequest.java` — new DTO
4. `application.yaml` — Google client ID env vars
5. `ErrorCode.java` — `INVALID_PROVIDER`, `INVALID_SOCIAL_TOKEN`
6. `AuthService.java` — `socialLogin()` + `verifyGoogleToken()`
7. `AuthController.java` — `POST /auth/social-login`
8. `FilterConfig.java` — whitelist `/auth/social-login`

## Acceptance Criteria

- Google account → accessToken + refreshToken issued
- First social login → User auto-created (email-based)
- Existing email user + same Google email → existing account used
- Invalid/expired token → 401
- Kakao/Line provider → 400 (`INVALID_PROVIDER`)
