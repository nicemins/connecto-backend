package com.pm.connecto.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.pm.connecto.common.service.S3Service;
import com.pm.connecto.match.service.MatchQueueService;
import com.pm.connecto.match.service.MatchService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
	@SuppressWarnings("unchecked")
	public RedisTemplate<String, String> redisTemplate() {
		// 인메모리 Map으로 동작하는 Mock RedisTemplate
		ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

		ValueOperations<String, String> mockValueOps = mock(ValueOperations.class);
		doAnswer(inv -> {
			store.put(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(mockValueOps).set(anyString(), anyString(), any());
		when(mockValueOps.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
		when(mockValueOps.increment(anyString())).thenReturn(1L);

		RedisTemplate<String, String> mockTemplate = mock(RedisTemplate.class);
		when(mockTemplate.opsForValue()).thenReturn(mockValueOps);
		when(mockTemplate.expire(anyString(), any())).thenReturn(true);
		doAnswer(inv -> { store.remove(inv.getArgument(0).toString()); return true; })
			.when(mockTemplate).delete(anyString());

		return mockTemplate;
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

	@Bean
	@Primary
	public S3Service s3Service() {
		// S3Service를 Mock으로 제공하여 실제 AWS S3 호출 방지
		return mock(S3Service.class);
	}
}
