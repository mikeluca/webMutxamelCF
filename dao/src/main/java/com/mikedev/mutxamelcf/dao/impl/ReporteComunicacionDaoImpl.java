package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ReporteComunicacionDao;
import com.mikedev.mutxamelcf.model.ReporteComunicacion;

@Repository
public class ReporteComunicacionDaoImpl implements ReporteComunicacionDao {

	private static final String CAMPOS = """
			ID, COMUNICACION_ID, AUTOR_ID, REPORTADOR_ID, MOTIVO, TITULO_COPIA, CONTENIDO_COPIA,
			FECHA_MENSAJE, FECHA_REPORTE, ESTADO, RESUELTO_POR_ID, FECHA_RESOLUCION, NOTA_RESOLUCION
			""";

	private static final RowMapper<ReporteComunicacion> MAPPER = ReporteComunicacionDaoImpl::mapear;

	private final JdbcTemplate jdbcTemplate;

	public ReporteComunicacionDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Long crear(ReporteComunicacion reporte) {

		KeyHolder keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO REPORTES_COMUNICACION
					    (COMUNICACION_ID, AUTOR_ID, REPORTADOR_ID, MOTIVO, TITULO_COPIA, CONTENIDO_COPIA,
					     FECHA_MENSAJE, FECHA_REPORTE, ESTADO)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE')
					""", new String[] { "ID" });

			ps.setLong(1, reporte.comunicacionId());

			if (reporte.autorId() == null) {
				ps.setNull(2, Types.NUMERIC);
			} else {
				ps.setLong(2, reporte.autorId());
			}

			ps.setLong(3, reporte.reportadorId());
			ps.setString(4, reporte.motivo());
			ps.setString(5, reporte.tituloCopia());
			ps.setString(6, reporte.contenidoCopia());
			ps.setTimestamp(7, reporte.fechaMensaje() == null ? null : Timestamp.valueOf(reporte.fechaMensaje()));
			ps.setTimestamp(8, Timestamp.valueOf(reporte.fechaReporte()));

			return ps;
		}, keyHolder);

		Number id = keyHolder.getKey();

		return id == null ? null : id.longValue();
	}

	@Override
	public boolean existe(Long comunicacionId, Long reportadorId) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM REPORTES_COMUNICACION WHERE COMUNICACION_ID = ? AND REPORTADOR_ID = ?",
				Integer.class, comunicacionId, reportadorId);

		return total != null && total > 0;
	}

	@Override
	public ReporteComunicacion obtenerPorId(Long id) {
		List<ReporteComunicacion> filas = jdbcTemplate.query(
				"SELECT " + CAMPOS + " FROM REPORTES_COMUNICACION WHERE ID = ?", MAPPER, id);

		return filas.isEmpty() ? null : filas.get(0);
	}

	@Override
	public List<ReporteComunicacion> listarPendientes() {
		return jdbcTemplate.query("SELECT " + CAMPOS
				+ " FROM REPORTES_COMUNICACION WHERE ESTADO = 'PENDIENTE' ORDER BY FECHA_REPORTE DESC, ID DESC",
				MAPPER);
	}

	@Override
	public List<ReporteComunicacion> listarResueltos(int limite) {
		return jdbcTemplate.query("SELECT " + CAMPOS
				+ " FROM REPORTES_COMUNICACION WHERE ESTADO <> 'PENDIENTE'"
				+ " ORDER BY FECHA_RESOLUCION DESC, ID DESC FETCH FIRST " + Math.max(1, limite) + " ROWS ONLY",
				MAPPER);
	}

	@Override
	public int contarPendientes() {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM REPORTES_COMUNICACION WHERE ESTADO = 'PENDIENTE'", Integer.class);

		return total == null ? 0 : total;
	}

	@Override
	public int contarDelAutor(Long autorId, Long excluirId) {
		if (autorId == null) {
			return 0;
		}

		Integer total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM REPORTES_COMUNICACION WHERE AUTOR_ID = ? AND ID <> ?",
				Integer.class, autorId, excluirId == null ? -1L : excluirId);

		return total == null ? 0 : total;
	}

	@Override
	public int resolverPendientesDeComunicacion(
			Long comunicacionId, String estado, Long resueltoPorId, LocalDateTime fecha, String nota) {

		return jdbcTemplate.update("""
				UPDATE REPORTES_COMUNICACION
				SET ESTADO = ?, RESUELTO_POR_ID = ?, FECHA_RESOLUCION = ?, NOTA_RESOLUCION = ?
				WHERE COMUNICACION_ID = ? AND ESTADO = 'PENDIENTE'
				""", estado, resueltoPorId, Timestamp.valueOf(fecha), nota, comunicacionId);
	}

	@Override
	public int resolver(Long id, String estado, Long resueltoPorId, LocalDateTime fecha, String nota) {

		return jdbcTemplate.update("""
				UPDATE REPORTES_COMUNICACION
				SET ESTADO = ?, RESUELTO_POR_ID = ?, FECHA_RESOLUCION = ?, NOTA_RESOLUCION = ?
				WHERE ID = ? AND ESTADO = 'PENDIENTE'
				""", estado, resueltoPorId, Timestamp.valueOf(fecha), nota, id);
	}

	private static ReporteComunicacion mapear(ResultSet rs, int fila) throws SQLException {

		long autor = rs.getLong("AUTOR_ID");
		Long autorId = rs.wasNull() ? null : autor;

		long resuelto = rs.getLong("RESUELTO_POR_ID");
		Long resueltoPorId = rs.wasNull() ? null : resuelto;

		return new ReporteComunicacion(
				rs.getLong("ID"), rs.getLong("COMUNICACION_ID"), autorId, rs.getLong("REPORTADOR_ID"),
				rs.getString("MOTIVO"), rs.getString("TITULO_COPIA"), rs.getString("CONTENIDO_COPIA"),
				fecha(rs.getTimestamp("FECHA_MENSAJE")), fecha(rs.getTimestamp("FECHA_REPORTE")),
				rs.getString("ESTADO"), resueltoPorId, fecha(rs.getTimestamp("FECHA_RESOLUCION")),
				rs.getString("NOTA_RESOLUCION"));
	}

	private static LocalDateTime fecha(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}
}
