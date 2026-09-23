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

	public Usuario obtenerPorUsuario(String usuario) {
		logger.debug("Inicio obtenerPorUsuario: usuario={}", usuario);
		String sql = "SELECT usuario, password, rol FROM usuarios WHERE usuario = ?";

		try {
			Usuario usuarioBD = jdbcTemplate.queryForObject(sql, usuarioRowMapper, usuario);
			logger.debug("Fin obtenerPorUsuario: usuario={}, encontrado={}", usuario, usuarioBD != null);
			return usuarioBD;
		} catch (EmptyResultDataAccessException e) {
			logger.warn("Usuario no encontrado: usuario={}", usuario);
			return null;
		}
	}

}
