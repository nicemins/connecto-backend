package com.pm.connecto.interest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.interest.domain.UserInterest;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

	@Query("SELECT ui FROM UserInterest ui JOIN FETCH ui.interest WHERE ui.user.id = :userId")
	List<UserInterest> findByUserIdWithInterest(@Param("userId") Long userId);

	boolean existsByUserIdAndInterestId(Long userId, Long interestId);

	Optional<UserInterest> findByUserIdAndInterestId(Long userId, Long interestId);

	int countByUserId(Long userId);

	/**
	 * 특정 사용자의 특정 관심사 삭제
	 * 
	 * <p>Flush 전략:
	 * <ul>
	 *   <li>flushAutomatically = true: DELETE 실행 전 pending 변경사항 flush</li>
	 *   <li>clearAutomatically = true: DELETE 실행 후 영속성 컨텍스트 클리어</li>
	 * </ul>
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM UserInterest ui WHERE ui.user.id = :userId AND ui.interest.id = :interestId")
	void deleteByUserIdAndInterestId(@Param("userId") Long userId, @Param("interestId") Long interestId);

	/**
	 * 사용자의 모든 관심사 관계 삭제
	 * 
	 * <p>Flush 전략:
	 * <ul>
	 *   <li>flushAutomatically = true: DELETE 실행 전 pending 변경사항 flush (순서 보장)</li>
	 *   <li>clearAutomatically = true: DELETE 실행 후 영속성 컨텍스트 클리어 (stale 데이터 제거)</li>
	 * </ul>
	 * 
	 * <p>주의: 이 메서드 호출 후 기존 영속 객체는 DETACHED 상태가 됨.
	 * 후속 INSERT 시 getReferenceById()로 User 프록시 재생성 권장.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM UserInterest ui WHERE ui.user.id = :userId")
	void deleteByUserId(@Param("userId") Long userId);

	// ========== 매칭용 쿼리 ==========

	/**
	 * 공통 관심사가 있는 사용자 ID 조회
	 */
	@Query("""
		SELECT DISTINCT ui.user.id FROM UserInterest ui
		WHERE ui.interest.id IN (
			SELECT ui2.interest.id FROM UserInterest ui2 WHERE ui2.user.id = :userId
		)
		AND ui.user.id != :userId
		""")
	List<Long> findUserIdsWithCommonInterests(@Param("userId") Long userId);

	/**
	 * 특정 관심사를 가진 사용자 수 조회
	 */
	@Query("SELECT COUNT(DISTINCT ui.user.id) FROM UserInterest ui WHERE ui.interest.id = :interestId")
	long countUsersByInterestId(@Param("interestId") Long interestId);
}
