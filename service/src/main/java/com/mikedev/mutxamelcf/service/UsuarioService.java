package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.UsuarioDTO;

public interface UsuarioService {
	
	UsuarioDTO validarUsuario(String usuario, String password);

}
