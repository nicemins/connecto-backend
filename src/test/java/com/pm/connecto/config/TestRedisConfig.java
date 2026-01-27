package com.pm.connecto.config;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

import com.pm.connecto.match.service.MatchQueueService;
import com.pm.connecto.match.service.MatchService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 테스트용 Redis 설정
 * - 실제 Redis 연결 없이 Mock 객체 제공
 * - MatchQueueService와 MatchService도 Mock으로 제공하여 테스트 환경에서 안전하게 동작
 * - 실제 서비스가 조건부로 생성되지 않을 때 사용됨
 */
@TestConfiguration
public class TestRedisConfig {

	@Bean
	@Primary
	public RedisTemplate<String, String> redisTemplate() {
		// Mock RedisTemplate 반환 (실제 Redis 연결 없이 테스트 가능)
		return mock(RedisTemplate.class);
	}

	@Bean
	@Primary
	public RedissonClient redissonClient() {
		// Mock RedissonClient 및 RLock 설정
		RedissonClient mockClient = mock(RedissonClient.class);
		RLock mockLock = mock(RLock.class);
		
		try {
			// 락 획득 시도가 항상 성공하도록 설정
			when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
			when(mockClient.getLock(anyString())).thenReturn(mockLock);
		} catch (Exception e) {
			// InterruptedException은 발생하지 않음
		}
		
		return mockClient;
	}

	@Bean
	@Primary
	public MatchQueueService matchQueueService() {
		// MatchQueueService를 Mock으로 제공하여 @PostConstruct 문제 방지
		return mock(MatchQueueService.class);
	}

	@Bean
	@Primary
	public MatchService matchService() {
		// MatchService를 Mock으로 제공하여 테스트 환경에서 안전하게 동작
		return mock(MatchService.class);
	}
}
