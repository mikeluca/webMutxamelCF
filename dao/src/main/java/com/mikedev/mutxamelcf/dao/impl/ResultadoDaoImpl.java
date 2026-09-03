package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ResultadoDao;
import com.mikedev.mutxamelcf.model.Resultado;

@Repository
public class ResultadoDaoImpl implements ResultadoDao {

	private static final Logger logger = LoggerFactory.getLogger(ResultadoDaoImpl.class);

	private static final RowMapper<Resultado> RESULTADO_ROW_MAPPER = ResultadoDaoImpl::mapRow;

	private final JdbcTemplate jdbcTemplate;

	public ResultadoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void actualizarResultado(Resultado resultado) {
		logger.debug("Inicio actualizarResultado: categoria={}, equipo={}", resultado.getCategoria(),
				resultado.getEquipo());
		java.sql.Date dateSQL = resultado.getDia() != null ? new java.sql.Date(resultado.getDia().getTime()) : null;

		String sql = "UPDATE RESULTADOS SET RIVAL = ?, RESULTADO = ?, DIA = ?, HORA = ?, CAMPO = ? WHERE CATEGORIA = ? AND EQUIPO = ?";
		int filasAfectadas = jdbcTemplate.update(sql, resultado.getRival(), resultado.getResultado(), dateSQL,
				resultado.getHora(), resultado.getCampo(), resultado.getCategoria(), resultado.getEquipo());
		logger.info("Resultado actualizado: categoria={}, equipo={}, filasAfectadas={}", resultado.getCategoria(),
				resultado.getEquipo(), filasAfectadas);
		logger.debug("Fin actualizarResultado: filasAfectadas={}", filasAfectadas);
	}

	@Override
	public List<Resultado> obtenerResultados(String deporte) {
		logger.debug("Inicio obtenerResultados: deporte={}", deporte);
		String sql = "SELECT * FROM resultados WHERE deporte = ? ORDER BY orden DESC, equipo";
		List<Resultado> resultados = jdbcTemplate.query(sql, RESULTADO_ROW_MAPPER, deporte);
		logger.debug("Fin obtenerResultados: deporte={}, total={}", deporte, resultados.size());
		return resultados;
	}

	@Override
	public Resultado obtenerResultadoPrimerEquipo() {
		logger.debug("Inicio obtenerResultadoPrimerEquipo");

		String sql = """
				SELECT *
				FROM resultados
				WHERE deporte = ?
				  AND categoria = ?
				  AND equipo = ?
				""";

		List<Resultado> resultados = jdbcTemplate.query(
				sql,
				RESULTADO_ROW_MAPPER,
				"F",
				"Primer Equipo",
				"Mutxamel CF");

		Resultado resultado = resultados.isEmpty()
				? null
				: resultados.get(0);

		logger.debug(
				"Fin obtenerResultadoPrimerEquipo: encontrado={}",
				resultado != null);

		return resultado;
	}

	private static Resultado mapRow(ResultSet rs, int rowNum) throws SQLException {
		Resultado resultado = new Resultado();
		resultado.setCategoria(rs.getString("CATEGORIA"));
		resultado.setEquipo(rs.getString("EQUIPO"));
		resultado.setRival(rs.getString("RIVAL"));
		resultado.setResultado(rs.getString("RESULTADO"));
		resultado.setDia(rs.getDate("DIA"));
		resultado.setHora(rs.getString("HORA"));
		resultado.setCampo(rs.getString("CAMPO"));
		return resultado;
	}

}