package com.pm.connecto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConnectoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConnectoApplication.class, args);
	}

}
