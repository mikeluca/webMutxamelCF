package com.mikedev.mutxamelcf.dao.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.UsuarioDao;
import com.mikedev.mutxamelcf.model.Usuario;

@Repository
public class UsuarioDaoImpl implements UsuarioDao {

	private static final Logger logger = LoggerFactory.getLogger(UsuarioDaoImpl.class);

	// RowMapper para mapear los resultados de la consulta al objeto Usuario
	private final RowMapper<Usuario> usuarioRowMapper = (rs, rowNum) -> {
		Usuario usuario = new Usuario();
		usuario.setUsuario(rs.getString("usuario"));
		usuario.setPassword(rs.getString("password"));
		usuario.setRol(rs.getString("rol"));
		return usuario;
	};

	private final JdbcTemplate jdbcTemplate;

	public UsuarioDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Usuario validarUsuario(String usuario, String password) {
		logger.debug("Inicio validarUsuario: usuario={}", usuario);
		String sql = "SELECT usuario, password, rol FROM usuarios WHERE usuario = ?";

		try {
			Usuario usuarioBD = jdbcTemplate.queryForObject(sql, usuarioRowMapper, usuario);
			boolean credencialesValidas = usuarioBD != null && password.equals(usuarioBD.getPassword());
			if (!credencialesValidas) {
				logger.warn("Contraseña incorrecta para el usuario={}", usuario);
			}
			logger.debug("Fin validarUsuario: usuario={}, valido={}", usuario, credencialesValidas);
			return credencialesValidas ? usuarioBD : null;
		} catch (EmptyResultDataAccessException e) {
			logger.warn("Usuario no encontrado: usuario={}", usuario);
			return null;
		}
	}

}
