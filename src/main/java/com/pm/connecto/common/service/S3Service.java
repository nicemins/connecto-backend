package com.pm.connecto.common.service;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pm.connecto.common.exception.BusinessException;
import com.pm.connecto.common.response.ErrorCode;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3Service {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
		"image/jpeg", "image/png", "image/webp"
	);

	private final S3Client s3Client;

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@Value("${cloud.aws.s3.region}")
	private String region;

	public S3Service(S3Client s3Client) {
		this.s3Client = s3Client;
	}

	public String upload(MultipartFile file, String key) {
		validateFileType(file);

		try {
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(file.getContentType())
				.contentLength(file.getSize())
				.build();

			s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

			return buildS3Url(key);
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
		}
	}

	public void delete(String key) {
		if (key == null || key.isBlank()) {
			return;
		}
		DeleteObjectRequest request = DeleteObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build();
		s3Client.deleteObject(request);
	}

	public String extractKey(String s3Url) {
		if (s3Url == null || s3Url.isBlank()) {
			return null;
		}
		String prefix = buildS3Url("");
		if (s3Url.startsWith(prefix)) {
			return s3Url.substring(prefix.length());
		}
		return null;
	}

	private void validateFileType(MultipartFile file) {
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
		}
	}

	private String buildS3Url(String key) {
		return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
	}
}
