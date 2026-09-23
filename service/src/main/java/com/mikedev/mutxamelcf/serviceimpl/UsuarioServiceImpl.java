package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.UsuarioDao;
import com.mikedev.mutxamelcf.model.Usuario;
import com.mikedev.mutxamelcf.model.UsuarioDTO;
import com.mikedev.mutxamelcf.service.UsuarioService;

@Service
public class UsuarioServiceImpl implements UsuarioService {

	private static final Logger logger = LoggerFactory.getLogger(UsuarioServiceImpl.class);

	private final UsuarioDao usuarioDao;

	public UsuarioServiceImpl(UsuarioDao usuarioDao) {
		this.usuarioDao = usuarioDao;
	}

	@Override
	public UsuarioDTO validarUsuario(String usuario, String password) {
		// Nunca se registra la contraseña, solo el nombre de usuario
		logger.debug("Inicio validarUsuario: usuario={}", usuario);
		UsuarioDTO usuarioDTO = toDTO(usuarioDao.validarUsuario(usuario, password));
		logger.debug("Fin validarUsuario: usuario={}, valido={}", usuario, usuarioDTO != null);
		return usuarioDTO;
	}

	// Método para mapear Usuario a UsuarioDTO
	private static UsuarioDTO toDTO(Usuario usuario) {
		if (usuario == null) {
			return null;
		}

		UsuarioDTO usuarioDTO = new UsuarioDTO();
		usuarioDTO.setId(usuario.getId());
		usuarioDTO.setUsuario(usuario.getUsuario());
		usuarioDTO.setPassword(usuario.getPassword());
		usuarioDTO.setRol(usuario.getRol());

		return usuarioDTO;
	}

}
