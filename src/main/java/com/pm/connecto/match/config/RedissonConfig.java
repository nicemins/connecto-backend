package com.pm.connecto.match.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 설정 (분산 락용)
 * - spring.data.redis.host가 명시적으로 설정되어 있을 때만 활성화 (테스트 환경에서는 비활성화)
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class RedissonConfig {

	@Value("${spring.data.redis.host:localhost}")
	private String redisHost;

	@Value("${spring.data.redis.port:6379}")
	private int redisPort;

	@Value("${spring.data.redis.password:}")
	private String redisPassword;

	@Bean
	public RedissonClient redissonClient() {
		Config config = new Config();
		String address = String.format("redis://%s:%d", redisHost, redisPort);
		
		config.useSingleServer()
			.setAddress(address)
			.setConnectionMinimumIdleSize(5)
			.setConnectionPoolSize(16)
			.setTimeout(3000)
			.setRetryAttempts(3)
			.setRetryInterval(1500);

		if (redisPassword != null && !redisPassword.isEmpty()) {
			config.useSingleServer().setPassword(redisPassword);
		}

		return Redisson.create(config);
	}
}
