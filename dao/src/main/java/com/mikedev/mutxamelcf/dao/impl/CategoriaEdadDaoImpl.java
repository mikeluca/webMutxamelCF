package com.mikedev.mutxamelcf.dao.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CategoriaEdadDao;
import com.mikedev.mutxamelcf.model.CategoriaEdad;

@Repository
public class CategoriaEdadDaoImpl implements CategoriaEdadDao {

	private final JdbcTemplate jdbcTemplate;

	public CategoriaEdadDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<CategoriaEdad> obtenerTodas() {
		return jdbcTemplate.query(
				"SELECT CATEGORIA, EDAD_MIN, EDAD_MAX, ORDEN FROM CATEGORIAS_EDAD ORDER BY ORDEN, EDAD_MIN",
				(rs, n) -> new CategoriaEdad(rs.getString("CATEGORIA"), rs.getInt("EDAD_MIN"), rs.getInt("EDAD_MAX"),
						rs.getInt("ORDEN")));
	}

	@Override
	public void guardar(CategoriaEdad c) {
		int filas = jdbcTemplate.update(
				"UPDATE CATEGORIAS_EDAD SET EDAD_MIN = ?, EDAD_MAX = ?, ORDEN = ? WHERE CATEGORIA = ?",
				c.getEdadMin(), c.getEdadMax(), c.getOrden(), c.getCategoria());

		if (filas == 0) {
			jdbcTemplate.update("INSERT INTO CATEGORIAS_EDAD (CATEGORIA, EDAD_MIN, EDAD_MAX, ORDEN) VALUES (?, ?, ?, ?)",
					c.getCategoria(), c.getEdadMin(), c.getEdadMax(), c.getOrden());
		}
	}

	@Override
	public void eliminar(String categoria) {
		jdbcTemplate.update("DELETE FROM CATEGORIAS_EDAD WHERE CATEGORIA = ?", categoria);
	}
}
