package com.pm.connecto.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.domain.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

	// ========== 상태 상수 (JPQL 파라미터용) ==========
	UserStatus ACTIVE_STATUS = UserStatus.ACTIVE;
	UserStatus DELETED_STATUS = UserStatus.DELETED;

	// ========== 인증용 조회 (상태 무관, 이후 상태별 에러 처리 필요) ==========

	/**
	 * 인증 로직 전용: 모든 상태의 사용자 조회
	 * 주의: 반드시 isDeleted(), isBlocked() 체크 후 사용
	 */
	@Query("SELECT u FROM User u WHERE u.id = :id")
	Optional<User> findByIdForAuth(@Param("id") Long id);

	/**
	 * 인증 로직 전용: 모든 상태의 사용자 조회
	 * 주의: 반드시 isDeleted(), isBlocked() 체크 후 사용
	 */
	@Query("SELECT u FROM User u WHERE u.email = :email")
	Optional<User> findByEmailForAuth(@Param("email") String email);

	// ========== 일반 조회 (ACTIVE 사용자만) ==========

	/**
	 * 활성 사용자만 조회 (ACTIVE 상태)
	 */
	@Query("SELECT u FROM User u WHERE u.id = :id AND u.status = :status")
	Optional<User> findByIdAndStatus(@Param("id") Long id, @Param("status") UserStatus status);

	/**
	 * 활성 사용자만 조회 (ACTIVE 상태)
	 */
	default Optional<User> findActiveById(Long id) {
		return findByIdAndStatus(id, ACTIVE_STATUS);
	}

	/**
	 * 활성 사용자만 조회 (ACTIVE 상태)
	 */
	@Query("SELECT u FROM User u WHERE u.email = :email AND u.status = :status")
	Optional<User> findByEmailAndStatus(@Param("email") String email, @Param("status") UserStatus status);

	/**
	 * 활성 사용자만 조회 (ACTIVE 상태)
	 */
	default Optional<User> findActiveByEmail(String email) {
		return findByEmailAndStatus(email, ACTIVE_STATUS);
	}

	// ========== 존재 여부 확인 (DELETED 제외) ==========

	/**
	 * 이메일 중복 확인 (삭제된 사용자 제외)
	 */
	@Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND u.status != :excludedStatus")
	boolean existsByEmailExcludingStatus(@Param("email") String email, @Param("excludedStatus") UserStatus excludedStatus);

	/**
	 * 이메일 중복 확인 (삭제된 사용자 제외)
	 */
	default boolean existsByEmail(String email) {
		return existsByEmailExcludingStatus(email, DELETED_STATUS);
	}
}
