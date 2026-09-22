package com.mikedev.mutxamelcf.mvc.config;

import java.util.List;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.mikedev.mutxamelcf")
public class AppMvcConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
		registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
	}

	/*
	 * El SDK de Firebase Admin (notificaciones push) arrastra
	 * jackson-dataformat-xml como dependencia transitiva. Al usar
	 * @EnableWebMvc, Spring registra ese conversor XML antes que el
	 * JSON, así que con el Accept comodín que manda cualquier fetch()
	 * sin cabecera explícita, los @ResponseBody de la API se
	 * serializaban como XML en vez de JSON. La app nunca necesita
	 * producir XML, así que se elimina ese conversor.
	 */
	@Override
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.removeIf(converter -> converter instanceof MappingJackson2XmlHttpMessageConverter);
	}

}
