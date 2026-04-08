# Report: profile-image-upload

> Feature: 프로필 이미지 업로드 (AWS S3)
> 기간: 2026-03-07
> Match Rate: 97%
> Status: Completed ✅

---

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | profile-image-upload |
| 시작일 | 2026-03-07 |
| 완료일 | 2026-03-07 |
| Match Rate | **97%** |
| 구현 파일 | 신규 2개 + 수정 3개 |

### Value Delivered

| 관점 | 내용 |
|------|------|
| Problem | 사용자가 프로필 이미지를 업로드하거나 변경할 수단이 없음 |
| Solution | `PATCH /users/me/profile/image` — AWS S3 업로드 + DB URL 갱신 |
| Function UX Effect | 프로필 이미지 변경 즉시 반영, 이전 이미지 자동 삭제 |
| Core Value | 개인화된 프로필로 소통 신뢰감 향상 |

---

## 1. 구현 내용

### 신규 생성 (2개)

| 파일 | 내용 |
|------|------|
| `common/config/S3Config.java` | AWS S3Client 빈 등록 (환경변수 기반) |
| `common/service/S3Service.java` | upload(file, key), delete(key), ALLOWED_CONTENT_TYPES |

### 수정 (3개)

| 파일 | 변경 내용 |
|------|-----------|
| `profile/service/ProfileService.java` | `updateProfileImage()` — 이전 이미지 삭제 + S3 업로드 + URL 갱신 |
| `user/controller/UserController.java` | `PATCH /users/me/profile/image` 핸들러 |
| `build.gradle` | AWS SDK v2 의존성 추가 |

---

## 2. 아키텍처 결정

| 결정 | 내용 | 결과 |
|------|------|------|
| AWS SDK v2 | 최신 비동기 SDK 사용 | 미래 비동기 확장 용이 |
| S3 키: `profiles/{userId}/{uuid}.{ext}` | userId 기반 디렉토리 | 사용자별 이미지 격리 |
| 이전 이미지 자동 삭제 | S3 delete(oldKey) | 스토리지 비용 최적화 |
| ACL 미설정 | 현대 AWS 버킷 정책 방식 사용 | S3 권장 방식 준수 |

---

## 3. Success Criteria 최종

| 기준 | 상태 |
|------|------|
| PATCH /users/me/profile/image — multipart 수신 | ✅ Met |
| AWS S3 업로드 | ✅ Met |
| profileImageUrl DB 업데이트 | ✅ Met |
| 이전 이미지 S3 삭제 | ✅ Met |
| JPEG/PNG/WEBP 타입 제한 | ✅ Met |
| 5MB 크기 제한 | ✅ Met |

**6/6 (100%)**

---

## 4. 설계 대비 추가 구현 (향상)

| 항목 | 내용 |
|------|------|
| `FILE_SIZE_EXCEEDED` ErrorCode | 5MB 초과 명시적 에러코드 |
| `FILE_UPLOAD_FAILED` ErrorCode | S3 업로드 실패 처리 |
| `MaxUploadSizeExceededException` 핸들러 | 400 Bad Request로 처리 |
| 테스트 환경 S3 mock | TestRedisConfig에 S3Service mock 빈 추가 |

---

## 5. Match Rate

| Category | Score |
|----------|:-----:|
| FR/NFR | 100% |
| API Contract | 100% |
| **Overall** | **97%** |

> G-01 (의도적): NFR-04 ACL 미설정 — 현대 S3 버킷 정책 방식 사용 (기능 영향 없음)
