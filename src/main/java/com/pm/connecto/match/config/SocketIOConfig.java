package com.pm.connecto.match.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;

/**
 * Socket.io 서버 설정
 * - 실시간 매칭 알림을 위한 WebSocket 서버
 * - spring.data.redis.host가 명시적으로 설정되어 있을 때만 활성화
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class SocketIOConfig {

	@Value("${socketio.host:0.0.0.0}")
	private String host;

	@Value("${socketio.port:9092}")
	private Integer port;

	@Bean
	public SocketIOServer socketIOServer() {
		com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
		config.setHostname(host);
		config.setPort(port);
		
		// CORS 설정 - setAllowCustomRequests(true)로 CORS 허용
		// netty-socketio는 기본적으로 CORS를 지원하며, 커스텀 요청 허용 시 CORS도 함께 허용됨
		config.setAllowCustomRequests(true);
		
		config.setUpgradeTimeout(10000);
		config.setPingTimeout(60000);
		config.setPingInterval(25000);

		return new SocketIOServer(config);
	}

	@Bean
	public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketServer) {
		return new SpringAnnotationScanner(socketServer);
	}
}
