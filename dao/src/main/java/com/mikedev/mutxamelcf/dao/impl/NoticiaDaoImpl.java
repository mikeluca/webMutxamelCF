package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.NoticiaDao;
import com.mikedev.mutxamelcf.model.Noticia;

@Repository
public class NoticiaDaoImpl implements NoticiaDao {

	private final JdbcTemplate jdbcTemplate;

	public NoticiaDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardarNoticia(Noticia noticia) {
		String sql = "INSERT INTO noticias (titulo, contenido, fecha, imagen) VALUES (?, ?, ?, ?)";

		return (jdbcTemplate.update(sql, noticia.getTitulo(), noticia.getContenido(), noticia.getFecha(),
				noticia.getImagen()) == 1);
	}

	@SuppressWarnings("deprecation")
	@Override
	public Noticia obtenerNoticiaPorId(int id) {
		String sql = "SELECT * FROM noticias WHERE id = ?";

		return jdbcTemplate.queryForObject(sql, new Object[] { id }, new RowMapper<Noticia>() {
			@Override
			public Noticia mapRow(ResultSet rs, int rowNum) throws SQLException {
				Noticia noticia = new Noticia();
				noticia.setId(rs.getInt("id"));
				noticia.setTitulo(rs.getString("titulo"));
				noticia.setContenido(rs.getString("contenido"));
				noticia.setFecha(rs.getDate("fecha"));
				noticia.setImagen(rs.getBytes("imagen"));
				return noticia;
			}
		});
	}

	@Override
	public List<Noticia> obtenerNoticiasParaMostrar() {
		String sql = "SELECT * FROM (SELECT * FROM noticias ORDER BY fecha DESC) WHERE ROWNUM <= 3";

		return jdbcTemplate.query(sql, new RowMapper<Noticia>() {
			@Override
			public Noticia mapRow(ResultSet rs, int rowNum) throws SQLException {
				Noticia noticia = new Noticia();
				noticia.setId(rs.getInt("id"));
				noticia.setTitulo(rs.getString("titulo"));
				noticia.setContenido(rs.getString("contenido"));
				noticia.setFecha(rs.getDate("fecha"));

				// Manejo del campo BLOB
				Blob blob = rs.getBlob("imagen");
				if (blob != null) {
					// Convertir el BLOB a un byte[]
					noticia.setImagen(blob.getBytes(1, (int) blob.length()));
				} else {
					noticia.setImagen(null); // o inicializar con un arreglo vacío
				}

				return noticia;
			}
		});
	}

	@Override
	public void eliminarNoticia(int id) {
		String sql = "DELETE FROM noticias WHERE id = ?";
		jdbcTemplate.update(sql, id);
	}

	@Override
	public List<Noticia> obtenerTodas() {
		String sql = "SELECT * FROM noticias ORDER BY fecha DESC";

		return jdbcTemplate.query(sql, new RowMapper<Noticia>() {
			@Override
			public Noticia mapRow(ResultSet rs, int rowNum) throws SQLException {
				Noticia noticia = new Noticia();
				noticia.setId(rs.getInt("id"));
				noticia.setTitulo(rs.getString("titulo"));
				noticia.setContenido(rs.getString("contenido"));
				noticia.setFecha(rs.getDate("fecha"));

				// Manejo del campo BLOB
				Blob blob = rs.getBlob("imagen");
				if (blob != null) {
					// Convertir el BLOB a un byte[]
					noticia.setImagen(blob.getBytes(1, (int) blob.length()));
				} else {
					noticia.setImagen(null); // o inicializar con un arreglo vacío
				}

				return noticia;
			}
		});
	}

}
