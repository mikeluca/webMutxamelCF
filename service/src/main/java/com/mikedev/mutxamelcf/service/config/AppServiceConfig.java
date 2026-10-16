package com.mikedev.mutxamelcf.service.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = { "com.mikedev.mutxamelcf.service", "com.mikedev.mutxamelcf.dao" })
public class AppServiceConfig {

	/**
	 * Reloj del club: las horas de los partidos son hora de España. Se
	 * inyecta (en vez de usar LocalDateTime.now()) para poder fijarlo en los
	 * tests.
	 */
	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Europe/Madrid"));
	}

}
