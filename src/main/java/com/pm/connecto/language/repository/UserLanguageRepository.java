package com.pm.connecto.language.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.language.domain.LanguageType;
import com.pm.connecto.language.domain.UserLanguage;

public interface UserLanguageRepository extends JpaRepository<UserLanguage, Long> {

	@Query("SELECT ul FROM UserLanguage ul JOIN FETCH ul.language WHERE ul.user.id = :userId")
	List<UserLanguage> findByUserIdWithLanguage(@Param("userId") Long userId);

	List<UserLanguage> findByUserIdAndType(Long userId, LanguageType type);

	boolean existsByUserIdAndLanguageIdAndType(Long userId, Long languageId, LanguageType type);

	int countByUserId(Long userId);

	Optional<UserLanguage> findByIdAndUserId(Long id, Long userId);
}
