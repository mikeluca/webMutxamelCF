package com.mikedev.mutxamelcf.mvc.config;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.UsuarioDTO;
import com.mikedev.mutxamelcf.service.UsuarioService;

@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {

	private static final Logger logger = LoggerFactory.getLogger(CustomAuthenticationProvider.class);

	private final UsuarioService userService;

	public CustomAuthenticationProvider(UsuarioService userService) {
		this.userService = userService;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		String password = (String) authentication.getCredentials();
		// Nunca se registra la contraseña, solo el nombre de usuario
		logger.debug("Inicio authenticate: username={}", username);

		UsuarioDTO usuario = userService.validarUsuario(username, password);
		if (usuario == null) {
			logger.warn("Autenticacion fallida, credenciales incorrectas: username={}", username);
			throw new BadCredentialsException("Credenciales incorrectas");
		}

		// Aquí podrías agregar roles y permisos si es necesario
		logger.debug("Fin authenticate: username={}, autenticado=true", username);
		String rol = usuario.getRol() == null ? "" : usuario.getRol().trim().toUpperCase(java.util.Locale.ROOT);
		return new UsernamePasswordAuthenticationToken(usuario.getUsuario(), password,
				rol.isBlank() ? Collections.emptyList()
						: List.of(
								new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + rol)));
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
