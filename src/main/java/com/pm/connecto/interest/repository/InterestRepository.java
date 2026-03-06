package com.pm.connecto.interest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pm.connecto.interest.domain.Interest;

public interface InterestRepository extends JpaRepository<Interest, Long> {

	List<Interest> findByUserId(Long userId);

	boolean existsByUserIdAndTag(Long userId, String tag);

	int countByUserId(Long userId);

	Optional<Interest> findByIdAndUserId(Long id, Long userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM Interest i WHERE i.user.id = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
