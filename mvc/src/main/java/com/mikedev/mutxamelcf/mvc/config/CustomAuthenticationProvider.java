package com.mikedev.mutxamelcf.mvc.config;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	private UsuarioService userService;

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		String password = (String) authentication.getCredentials();

		UsuarioDTO usuario = userService.validarUsuario(username, password);
		if (usuario == null) {
			throw new BadCredentialsException("Credenciales incorrectas");
		}

		// Aquí podrías agregar roles y permisos si es necesario
		return new UsernamePasswordAuthenticationToken(usuario.getUsuario(), password, Collections.emptyList());
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
