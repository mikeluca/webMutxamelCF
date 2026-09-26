package com.mikedev.mutxamelcf.mvc.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/*
 * Se extiende WebMvcConfigurationSupport (en vez de usar @EnableWebMvc +
 * WebMvcConfigurer) porque necesitamos SUSTITUIR el bean "localeResolver"
 * que el propio framework registra por defecto (AcceptHeaderLocaleResolver).
 * Con @EnableWebMvc ese bean lo define directamente DelegatingWebMvcConfiguration
 * (sin @ConditionalOnMissingBean), así que declarar nuestro propio @Bean
 * localeResolver() en una clase aparte choca con el suyo (BeanDefinitionOverrideException).
 * Extender WebMvcConfigurationSupport y sobrescribir su método localeResolver()
 * es la forma soportada por Spring de reemplazarlo, y equivale exactamente a lo
 * que hacía @EnableWebMvc (que internamente también extiende esta misma clase).
 */
@Configuration
@ComponentScan(basePackages = "com.mikedev.mutxamelcf")
public class AppMvcConfig extends WebMvcConfigurationSupport {

	/*
	 * Idioma por defecto: castellano. El "valenciano" se identifica con el
	 * código ISO 639-1 "ca" (catalán y valenciano comparten código, no existe
	 * uno propio), pero esto es un detalle puramente técnico: en la interfaz
	 * la opción se muestra siempre como "Valencià", nunca como "Català".
	 */
	@Bean
	@Override
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
	protected void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(localeChangeInterceptor());
	}

	@Override
	protected void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
		registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
	}

	/*
	 * El SDK de Firebase Admin (notificaciones push) arrastra
	 * jackson-dataformat-xml como dependencia transitiva. Al asumir el control
	 * completo de la configuración de MVC (antes vía @EnableWebMvc, ahora
	 * extendiendo WebMvcConfigurationSupport), Spring registra ese conversor
	 * XML antes que el JSON, así que con el Accept comodín que manda cualquier
	 * fetch() sin cabecera explícita, los @ResponseBody de la API se
	 * serializaban como XML en vez de JSON. La app nunca necesita producir
	 * XML, así que se elimina ese conversor.
	 */
	@Override
	protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.removeIf(converter -> converter instanceof MappingJackson2XmlHttpMessageConverter);
	}

}
