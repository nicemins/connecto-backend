# Plan: profile-image-upload

## 개요

| 항목 | 내용 |
|------|------|
| 기능명 | 프로필 이미지 업로드 |
| 엔드포인트 | `PATCH /users/me/profile/image` |
| 스토리지 | AWS S3 |
| 우선순위 | 낮음 (프론트 대기 항목) |
| 작성일 | 2026-03-07 |

## 목표

사용자가 자신의 프로필 이미지를 업로드하거나 변경할 수 있도록 한다.
이미지는 AWS S3에 저장되고, DB의 `profile.profileImageUrl`을 S3 URL로 업데이트한다.

## 요구사항

### 기능 요구사항

| ID | 요구사항 |
|----|---------|
| FR-01 | `PATCH /users/me/profile/image` — `multipart/form-data`로 이미지 파일 수신 |
| FR-02 | 이미지를 AWS S3 버킷에 업로드 |
| FR-03 | S3 업로드 성공 시 `profile.profileImageUrl`을 S3 URL로 업데이트 |
| FR-04 | 이전 이미지가 있으면 S3에서 삭제 (옵션) |
| FR-05 | 인증된 사용자만 접근 가능 (JWT Bearer) |
| FR-06 | 업로드 완료 시 변경된 `ProfileResponse` 반환 |

### 비기능 요구사항

| ID | 요구사항 |
|----|---------|
| NFR-01 | 허용 파일 타입: JPEG, PNG, WEBP |
| NFR-02 | 최대 파일 크기: 5MB |
| NFR-03 | S3 키 형식: `profiles/{userId}/{uuid}.{ext}` |
| NFR-04 | S3 객체 ACL: 퍼블릭 읽기 (또는 presigned URL — 이번 구현은 public-read) |
| NFR-05 | Spring Boot 환경변수로 AWS 자격증명 관리 (`application.yaml`) |

## 스코프

### 포함

- `PATCH /users/me/profile/image` 엔드포인트
- `S3Service` 빈 (upload, delete)
- `application.yaml` AWS 설정 추가
- `build.gradle` AWS SDK 의존성 추가
- `UserController`에 이미지 업로드 핸들러 추가
- `ProfileService.updateProfileImage()` 메서드 추가

### 제외

- Presigned URL 방식 (프론트 직접 업로드) — 이번 구현 제외
- 이미지 리사이징 / 썸네일 생성
- CDN (CloudFront) 연동

## 기술 스택

| 항목 | 선택 |
|------|------|
| AWS SDK | `software.amazon.awssdk:s3` (v2) |
| Spring | `spring-boot-starter-web` (multipart 내장 지원) |
| 자격증명 | `application.yaml` — access-key, secret-key, region, bucket |

## API 명세

```
PATCH /users/me/profile/image
Authorization: Bearer {token}
Content-Type: multipart/form-data

Form field: image (MultipartFile)

Response 200:
{
  "success": true,
  "data": {
    "id": 1,
    "nickname": "홍길동",
    "profileImageUrl": "https://{bucket}.s3.{region}.amazonaws.com/profiles/{userId}/{uuid}.jpg",
    "bio": "..."
  },
  "message": null
}

Error cases:
- 400: 파일 없음 / 지원하지 않는 타입 / 크기 초과
- 401: 인증 실패
- 404: 프로필 없음
```

## 구현 순서

1. `build.gradle` — AWS SDK v2 의존성 추가
2. `application.yaml` — AWS 설정 (access-key, secret-key, region, bucket)
3. `common/config/S3Config.java` — `S3Client` 빈 등록
4. `common/service/S3Service.java` — upload(file, key), delete(key)
5. `profile/service/ProfileService.java` — `updateProfileImage()` 추가
6. `user/controller/UserController.java` — `PATCH /users/me/profile/image` 핸들러 추가

## 리스크

| 리스크 | 대응 |
|--------|------|
| AWS 자격증명 없음 | 개발 환경에서는 더미 설정으로 빌드 통과, 실제 업로드는 환경변수 필요 |
| 파일 크기 제한 | `spring.servlet.multipart.max-file-size=5MB` 설정 필요 |
| S3 권한 오류 | IAM 정책 — `s3:PutObject`, `s3:DeleteObject` 필요 |
