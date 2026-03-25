package com.pm.connecto.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	// JOIN FETCH sender: getMessages() 페이징 시 N+1 방지
	@Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.room.id = :roomId ORDER BY m.createdAt DESC")
	Page<ChatMessage> findByRoomIdOrderByCreatedAtDesc(@Param("roomId") Long roomId, Pageable pageable);

	Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(Long roomId);

	/**
	 * 채팅방 목록 조회 시 N+1 방지 — 각 방의 최신 메시지를 일괄 조회
	 * [roomId, content] 쌍으로 반환. IMAGE 타입은 '사진' 고정 문자열
	 */
	@Query("SELECT m.room.id, CASE WHEN m.messageType = 'IMAGE' THEN '사진' ELSE m.content END FROM ChatMessage m WHERE m.id IN " +
		"(SELECT MAX(m2.id) FROM ChatMessage m2 WHERE m2.room.id IN :roomIds GROUP BY m2.room.id)")
	List<Object[]> findLatestMessageContentByRoomIds(@Param("roomIds") Collection<Long> roomIds);
}
