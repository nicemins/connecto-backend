package com.pm.connecto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.pm.connecto.config.TestRedisConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ConnectoApplicationTests {

	@Test
	void contextLoads() {
	}

}
