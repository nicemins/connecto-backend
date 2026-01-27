package com.pm.connecto.match.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.match.domain.CallSession;
import com.pm.connecto.match.domain.CallSessionStatus;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

	/**
	 * 사용자가 참여한 세션 조회 (양쪽 모두 확인)
	 */
	@Query("SELECT cs FROM CallSession cs WHERE cs.id = :sessionId AND (cs.user1.id = :userId OR cs.user2.id = :userId)")
	Optional<CallSession> findByIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

	/**
	 * 사용자가 진행 중인 세션 조회
	 */
	@Query("SELECT cs FROM CallSession cs WHERE (cs.user1.id = :userId OR cs.user2.id = :userId) AND cs.status = :status")
	Optional<CallSession> findInProgressByUserId(@Param("userId") Long userId, @Param("status") CallSessionStatus status);

	default Optional<CallSession> findInProgressByUserId(Long userId) {
		return findInProgressByUserId(userId, CallSessionStatus.IN_PROGRESS);
	}

	/**
	 * 시작 시간이 특정 시간 이전인 진행 중인 세션 조회 (스케줄러용)
	 */
	@Query("SELECT cs FROM CallSession cs WHERE cs.status = :status AND cs.startedAt IS NOT NULL AND cs.startedAt < :cutoffTime")
	List<CallSession> findInProgressSessionsStartedBefore(
		@Param("status") CallSessionStatus status,
		@Param("cutoffTime") LocalDateTime cutoffTime
	);
}
