package com.mikedev.mutxamelcf.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        AuthenticationManager authManager(
                        HttpSecurity http,
                        CustomAuthenticationProvider customAuthenticationProvider) throws Exception {

                AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(
                                AuthenticationManagerBuilder.class);

                authenticationManagerBuilder
                                .authenticationProvider(
                                                customAuthenticationProvider);

                return authenticationManagerBuilder.build();
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter)
                        throws Exception {

                http

                                /*
                                 * CSRF:
                                 *
                                 * La web continúa protegida mediante CSRF.
                                 * Las APIs REST de la aplicación móvil quedan
                                 * excluidas porque Flutter no utiliza la sesión
                                 * ni el formulario web.
                                 */
                                .csrf(csrf -> csrf
                                                .ignoringRequestMatchers(
                                                                "/api/public/**",
                                                                "/api/app/**"))

                                .authorizeHttpRequests(authorize -> authorize

                                                /*
                                                 * WEB PÚBLICA
                                                 */
                                                .requestMatchers(
                                                                "/",
                                                                "/css/**",
                                                                "/images/**")
                                                .permitAll()

                                                /*
                                                 * API PÚBLICA
                                                 */
                                                .requestMatchers(
                                                                "/api/public/**")
                                                .permitAll()

                                                /*
                                                 * AUTENTICACIÓN DE LA APP
                                                 *
                                                 * Login y activación no requieren JWT.
                                                 */
                                                .requestMatchers(
                                                                "/api/app/auth/login",
                                                                "/api/app/auth/activar")
                                                .permitAll()

                                                /*
                                                 * API PRIVADA DE LA APP
                                                 *
                                                 * Requiere JWT válido.
                                                 */
                                                .requestMatchers(
                                                                "/api/app/**")
                                                .authenticated()

                                                /*
                                                 * ADMINISTRACIÓN WEB
                                                 */
                                                .requestMatchers(
                                                                "/admin/pagos/**")
                                                .hasRole("SUPER")

                                                .requestMatchers(
                                                                "/admin/**")
                                                .authenticated()

                                                /*
                                                 * RESTO DE PETICIONES
                                                 */
                                                .anyRequest()
                                                .permitAll())

                                /*
                                 * LOGIN WEB ACTUAL
                                 *
                                 * No lo modificamos.
                                 */
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .defaultSuccessUrl(
                                                                "/admin/admin",
                                                                true)
                                                .failureUrl(
                                                                "/login?error=true")
                                                .permitAll())

                                /*
                                 * LOGOUT WEB
                                 */
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/index")
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .permitAll())

                                /*
                                 * MANEJO DE AUTENTICACIÓN:
                                 *
                                 * API móvil:
                                 * -> 401 Unauthorized
                                 *
                                 * Web de administración:
                                 * -> /login
                                 */
                                .exceptionHandling(exception -> exception

                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                PathPatternRequestMatcher.withDefaults()
                                                                                .matcher("/api/app/**"))

                                                .defaultAuthenticationEntryPointFor(
                                                                new LoginUrlAuthenticationEntryPoint("/login"),
                                                                PathPatternRequestMatcher.withDefaults()
                                                                                .matcher("/admin/**")));

                /*
                 * El filtro JWT se ejecuta antes del filtro de autenticación
                 * estándar de Spring Security.
                 */
                http.addFilterBefore(
                                jwtAuthenticationFilter,
                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}