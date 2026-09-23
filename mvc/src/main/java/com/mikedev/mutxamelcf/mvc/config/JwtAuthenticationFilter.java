package com.mikedev.mutxamelcf.mvc.config;

import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioAppService usuarioAppService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UsuarioAppService usuarioAppService) {

        this.jwtService = jwtService;
        this.usuarioAppService = usuarioAppService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        /*
         * Si no existe Authorization o no empieza
         * por Bearer, dejamos continuar la petición.
         */
        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        /*
         * Si el JWT no es válido, no autenticamos
         * al usuario.
         */
        if (!jwtService.esValido(token)) {

            filterChain.doFilter(request, response);
            return;
        }

        try {

            int usuarioId = jwtService.extraerUsuarioId(token);

            /*
             * Si ya existe una autenticación en el contexto,
             * no la sustituimos.
             */
            if (SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                /*
                 * Comprobamos que el usuario sigue existiendo.
                 */
                UsuarioApp usuario = usuarioAppService.obtenerPorId(usuarioId);

                if (usuario == null || !usuario.isActivo()) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                /*
                 * Recuperamos los roles actuales desde Oracle.
                 */
                List<RolApp> roles = usuarioAppService.obtenerRoles(
                        usuarioId);

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(rol -> new SimpleGrantedAuthority(
                                "ROLE_" +
                                        rol.getCodigo()))
                        .toList();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        usuarioId,
                        null,
                        authorities);

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request));

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);
            }

        } catch (Exception e) {

            /*
             * Si el token tiene algún problema,
             * simplemente no autenticamos la petición.
             */
            SecurityContextHolder
                    .clearContext();
        }

        filterChain.doFilter(request, response);
    }
}