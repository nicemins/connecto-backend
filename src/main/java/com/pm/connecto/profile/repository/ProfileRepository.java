package com.pm.connecto.profile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.profile.domain.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

	@Query("SELECT p FROM Profile p WHERE p.user.id = :userId")
	Optional<Profile> findByUserId(@Param("userId") Long userId);

	boolean existsByUserId(Long userId);

	boolean existsByNickname(String nickname);

	@Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Profile p WHERE p.nickname = :nickname AND p.user.id != :userId")
	boolean existsByNicknameAndUserIdNot(@Param("nickname") String nickname, @Param("userId") Long userId);
}
