package com.pm.connecto.language.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.language.domain.Language;
import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;

public interface LanguageRepository extends JpaRepository<Language, Long> {

	// ========== 타입 상수 (JPQL 파라미터용) ==========
	LanguageType NATIVE_TYPE = LanguageType.NATIVE;
	LanguageType LEARNING_TYPE = LanguageType.LEARNING;

	List<Language> findByUserId(Long userId);

	List<Language> findByUserIdAndType(Long userId, LanguageType type);

	Optional<Language> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndLanguageCodeAndType(Long userId, String languageCode, LanguageType type);

	int countByUserId(Long userId);

	/**
	 * 사용자의 모든 언어 삭제
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
	@Query("DELETE FROM Language l WHERE l.user.id = :userId")
	void deleteByUserId(@Param("userId") Long userId);

	// ========== 실시간 매칭용 쿼리 ==========

	/**
	 * 특정 언어 코드와 타입으로 사용자 목록 조회 (매칭용)
	 */
	@Query("SELECT l FROM Language l WHERE l.languageCode = :languageCode AND l.type = :type")
	List<Language> findByLanguageCodeAndType(
		@Param("languageCode") String languageCode,
		@Param("type") LanguageType type
	);

	/**
	 * 언어 교환 매칭: 상대방의 NATIVE가 내 LEARNING이고, 상대방의 LEARNING이 내 NATIVE인 사용자 찾기
	 */
	@Query("""
		SELECT DISTINCT l.user.id FROM Language l
		WHERE l.user.id != :userId
		AND EXISTS (
			SELECT 1 FROM Language l2
			WHERE l2.user.id = l.user.id
			AND l2.languageCode = :myLearningCode
			AND l2.type = :nativeType
		)
		AND EXISTS (
			SELECT 1 FROM Language l3
			WHERE l3.user.id = l.user.id
			AND l3.languageCode = :myNativeCode
			AND l3.type = :learningType
		)
		""")
	List<Long> findMatchingUserIds(
		@Param("userId") Long userId,
		@Param("myNativeCode") String myNativeCode,
		@Param("myLearningCode") String myLearningCode,
		@Param("nativeType") LanguageType nativeType,
		@Param("learningType") LanguageType learningType
	);

	/**
	 * 언어 교환 매칭 (편의 메서드)
	 * - 기본 NATIVE/LEARNING 타입 사용
	 */
	default List<Long> findMatchingUserIds(Long userId, String myNativeCode, String myLearningCode) {
		return findMatchingUserIds(userId, myNativeCode, myLearningCode, NATIVE_TYPE, LEARNING_TYPE);
	}

	/**
	 * 특정 레벨 이상의 사용자 조회 (매칭 필터용)
	 */
	@Query("SELECT l FROM Language l WHERE l.languageCode = :languageCode AND l.type = :type AND l.level >= :minLevel")
	List<Language> findByLanguageCodeAndTypeAndLevelGreaterThanEqual(
		@Param("languageCode") String languageCode,
		@Param("type") LanguageType type,
		@Param("minLevel") LanguageLevel minLevel
	);
}
