package com.pm.connecto.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pm.connecto.notification.domain.DeviceToken;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

	List<DeviceToken> findAllByUserId(Long userId);

	Optional<DeviceToken> findByToken(String token);

	void deleteByUserIdAndToken(Long userId, String token);

	void deleteAllByUserId(Long userId);
}
