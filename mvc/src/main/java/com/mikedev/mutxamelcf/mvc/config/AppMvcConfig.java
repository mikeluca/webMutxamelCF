package com.mikedev.mutxamelcf.mvc.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/*
 * BE-05: antes se extendía WebMvcConfigurationSupport para poder
 * sustituir el bean "localeResolver" por defecto. El problema es que la
 * sola presencia de un WebMvcConfigurationSupport en el contexto apaga
 * TODA la autoconfiguración MVC de Spring Boot (WebMvcAutoConfiguration
 * está @ConditionalOnMissingBean(WebMvcConfigurationSupport.class)):
 * el ObjectMapper de JacksonConfig dejaba de ser el que usan los
 * conversores JSON, /webjars/** no se servía y cualquier propiedad
 * spring.mvc.* se ignoraba.
 *
 * Implementando WebMvcConfigurer (una interfaz, sin @EnableWebMvc) y
 * declarando el bean con el nombre exacto "localeResolver", el propio
 * localeResolver() de Boot no se crea (está
 * @ConditionalOnMissingBean(name = "localeResolver")) y el resto de la
 * autoconfiguración de Boot se mantiene activa.
 */
@Configuration
public class AppMvcConfig implements WebMvcConfigurer {

	/*
	 * Idioma por defecto: castellano. El "valenciano" se identifica con el
	 * código ISO 639-1 "ca" (catalán y valenciano comparten código, no existe
	 * uno propio), pero esto es un detalle puramente técnico: en la interfaz
	 * la opción se muestra siempre como "Valencià", nunca como "Català".
	 */
	@Bean(name = "localeResolver")
	public LocaleResolver localeResolver() {
		CookieLocaleResolver resolver = new CookieLocaleResolver("idioma");
		resolver.setDefaultLocaleFunction(request -> new Locale("es"));
		resolver.setCookieMaxAge(java.time.Duration.ofDays(365));
		resolver.setCookiePath("/");
		return resolver;
	}

	@Bean
	public LocaleChangeInterceptor localeChangeInterceptor() {
		LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
		interceptor.setParamName("lang");
		return interceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(localeChangeInterceptor());
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
		registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
	}

	/*
	 * El SDK de Firebase Admin (notificaciones push) arrastra
	 * jackson-dataformat-xml como dependencia transitiva, y Boot la
	 * registra como conversor disponible. La app nunca necesita producir
	 * XML, y con el Accept comodín que manda cualquier fetch() sin
	 * cabecera explícita, ese conversor podría anteponerse al JSON, así
	 * que se elimina explícitamente.
	 */
	@Override
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.removeIf(converter -> converter instanceof MappingJackson2XmlHttpMessageConverter);
	}

}
