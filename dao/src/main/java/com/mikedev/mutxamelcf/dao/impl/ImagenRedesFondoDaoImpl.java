package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ImagenRedesFondoDao;
import com.mikedev.mutxamelcf.model.FondoCartel;

@Repository
public class ImagenRedesFondoDaoImpl implements ImagenRedesFondoDao {

	private final JdbcTemplate jdbcTemplate;

	public ImagenRedesFondoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public FondoCartel obtener(String cartel) {

		List<FondoCartel> fondos = jdbcTemplate.query("""
				SELECT CARTEL, IMAGEN, TIPO_CONTENIDO, ANCHO, ALTO, FECHA_ACTUALIZACION, USUARIO_ACTUALIZO
				FROM IMAGENES_REDES_FONDOS
				WHERE CARTEL = ?
				""", (rs, n) -> {
			Timestamp fecha = rs.getTimestamp("FECHA_ACTUALIZACION");
			return new FondoCartel(rs.getString("CARTEL"), rs.getBytes("IMAGEN"), rs.getString("TIPO_CONTENIDO"),
					rs.getInt("ANCHO"), rs.getInt("ALTO"), fecha == null ? null : fecha.toLocalDateTime(),
					rs.getString("USUARIO_ACTUALIZO"));
		}, cartel);

		return fondos.isEmpty() ? null : fondos.get(0);
	}

	@Override
	public void guardar(String cartel, byte[] imagen, String tipoContenido, int ancho, int alto, String usuario) {

		int actualizadas = jdbcTemplate.update("""
				UPDATE IMAGENES_REDES_FONDOS
				SET IMAGEN = ?, TIPO_CONTENIDO = ?, ANCHO = ?, ALTO = ?, FECHA_ACTUALIZACION = SYSTIMESTAMP,
				    USUARIO_ACTUALIZO = ?
				WHERE CARTEL = ?
				""", imagen, tipoContenido, ancho, alto, usuario, cartel);

		if (actualizadas == 0) {
			jdbcTemplate.update("""
					INSERT INTO IMAGENES_REDES_FONDOS
					    (CARTEL, IMAGEN, TIPO_CONTENIDO, ANCHO, ALTO, FECHA_ACTUALIZACION, USUARIO_ACTUALIZO)
					VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP, ?)
					""", cartel, imagen, tipoContenido, ancho, alto, usuario);
		}
	}

	@Override
	public boolean borrar(String cartel) {
		return jdbcTemplate.update("DELETE FROM IMAGENES_REDES_FONDOS WHERE CARTEL = ?", cartel) > 0;
	}
}
