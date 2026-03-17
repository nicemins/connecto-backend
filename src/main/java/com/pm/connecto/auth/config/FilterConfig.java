package com.pm.connecto.auth.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pm.connecto.auth.filter.JwtAuthenticationFilter;

@Configuration
public class FilterConfig {

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
		JwtAuthenticationFilter jwtAuthenticationFilter
	) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(jwtAuthenticationFilter);
		registration.addUrlPatterns("/*");
		registration.setOrder(1);
		return registration;
	}
}
