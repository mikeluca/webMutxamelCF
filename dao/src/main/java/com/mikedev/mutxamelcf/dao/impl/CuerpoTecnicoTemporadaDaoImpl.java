package com.mikedev.mutxamelcf.dao.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoTemporadaDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoTemporadaFila;

@Repository
public class CuerpoTecnicoTemporadaDaoImpl implements CuerpoTecnicoTemporadaDao {

	private final JdbcTemplate jdbcTemplate;

	public CuerpoTecnicoTemporadaDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<CuerpoTecnicoTemporadaFila> listarPorTemporada(Long temporadaId) {
		return jdbcTemplate.query("""
				SELECT CTT.EQUIPO_ID, E.NOMBRE AS EQUIPO, CT.ID AS FICHA, CT.NOMBRE, CT.APELLIDOS, CTT.PUESTO
				FROM CUERPO_TECNICO_TEMPORADA CTT
				JOIN CUERPO_TECNICO CT ON CT.ID = CTT.CUERPO_TECNICO_ID
				JOIN EQUIPO E ON E.ID = CTT.EQUIPO_ID
				WHERE CTT.TEMPORADA_ID = ?
				ORDER BY E.ORDEN, E.NOMBRE, CT.APELLIDOS, CT.NOMBRE
				""",
				(rs, n) -> new CuerpoTecnicoTemporadaFila(rs.getLong("EQUIPO_ID"), rs.getString("EQUIPO"),
						rs.getLong("FICHA"), rs.getString("NOMBRE"), rs.getString("APELLIDOS"), rs.getString("PUESTO")),
				temporadaId);
	}
}
