package com.pm.connecto.friend.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.friend.domain.Block;

public interface BlockRepository extends JpaRepository<Block, Long> {

	// 차단 목록 조회 — N+1 방지 (blocked 유저 FETCH)
	@Query("SELECT b FROM Block b JOIN FETCH b.blocked WHERE b.blocker.id = :blockerId ORDER BY b.createdAt DESC")
	List<Block> findAllByBlockerIdWithBlocked(@Param("blockerId") Long blockerId);

	boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

	Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

	/**
	 * 양방향 차단 존재 여부 (매칭 필터링, 친구 신청 차단용)
	 */
	@Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Block b " +
		"WHERE (b.blocker.id = :userId1 AND b.blocked.id = :userId2) " +
		"OR (b.blocker.id = :userId2 AND b.blocked.id = :userId1)")
	boolean existsBlockBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

	/**
	 * 매칭 루프 전 일괄 조회 — 락 내부에서 N번 쿼리 방지
	 */
	@Query("SELECT b.blocked.id FROM Block b WHERE b.blocker.id = :userId")
	Set<Long> findBlockedIdsByBlockerId(@Param("userId") Long userId);

	@Query("SELECT b.blocker.id FROM Block b WHERE b.blocked.id = :userId")
	Set<Long> findBlockerIdsByBlockedId(@Param("userId") Long userId);
}
