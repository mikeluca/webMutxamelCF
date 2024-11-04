package com.mikedev.mutxamelcf.service.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = { "com.mikedev.mutxamelcf.service", "com.mikedev.mutxamelcf.dao" })
public class AppServiceConfig {

}
