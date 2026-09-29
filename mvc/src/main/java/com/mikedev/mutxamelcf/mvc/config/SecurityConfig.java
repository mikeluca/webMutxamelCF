package com.mikedev.mutxamelcf.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authentication.LockedException;
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
                                 * SEC-04 / SEC-09: Content-Security-Policy. Limita de qué
                                 * orígenes puede cargarse script/estilo/fuente/imagen, a los
                                 * CDN y servicios que ya usan las plantillas actuales
                                 * (Bootstrap/jQuery/Chart.js por jsdelivr, Google Fonts,
                                 * Google Analytics/Tag Manager).
                                 *
                                 * N-05: 'unsafe-inline' en script-src sigue permitiendo que
                                 * se ejecute un atributo onerror=/onclick= o un <script>
                                 * inline igual que sin CSP -- así que esta política NO frena
                                 * el payload de SEC-04 (<img src=x onerror=...>); la defensa
                                 * real contra eso es el escapado en jugadores.html /
                                 * familiares.html. Quitar 'unsafe-inline' exige mover antes
                                 * los onclick=/onchange= inline del panel a
                                 * addEventListener (pendiente, ver auditoría). frame-src
                                 * permite el <iframe> de Google Maps de contacto.html, y
                                 * connect-src incluye los subdominios reales a los que envía
                                 * Google Analytics 4 (no solo google-analytics.com).
                                 */
                                .headers(headers -> headers
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                                                "default-src 'self'; "
                                                                                + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net "
                                                                                + "https://code.jquery.com https://www.googletagmanager.com; "
                                                                                + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net "
                                                                                + "https://fonts.googleapis.com; "
                                                                                + "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; "
                                                                                + "img-src 'self' data: https:; "
                                                                                + "frame-src https://www.google.com; "
                                                                                + "connect-src 'self' https://*.google-analytics.com "
                                                                                + "https://*.analytics.google.com; "
                                                                                + "frame-ancestors 'self'; base-uri 'self'; form-action 'self'")))

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
                                                 * N-01: sin esto, un token ya inválido (caducado,
                                                 * cuenta desactivada o JWT_SECRET rotado) hace que
                                                 * este DELETE devuelva 401, y la app entra en un
                                                 * bucle de logout -> DELETE -> 401 -> logout... El
                                                 * controlador exige igualmente un usuario
                                                 * autenticado para desactivar ningún token; esto
                                                 * solo permite que una petición SIN sesión válida
                                                 * llegue a él y reciba 204 en vez de 401.
                                                 */
                                                .requestMatchers(
                                                                org.springframework.http.HttpMethod.DELETE,
                                                                "/api/app/dispositivos")
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
                                                                "/admin/usuarios-app/**")
                                                .hasRole("SUPER")

                                                /*
                                                 * SEC-03: nunca solo authenticated() aqui.
                                                 * Los unicos roles web que existen hoy son
                                                 * SUPER y ENTRENADOR (no hay ningun otro
                                                 * hasRole/sec:authorize en toda la app), asi
                                                 * que cualquier futuro controlador que cuelgue
                                                 * de /admin/** sin marcar su propio hasRole()
                                                 * queda protegido igualmente por defecto.
                                                 */
                                                .requestMatchers(
                                                                "/admin/**")
                                                .hasAnyRole("SUPER", "ENTRENADOR")

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
                                                .failureHandler((request, response, exception) -> {
                                                        String motivo = exception instanceof LockedException
                                                                        ? "bloqueado"
                                                                        : "true";
                                                        response.sendRedirect(
                                                                        request.getContextPath()
                                                                                        + "/login?error=" + motivo);
                                                })
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