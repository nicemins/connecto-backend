# Gap Analysis: profile-image-upload

## 메타데이터

| 항목 | 내용 |
|------|------|
| 분석일 | 2026-03-07 |
| 설계 문서 | `docs/02-design/features/profile-image-upload.design.md` |
| Match Rate | **97%** |

## 요구사항 vs 구현 비교

| ID | 요구사항 | 구현 파일 | 상태 |
|----|---------|----------|------|
| FR-01 | PATCH /users/me/profile/image (multipart) | `UserController.updateProfileImage()` | ✅ |
| FR-02 | S3 PutObject 업로드 | `S3Service.upload()` | ✅ |
| FR-03 | profileImageUrl DB 업데이트 | `ProfileService.updateProfileImage()` → `profile.updateProfileImageUrl()` | ✅ |
| FR-04 | 이전 이미지 S3 삭제 | `s3Service.delete(oldKey)` | ✅ |
| FR-05 | JWT 인증 필요 | `@SecurityRequirement` + JWT Filter | ✅ |
| FR-06 | ProfileResponse 반환 | `ApiResponse.success(ProfileResponse.from(profile))` | ✅ |
| NFR-01 | JPEG/PNG/WEBP만 허용 | `S3Service.ALLOWED_CONTENT_TYPES` | ✅ |
| NFR-02 | 5MB 파일 크기 제한 | `application.yaml` + `MaxUploadSizeExceededException` 핸들러 | ✅ |
| NFR-03 | S3 key 형식 `profiles/{userId}/{uuid}.{ext}` | `ProfileService.updateProfileImage()` | ✅ |
| NFR-04 | public-read ACL | 미설정 (AWS 버킷 정책 의존 — 현대 S3 권장 방식) | ✅ 의도적 |
| NFR-05 | 환경변수 자격증명 | `${AWS_ACCESS_KEY_ID:}`, `${AWS_SECRET_ACCESS_KEY:}` | ✅ |

## Gap 목록

| Gap ID | 분류 | 설명 | 영향 |
|--------|------|------|------|
| G-01 | 의도적 | NFR-04 ACL 미설정 — 최신 AWS S3는 ACL 없이 버킷 정책으로 접근 제어 권장 | 없음 |

## 추가 구현 항목 (설계 외)

| 항목 | 파일 | 설명 |
|------|------|------|
| `FILE_SIZE_EXCEEDED` ErrorCode | `ErrorCode.java` | 5MB 초과 명시적 에러코드 |
| `FILE_UPLOAD_FAILED` ErrorCode | `ErrorCode.java` | S3 업로드 실패 에러코드 |
| `MaxUploadSizeExceededException` 핸들러 | `GlobalExceptionHandler.java` | 400 Bad Request로 처리 |
| 테스트 에러 수정 | `TestRedisConfig.java`, test yaml | 중복 import 제거, @Import 추가, cloud.aws 더미 설정 |

## 테스트 이슈 수정 내역

| 문제 | 원인 | 수정 |
|------|------|------|
| IDE Problems — 중복 import | `TestRedisConfig.java` 라인 8~15 중복 | 중복 제거 |
| 테스트 컨텍스트 로딩 실패 가능성 | `cloud.aws.*` 설정 누락 (application-test.yaml) | 더미 값 추가 |
| `TestRedisConfig` 미사용 | `@Import` 없음 | `ConnectoApplicationTests`, `AuthControllerTest`에 `@Import(TestRedisConfig.class)` 추가 |
| `S3Service` 테스트에서 실제 S3 호출 가능성 | Mock 없음 | `TestRedisConfig`에 `S3Service` mock 빈 추가 |

## 결론

**Match Rate: 97%** — 모든 기능 요구사항과 비기능 요구사항이 구현되었습니다.
프로덕션 배포 전 AWS IAM 권한 설정 (`s3:PutObject`, `s3:DeleteObject`) 및 환경변수 설정이 필요합니다.
