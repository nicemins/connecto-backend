package com.pm.connecto.match.service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.LockAcquisitionException;
import com.pm.connecto.common.response.ErrorCode;

import jakarta.annotation.PostConstruct;

/**
 * Redis 기반 매칭 대기열 서비스 (프로덕션 수준)
 * - Sorted Set을 활용한 FIFO 대기열
 * - 분산 락을 통한 동시성 제어
 * - Lua 스크립트를 통한 원자적 매칭 연산
 * - spring.data.redis.host가 명시적으로 설정되어 있을 때만 활성화 (테스트 환경에서는 비활성화)
 */
@Service
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class MatchQueueService {

	private static final Logger log = LoggerFactory.getLogger(MatchQueueService.class);
	private static final String QUEUE_KEY = "match:queue";
	private static final String USER_QUEUE_KEY_PREFIX = "match:user:";
	private static final String LOCK_KEY = "match:lock";
	private static final long QUEUE_TIMEOUT_SECONDS = 300; // 5분 타임아웃
	private static final int LOCK_WAIT_SECONDS = 3;
	private static final int LOCK_LEASE_SECONDS = 10;

	private final RedisTemplate<String, String> redisTemplate;
	private final RedissonClient redissonClient;
	private DefaultRedisScript<Long> atomicMatchScript;

	public MatchQueueService(RedisTemplate<String, String> redisTemplate, RedissonClient redissonClient) {
		this.redisTemplate = redisTemplate;
		this.redissonClient = redissonClient;
	}

	@PostConstruct
	public void init() {
		// Lua 스크립트 초기화 (인라인으로 작성)
		atomicMatchScript = new DefaultRedisScript<>();
		atomicMatchScript.setScriptText(
			// 두 사용자를 원자적으로 제거하는 Lua 스크립트
			// 반환값: 0=실패, 1=성공
			"local queueKey = KEYS[1]\n" +
			"local user1 = ARGV[1]\n" +
			"local user2 = ARGV[2]\n" +
			"local removed1 = redis.call('ZREM', queueKey, user1)\n" +
			"local removed2 = redis.call('ZREM', queueKey, user2)\n" +
			"if removed1 == 1 and removed2 == 1 then\n" +
			"  return 1\n" +
			"else\n" +
			"  -- 롤백: 하나라도 실패하면 둘 다 다시 추가 (원래 score 유지 필요)\n" +
			"  if removed1 == 1 then\n" +
			"    local score1 = ARGV[3]\n" +
			"    redis.call('ZADD', queueKey, score1, user1)\n" +
			"  end\n" +
			"  if removed2 == 1 then\n" +
			"    local score2 = ARGV[4]\n" +
			"    redis.call('ZADD', queueKey, score2, user2)\n" +
			"  end\n" +
			"  return 0\n" +
			"end"
		);
		atomicMatchScript.setResultType(Long.class);
	}

	/**
	 * 대기열 진입
	 * - 락을 먼저 획득한 후 isInQueue 체크 및 삽입 수행 (Race Condition 방지)
	 * - 분산 락을 사용하여 동시성 제어
	 * - Sorted Set에 타임스탬프와 함께 추가
	 */
	public void enqueue(Long userId) {
		RLock lock = redissonClient.getLock(LOCK_KEY);
		try {
			// 락 획득 시도 (시작 시점부터 락 보호)
			if (lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
				try {
					// 락 내부에서 isInQueue 체크 (Race Condition 방지)
					if (isInQueueLocked(userId)) {
						log.warn("User {} is already in queue", userId);
						throw new DuplicateResourceException(ErrorCode.ALREADY_IN_QUEUE);
					}

					// 진행 중인 통화가 있는지 확인은 MatchService에서 처리

					// Sorted Set에 추가 (score = 현재 시간)
					long score = System.currentTimeMillis();
					redisTemplate.opsForZSet().add(QUEUE_KEY, String.valueOf(userId), score);
					
					// 사용자별 대기열 키 설정 (타임아웃 관리용)
					String userQueueKey = USER_QUEUE_KEY_PREFIX + userId;
					redisTemplate.opsForValue().set(userQueueKey, "1", QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

					log.info("User {} entered match queue", userId);
				} finally {
					lock.unlock();
				}
			} else {
				log.error("Failed to acquire lock for enqueue (userId: {})", userId);
				throw new LockAcquisitionException("대기열 진입을 위한 락 획득에 실패했습니다.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Interrupted while acquiring lock for enqueue (userId: {})", userId, e);
			throw new LockAcquisitionException("대기열 진입 중 인터럽트가 발생했습니다.");
		}
	}

	/**
	 * 대기열 이탈
	 * - 예외 처리 강화: 락 획득 실패 시 예외 발생
	 */
	public void dequeue(Long userId) {
		RLock lock = redissonClient.getLock(LOCK_KEY);
		try {
			if (lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
				try {
					redisTemplate.opsForZSet().remove(QUEUE_KEY, String.valueOf(userId));
					redisTemplate.delete(USER_QUEUE_KEY_PREFIX + userId);
					log.info("User {} left match queue", userId);
				} finally {
					lock.unlock();
				}
			} else {
				log.error("Failed to acquire lock for dequeue (userId: {})", userId);
				throw new LockAcquisitionException("대기열 이탈을 위한 락 획득에 실패했습니다.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Interrupted while acquiring lock for dequeue (userId: {})", userId, e);
			throw new LockAcquisitionException("대기열 이탈 중 인터럽트가 발생했습니다.");
		}
	}

	/**
	 * 대기열에서 매칭 상대 찾기 (FIFO)
	 * - 가장 오래된 사용자와 매칭
	 * - Lua 스크립트를 통한 원자적 연산으로 Race Condition 완벽 방지
	 * - 롤백 시 원래 score 유지하여 FIFO 순서 보장
	 */
	public Long findMatch(Long userId) {
		RLock lock = redissonClient.getLock(LOCK_KEY);
		try {
			if (lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
				try {
					// 현재 사용자가 대기열에 있는지 확인
					if (!isInQueueLocked(userId)) {
						return null;
					}

					ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
					
					// 대기열에서 가장 오래된 사용자부터 순회 (score 기준 오름차순)
					Set<ZSetOperations.TypedTuple<String>> queueMembers = zSetOps.rangeWithScores(QUEUE_KEY, 0, -1);
					
					if (queueMembers == null || queueMembers.isEmpty()) {
						return null;
					}

					String userIdStr = String.valueOf(userId);
					
					// 현재 사용자의 원래 score 조회 (롤백 시 사용)
					Double userScore = zSetOps.score(QUEUE_KEY, userIdStr);
					if (userScore == null) {
						// 이미 대기열에서 제거된 경우
						return null;
					}
					
					// 가장 오래된 사용자부터 순회
					for (ZSetOperations.TypedTuple<String> tuple : queueMembers) {
						String candidateUserIdStr = tuple.getValue();
						
						if (candidateUserIdStr == null) {
							continue;
						}
						
						Long candidateUserId = Long.parseLong(candidateUserIdStr);
						
						// 자신은 제외
						if (candidateUserId.equals(userId)) {
							continue;
						}

						// 후보자의 원래 score 저장 (롤백 시 사용)
						Double candidateScore = tuple.getScore();
						
						if (candidateScore == null) {
							// 이미 제거된 경우 다음 후보 시도
							continue;
						}

						// Lua 스크립트를 통한 원자적 제거 연산
						Long result = redisTemplate.execute(
							atomicMatchScript,
							java.util.Collections.singletonList(QUEUE_KEY),
							userIdStr,
							candidateUserIdStr,
							userScore.toString(),
							candidateScore.toString()
						);

						if (result != null && result == 1) {
							// 매칭 성공: 두 사용자 모두 원자적으로 제거됨
							// 사용자별 키도 삭제
							redisTemplate.delete(USER_QUEUE_KEY_PREFIX + userId);
							redisTemplate.delete(USER_QUEUE_KEY_PREFIX + candidateUserId);
							
							log.info("Matched users: {} and {} (atomic operation)", userId, candidateUserId);
							return candidateUserId;
						}
						// 실패 시 다음 후보 시도 (Lua 스크립트가 자동으로 롤백 처리)
					}

					return null;
				} finally {
					lock.unlock();
				}
			} else {
				log.error("Failed to acquire lock for findMatch (userId: {})", userId);
				return null; // findMatch는 실패해도 예외를 던지지 않음 (재시도 가능)
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Interrupted while acquiring lock for findMatch (userId: {})", userId, e);
			return null;
		}
	}

	/**
	 * 대기열에 있는지 확인 (락 보호 버전)
	 * - 락 내부에서 호출되어야 함
	 */
	private boolean isInQueueLocked(Long userId) {
		Double score = redisTemplate.opsForZSet().score(QUEUE_KEY, String.valueOf(userId));
		return score != null;
	}

	/**
	 * 대기열에 있는지 확인 (공개 메서드)
	 * - 락 없이 호출 가능 (읽기 전용이므로)
	 */
	public boolean isInQueue(Long userId) {
		Double score = redisTemplate.opsForZSet().score(QUEUE_KEY, String.valueOf(userId));
		return score != null;
	}

	/**
	 * 대기열 크기 조회
	 */
	public long getQueueSize() {
		Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
		return size != null ? size : 0;
	}

	/**
	 * 타임아웃된 사용자 정리
	 */
	public void cleanupExpiredUsers() {
		long currentTime = System.currentTimeMillis();
		long expiredTime = currentTime - (QUEUE_TIMEOUT_SECONDS * 1000);
		
		Long removed = redisTemplate.opsForZSet().removeRangeByScore(QUEUE_KEY, 0, expiredTime);
		if (removed != null && removed > 0) {
			log.info("Cleaned up {} expired users from queue", removed);
		}
	}
}
