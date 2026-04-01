package com.pm.connecto.friend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.friend.domain.Friendship;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

	/**
	 * 사용자의 모든 친구 관계 조회 (양방향)
	 */
	@Query("SELECT f FROM Friendship f WHERE f.user1.id = :userId OR f.user2.id = :userId")
	List<Friendship> findAllByUserId(@Param("userId") Long userId);

	/**
	 * 두 사용자 간 친구 관계 존재 여부 확인 (양방향)
	 */
	@Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
		"WHERE (f.user1.id = :userId1 AND f.user2.id = :userId2) " +
		"OR (f.user1.id = :userId2 AND f.user2.id = :userId1)")
	boolean existsBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

	/**
	 * 두 사용자 간 친구 관계 조회 (양방향)
	 */
	@Query("SELECT f FROM Friendship f WHERE " +
		"(f.user1.id = :userId1 AND f.user2.id = :userId2) " +
		"OR (f.user1.id = :userId2 AND f.user2.id = :userId1)")
	Optional<Friendship> findBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
