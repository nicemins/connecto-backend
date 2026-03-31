package com.pm.connecto.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.chat.domain.ChatRoomMember;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

	Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

	// getRooms() 최적화: 내 전체 룸의 lastReadMessageId 일괄 조회
	@Query("SELECT m FROM ChatRoomMember m WHERE m.user.id = :userId AND m.room.id IN :roomIds")
	List<ChatRoomMember> findByUserIdAndRoomIdIn(
		@Param("userId") Long userId,
		@Param("roomIds") Collection<Long> roomIds
	);
}
