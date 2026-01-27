package com.pm.connecto.common.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리 설정
 * - 매칭 폴링 등 비동기 작업용 스레드 풀
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "matchTaskExecutor")
	public Executor matchTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(5);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("match-async-");
		executor.initialize();
		return executor;
	}
}
