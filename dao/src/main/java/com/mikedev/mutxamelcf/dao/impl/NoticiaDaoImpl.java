package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.NoticiaDao;
import com.mikedev.mutxamelcf.model.Noticia;

@Repository
public class NoticiaDaoImpl implements NoticiaDao {

	private static final Logger logger = LoggerFactory.getLogger(NoticiaDaoImpl.class);

	private static final RowMapper<Noticia> NOTICIA_ROW_MAPPER = NoticiaDaoImpl::mapRow;

	private final JdbcTemplate jdbcTemplate;

	public NoticiaDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardarNoticia(Noticia noticia) {
		logger.debug("Inicio guardarNoticia: id={}, titulo={}", noticia.getId(), noticia.getTitulo());
		boolean resultado = noticia.getId() > 0 ? actualizarNoticia(noticia) : insertarNoticia(noticia);
		logger.debug("Fin guardarNoticia: resultado={}", resultado);
		return resultado;
	}

	private boolean insertarNoticia(Noticia noticia) {
		String sql = "INSERT INTO noticias (titulo, contenido, fecha, imagen) VALUES (?, ?, ?, ?)";

		boolean insertada = jdbcTemplate.update(sql, noticia.getTitulo(), noticia.getContenido(), noticia.getFecha(),
				noticia.getImagen()) == 1;
		logger.info("Noticia insertada: titulo={}, insertada={}", noticia.getTitulo(), insertada);
		return insertada;
	}

	private boolean actualizarNoticia(Noticia noticia) {
		String sql = "UPDATE noticias SET titulo = ?, contenido = ?, imagen = ? WHERE id = ?";

		boolean actualizada = jdbcTemplate.update(sql, noticia.getTitulo(), noticia.getContenido(),
				noticia.getImagen(), noticia.getId()) == 1;
		logger.info("Noticia actualizada: id={}, actualizada={}", noticia.getId(), actualizada);
		return actualizada;
	}

	@Override
	public Noticia obtenerNoticiaPorId(int id) {
		logger.debug("Inicio obtenerNoticiaPorId: id={}", id);
		String sql = "SELECT * FROM noticias WHERE id = ?";
		Noticia noticia = jdbcTemplate.queryForObject(sql, NOTICIA_ROW_MAPPER, id);
		logger.debug("Fin obtenerNoticiaPorId: id={}", id);
		return noticia;
	}

	@Override
	public List<Noticia> obtenerNoticiasParaMostrar() {
		logger.debug("Inicio obtenerNoticiasParaMostrar");
		String sql = "SELECT * FROM (SELECT * FROM noticias ORDER BY fecha DESC) WHERE ROWNUM <= 4";
		List<Noticia> noticias = jdbcTemplate.query(sql, NOTICIA_ROW_MAPPER);
		logger.debug("Fin obtenerNoticiasParaMostrar: total={}", noticias.size());
		return noticias;
	}

	@Override
	public void eliminarNoticia(int id) {
		logger.debug("Inicio eliminarNoticia: id={}", id);
		String sql = "DELETE FROM noticias WHERE id = ?";
		int filasAfectadas = jdbcTemplate.update(sql, id);
		logger.info("Noticia eliminada: id={}, filasAfectadas={}", id, filasAfectadas);
		logger.debug("Fin eliminarNoticia: id={}", id);
	}

	@Override
	public List<Noticia> obtenerTodas() {
		logger.debug("Inicio obtenerTodas");
		String sql = "SELECT * FROM noticias ORDER BY fecha DESC";
		List<Noticia> noticias = jdbcTemplate.query(sql, NOTICIA_ROW_MAPPER);
		logger.debug("Fin obtenerTodas: total={}", noticias.size());
		return noticias;
	}

	private static Noticia mapRow(ResultSet rs, int rowNum) throws SQLException {
		Noticia noticia = new Noticia();
		noticia.setId(rs.getInt("id"));
		noticia.setTitulo(rs.getString("titulo"));
		noticia.setContenido(rs.getString("contenido"));
		noticia.setFecha(rs.getDate("fecha"));

		Blob blob = rs.getBlob("imagen");
		noticia.setImagen(blob != null ? blob.getBytes(1, (int) blob.length()) : null);

		return noticia;
	}

}
