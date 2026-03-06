# Design: profile-image-upload

## 참조

- Plan: `docs/01-plan/features/profile-image-upload.plan.md`
- 작성일: 2026-03-07

## 아키텍처 다이어그램

```
Client
  │ PATCH /users/me/profile/image
  │ Content-Type: multipart/form-data
  │ Authorization: Bearer {token}
  ▼
UserController.updateProfileImage(MultipartFile image)
  │
  ├─ 1. userContext.getUserId()
  ├─ 2. 파일 검증 (타입, 크기)
  │
  ▼
ProfileService.updateProfileImage(userId, multipartFile)
  │
  ├─ 3. profileRepository.findByUserId(userId)
  ├─ 4. S3Service.upload(file, key)  → S3 PutObject
  ├─ 5. (선택) S3Service.delete(oldKey) → S3 DeleteObject
  ├─ 6. profile.updateImageUrl(s3Url)
  │
  ▼
ProfileResponse 반환 (200 OK)
```

## 신규 파일

| 파일 | 역할 |
|------|------|
| `common/config/S3Config.java` | `S3Client` 빈 등록 |
| `common/service/S3Service.java` | S3 upload / delete |

## 수정 파일

| 파일 | 변경 내용 |
|------|---------|
| `build.gradle` | `software.amazon.awssdk:s3` 의존성 추가 |
| `application.yaml` | `cloud.aws.*` 설정 추가 |
| `profile/service/ProfileService.java` | `updateProfileImage()` 추가 |
| `user/controller/UserController.java` | `PATCH /users/me/profile/image` 핸들러 추가 |

## 상세 설계

### 1. build.gradle 의존성

```gradle
// AWS SDK v2 S3
implementation platform('software.amazon.awssdk:bom:2.25.0')
implementation 'software.amazon.awssdk:s3'
```

### 2. application.yaml 설정

```yaml
cloud:
  aws:
    s3:
      bucket: ${AWS_S3_BUCKET:connecto-dev}
      region: ${AWS_REGION:ap-northeast-2}
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID:}
      secret-key: ${AWS_SECRET_ACCESS_KEY:}
```

> multipart 설정도 추가:
> ```yaml
> spring:
>   servlet:
>     multipart:
>       max-file-size: 5MB
>       max-request-size: 5MB
> ```

### 3. S3Config.java

```java
@Configuration
public class S3Config {

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.s3.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();
    }
}
```

### 4. S3Service.java

```java
@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.region}")
    private String region;

    // 반환: https://{bucket}.s3.{region}.amazonaws.com/{key}
    public String upload(MultipartFile file, String key) { ... }

    public void delete(String key) { ... }

    // S3 URL에서 key 추출 헬퍼
    public String extractKey(String s3Url) { ... }
}
```

**upload 로직:**
1. `file.getContentType()` → JPEG / PNG / WEBP 검증
2. `s3Client.putObject(PutObjectRequest, RequestBody.fromInputStream(...))`
3. ACL: `ObjectCannedACL.PUBLIC_READ`
4. 반환: `https://{bucket}.s3.{region}.amazonaws.com/{key}`

**S3 key 형식:** `profiles/{userId}/{UUID}.{ext}`

### 5. ProfileService.updateProfileImage()

```java
@Transactional
public Profile updateProfileImage(Long userId, MultipartFile file) {
    Profile profile = profileRepository.findByUserId(userId)
        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROFILE_NOT_FOUND));

    // 기존 이미지 삭제 (있으면)
    if (profile.getProfileImageUrl() != null) {
        String oldKey = s3Service.extractKey(profile.getProfileImageUrl());
        s3Service.delete(oldKey);
    }

    String key = "profiles/" + userId + "/" + UUID.randomUUID() + "." + getExt(file);
    String url = s3Service.upload(file, key);

    profile.updateImageUrl(url);
    return profile;
}
```

### 6. UserController — 핸들러 추가

```java
@Operation(summary = "프로필 이미지 수정", description = "프로필 이미지를 변경합니다.")
@SecurityRequirement(name = "Bearer Authentication")
@PatchMapping(value = "/me/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<ProfileResponse> updateProfileImage(
    @RequestPart("image") MultipartFile image
) {
    Profile profile = profileService.updateProfileImage(userContext.getUserId(), image);
    return ApiResponse.success(ProfileResponse.from(profile));
}
```

### 7. Profile 도메인 — updateImageUrl() 메서드

```java
// Profile.java에 추가
public void updateImageUrl(String profileImageUrl) {
    this.profileImageUrl = profileImageUrl;
}
```

## 검증 규칙

| 항목 | 규칙 | 처리 |
|------|------|------|
| 파일 필수 | image 파트 없으면 | 400 Bad Request |
| 파일 타입 | image/jpeg, image/png, image/webp만 허용 | 400 Bad Request |
| 파일 크기 | 5MB 초과 | 400 (MaxUploadSizeExceededException) |
| 프로필 없음 | userId에 해당 프로필 없음 | 404 Not Found |

## ErrorCode 추가

```java
// ErrorCode.java에 추가
INVALID_FILE_TYPE(400, "지원하지 않는 파일 형식입니다."),
```

## 구현 순서 (Do 페이즈용)

1. `build.gradle` — AWS SDK v2 의존성
2. `application.yaml` — `cloud.aws.*` + multipart 설정
3. `ErrorCode.java` — `INVALID_FILE_TYPE` 추가
4. `S3Config.java` — S3Client 빈
5. `S3Service.java` — upload, delete, extractKey
6. `Profile.java` — `updateImageUrl()` 추가
7. `ProfileService.java` — `updateProfileImage()` 추가 + S3Service 주입
8. `UserController.java` — `PATCH /users/me/profile/image` 핸들러
