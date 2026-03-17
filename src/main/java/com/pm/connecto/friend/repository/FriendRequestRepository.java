package com.pm.connecto.friend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.friend.domain.FriendRequest;
import com.pm.connecto.friend.domain.FriendRequestStatus;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

	/**
	 * 특정 방향 요청 조회 (sender → receiver)
	 */
	Optional<FriendRequest> findBySenderIdAndReceiverId(Long senderId, Long receiverId);

	/**
	 * 중복 요청 확인 (양방향) - 이미 PENDING 또는 ACCEPTED 상태 체크
	 */
	@Query("SELECT CASE WHEN COUNT(fr) > 0 THEN true ELSE false END FROM FriendRequest fr " +
		"WHERE ((fr.sender.id = :userId1 AND fr.receiver.id = :userId2) " +
		"OR (fr.sender.id = :userId2 AND fr.receiver.id = :userId1)) " +
		"AND fr.status IN ('PENDING', 'ACCEPTED')")
	boolean existsActiveRequestBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

	/**
	 * 받은 친구 요청 목록 조회 (PENDING)
	 */
	List<FriendRequest> findByReceiverIdAndStatus(Long receiverId, FriendRequestStatus status);

	/**
	 * ID + receiver 로 요청 조회 (수락/거절용)
	 */
	Optional<FriendRequest> findByIdAndReceiverId(Long id, Long receiverId);
}
