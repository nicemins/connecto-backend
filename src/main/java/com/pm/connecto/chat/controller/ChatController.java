package com.pm.connecto.chat.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pm.connecto.chat.dto.ChatMessagePageResponse;
import com.pm.connecto.chat.dto.ChatMessageResponse;
import com.pm.connecto.chat.dto.ChatRoomCreateRequest;
import com.pm.connecto.chat.dto.ChatRoomResponse;
import com.pm.connecto.chat.service.ChatService;
import com.pm.connecto.chat.service.ChatService.RoomResult;
import com.pm.connecto.chat.service.ChatService.SavedMessage;
import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.exception.BusinessException;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.common.service.S3Service;
import com.pm.connecto.match.handler.MatchSocketHandler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "채팅", description = "친구 간 1:1 채팅 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/chat")
public class ChatController {

	private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB

	private final ChatService chatService;
	private final UserContext userContext;
	private final S3Service s3Service;

	@Nullable
	@Autowired(required = false)
	private MatchSocketHandler matchSocketHandler;

	public ChatController(ChatService chatService, UserContext userContext, S3Service s3Service) {
		this.chatService = chatService;
		this.userContext = userContext;
		this.s3Service = s3Service;
	}

	@Operation(summary = "채팅방 생성", description = "친구와의 채팅방을 생성합니다. 이미 있으면 기존 채팅방을 반환합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신규 채팅방 생성"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기존 채팅방 반환"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "친구가 아닌 사용자")
	})
	@PostMapping("/rooms")
	public ResponseEntity<ApiResponse<ChatRoomResponse>> createRoom(
		@Valid @RequestBody ChatRoomCreateRequest request
	) {
		RoomResult result = chatService.createOrGetRoom(userContext.getUserId(), request.friendId());
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
	}

	@Operation(summary = "채팅방 목록 조회", description = "내 채팅방 목록을 최신 메시지 순으로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping("/rooms")
	public ApiResponse<List<ChatRoomResponse>> getRooms() {
		return ApiResponse.success(chatService.getRooms(userContext.getUserId()));
	}

	@Operation(summary = "이미지 메시지 전송", description = "채팅방에 이미지를 전송합니다. (multipart/form-data, 5MB, JPEG/PNG/WEBP)")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "전송 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파일 형식/크기 오류"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "채팅방 멤버 아님 또는 차단 상태"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "채팅방 없음")
	})
	@PostMapping("/rooms/{roomId}/messages/image")
	public ResponseEntity<ApiResponse<ChatMessageResponse>> sendImageMessage(
		@Parameter(description = "채팅방 ID") @PathVariable Long roomId,
		@RequestParam("image") MultipartFile image
	) {
		if (image.getSize() > MAX_IMAGE_SIZE) {
			throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
		}

		Long senderId = userContext.getUserId();
		String ext = resolveExtension(image);
		String key = "chat/" + roomId + "/" + UUID.randomUUID() + ext;
		String imageUrl = s3Service.upload(image, key);

		SavedMessage saved = chatService.saveImageMessage(roomId, senderId, imageUrl);

		if (matchSocketHandler != null) {
			Map<String, Object> payload = Map.of("roomId", roomId, "message", saved.messageResponse());
			matchSocketHandler.emitToUser(senderId, "chat:receive", payload);
			matchSocketHandler.emitToUser(saved.otherUserId(), "chat:receive", payload);
		}

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(saved.messageResponse()));
	}

	private String resolveExtension(MultipartFile file) {
		String name = file.getOriginalFilename();
		if (name != null && name.contains(".")) {
			return name.substring(name.lastIndexOf("."));
		}
		return "";
	}

	@Operation(summary = "메시지 히스토리 조회", description = "채팅방의 메시지 히스토리를 페이징으로 조회합니다. (최신순)")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "채팅방 멤버 아님"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "채팅방 없음")
	})
	@GetMapping("/rooms/{roomId}/messages")
	public ApiResponse<ChatMessagePageResponse> getMessages(
		@Parameter(description = "채팅방 ID") @PathVariable Long roomId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "50") int size
	) {
		return ApiResponse.success(chatService.getMessages(userContext.getUserId(), roomId, page, size));
	}
}
