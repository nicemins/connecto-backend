package com.pm.connecto.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.chat.domain.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	@Query("SELECT r FROM ChatRoom r WHERE " +
		"(r.user1.id = :userId1 AND r.user2.id = :userId2) OR " +
		"(r.user1.id = :userId2 AND r.user2.id = :userId1)")
	Optional<ChatRoom> findBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

	@Query("SELECT r FROM ChatRoom r JOIN FETCH r.user1 JOIN FETCH r.user2 " +
		"WHERE (r.user1.id = :userId AND r.user1LeftAt IS NULL) " +
		"   OR (r.user2.id = :userId AND r.user2LeftAt IS NULL) " +
		"ORDER BY r.updatedAt DESC")
	List<ChatRoom> findAllByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);
}
