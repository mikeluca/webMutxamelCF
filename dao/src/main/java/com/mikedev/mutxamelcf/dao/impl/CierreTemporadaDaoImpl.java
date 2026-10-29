package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CierreTemporadaDao;
import com.mikedev.mutxamelcf.model.CifrasCierre;

@Repository
public class CierreTemporadaDaoImpl implements CierreTemporadaDao {

	/** El equipo (alias E) es de una categoría con regla de edad: formativo. */
	private static final String FORMATIVO = "EXISTS (SELECT 1 FROM CATEGORIAS_EDAD CE"
			+ " WHERE UPPER(TRIM(CE.CATEGORIA)) = UPPER(TRIM(E.CATEGORIA)))";

	/** El equipo (alias E) es de una categoría sin regla de edad: Filial, Primer Equipo, Veteranos. */
	private static final String ADULTO = "NOT " + FORMATIVO;

	/** Sesiones (alias S) desde una fecha sin ningún entrenamiento asociado. */
	private static final String SESION_SIN_ENTRENAMIENTO = "S.FECHA >= ? AND NOT EXISTS (SELECT 1 FROM ENTRENAMIENTOS EN"
			+ " WHERE EN.SESION_ENTRENAMIENTO_ID = S.ID)";

	private final JdbcTemplate jdbcTemplate;

	public CierreTemporadaDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private int contar(String sql, Object... parametros) {
		Integer total = jdbcTemplate.queryForObject(sql, Integer.class, parametros);
		return total == null ? 0 : total;
	}

	@Override
	public CifrasCierre cifras(Long origenId, Long destinoId, LocalDate inicioDestino) {
		return new CifrasCierre(
				contar("SELECT COUNT(*) FROM CUERPO_TECNICO_EQUIPO"),
				contar("SELECT COUNT(*) FROM CUERPO_TECNICO_EQUIPO CTE JOIN EQUIPO E ON E.ID = CTE.EQUIPO_ID WHERE "
						+ FORMATIVO),
				contar("SELECT COUNT(*) FROM EQUIPO E WHERE " + ADULTO),
				contar("SELECT COUNT(*) FROM EQUIPO E WHERE " + FORMATIVO),
				contar("SELECT COUNT(*) FROM HORARIOS_ENTRENAMIENTO WHERE TEMPORADA_ID = ? AND ACTIVO = 1", origenId),
				contar("SELECT COUNT(*) FROM SESIONES_ENTRENAMIENTO S WHERE " + SESION_SIN_ENTRENAMIENTO,
						Date.valueOf(inicioDestino)),
				contar("SELECT COUNT(*) FROM CONCEPTOS_PAGO C WHERE C.TEMPORADA_ID = ? AND C.ACTIVO = 1"
						+ " AND NOT EXISTS (SELECT 1 FROM CONCEPTOS_PAGO D WHERE D.TEMPORADA_ID = ? AND D.NOMBRE = C.NOMBRE)",
						origenId, destinoId),
				contar("SELECT COUNT(*) FROM NOTIFICACIONES_APP"),
				contar("SELECT COUNT(*) FROM USUARIOS_APP WHERE ACTIVO = 1"));
	}

	@Override
	public int fotografiarCuerpoTecnico(Long origenId) {
		jdbcTemplate.update("DELETE FROM CUERPO_TECNICO_TEMPORADA WHERE TEMPORADA_ID = ?", origenId);

		return jdbcTemplate.update("""
				INSERT INTO CUERPO_TECNICO_TEMPORADA (CUERPO_TECNICO_ID, TEMPORADA_ID, EQUIPO_ID, PUESTO)
				SELECT CTE.CUERPO_TECNICO_ID, ?, CTE.EQUIPO_ID, COALESCE(CTE.PUESTO, CT.PUESTO)
				FROM CUERPO_TECNICO_EQUIPO CTE
				JOIN CUERPO_TECNICO CT ON CT.ID = CTE.CUERPO_TECNICO_ID
				""", origenId);
	}

	@Override
	public int copiarEquipos(Long origenId, Long destinoId) {
		int creadas = jdbcTemplate.update("""
				INSERT INTO EQUIPO_TEMPORADA (EQUIPO_ID, TEMPORADA_ID, ACTIVO, MODALIDAD, TIPO)
				SELECT E.ID, ?, CASE WHEN %s THEN 1 ELSE 0 END, COALESCE(ET.MODALIDAD, E.MODALIDAD),
				       COALESCE(ET.TIPO, 'NORMAL')
				FROM EQUIPO E
				LEFT JOIN EQUIPO_TEMPORADA ET ON ET.EQUIPO_ID = E.ID AND ET.TEMPORADA_ID = ?
				WHERE NOT EXISTS (SELECT 1 FROM EQUIPO_TEMPORADA D WHERE D.EQUIPO_ID = E.ID AND D.TEMPORADA_ID = ?)
				""".formatted(ADULTO), destinoId, origenId, destinoId);

		// Los equipos de adultos que ya tenían fila en la temporada nueva (creada a mano) también juegan
		jdbcTemplate.update("""
				UPDATE EQUIPO_TEMPORADA SET ACTIVO = 1
				WHERE TEMPORADA_ID = ? AND EQUIPO_ID IN (SELECT E.ID FROM EQUIPO E WHERE %s)
				""".formatted(ADULTO), destinoId);

		return creadas;
	}

	@Override
	public Long obtenerOCrearEquipo(String nombre, String categoria, String grupo, String orden, String deporte,
			String modalidad) {
		List<Long> existentes = jdbcTemplate.queryForList(
				"SELECT ID FROM EQUIPO WHERE UPPER(TRIM(NOMBRE)) = UPPER(TRIM(?))", Long.class, nombre);

		if (!existentes.isEmpty()) {
			return existentes.get(0);
		}

		jdbcTemplate.update("INSERT INTO EQUIPO (CATEGORIA, GRUPO, ORDEN, NOMBRE, DEPORTE, MODALIDAD)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", categoria, grupo, orden, nombre, deporte, modalidad);

		return jdbcTemplate.queryForObject("SELECT ID FROM EQUIPO WHERE UPPER(TRIM(NOMBRE)) = UPPER(TRIM(?))", Long.class,
				nombre);
	}

	@Override
	public void activarPretemporada(Long equipoId, Long destinoId, String modalidad) {
		int filas = jdbcTemplate.update("UPDATE EQUIPO_TEMPORADA SET ACTIVO = 1, TIPO = 'PRETEMPORADA', MODALIDAD = ?"
				+ " WHERE EQUIPO_ID = ? AND TEMPORADA_ID = ?", modalidad, equipoId, destinoId);

		if (filas == 0) {
			jdbcTemplate.update("INSERT INTO EQUIPO_TEMPORADA (EQUIPO_ID, TEMPORADA_ID, ACTIVO, MODALIDAD, TIPO)"
					+ " VALUES (?, ?, 1, ?, 'PRETEMPORADA')", equipoId, destinoId, modalidad);
		}
	}

	@Override
	public int desasignarCuerpoTecnico() {
		return jdbcTemplate.update("DELETE FROM CUERPO_TECNICO_EQUIPO WHERE EQUIPO_ID IN"
				+ " (SELECT E.ID FROM EQUIPO E WHERE " + FORMATIVO + ")");
	}

	@Override
	public int copiarHorariosActivos(Long origenId, Long destinoId) {
		return jdbcTemplate.update("""
				INSERT INTO HORARIOS_ENTRENAMIENTO (EQUIPO_ID, DIA_SEMANA, HORA, LUGAR, ACTIVO, FECHA_ACTUALIZACION,
				       TEMPORADA_ID)
				SELECT EQUIPO_ID, DIA_SEMANA, HORA, LUGAR, 0, ?, ?
				FROM HORARIOS_ENTRENAMIENTO
				WHERE TEMPORADA_ID = ? AND ACTIVO = 1
				""", Timestamp.valueOf(LocalDateTime.now()), destinoId, origenId);
	}

	@Override
	public int desactivarHorarios(Long origenId) {
		return jdbcTemplate.update("UPDATE HORARIOS_ENTRENAMIENTO SET ACTIVO = 0 WHERE TEMPORADA_ID = ? AND ACTIVO = 1",
				origenId);
	}

	@Override
	public int eliminarSesionesSinEntrenamiento(LocalDate desde) {
		Date fecha = Date.valueOf(desde);

		// La FK de las justificaciones no tiene ON DELETE CASCADE: van antes que sus sesiones.
		jdbcTemplate.update("DELETE FROM JUSTIFICACIONES_FALTA_ENTRENAMIENTO WHERE SESION_ID IN"
				+ " (SELECT S.ID FROM SESIONES_ENTRENAMIENTO S WHERE " + SESION_SIN_ENTRENAMIENTO + ")", fecha);

		return jdbcTemplate.update("DELETE FROM SESIONES_ENTRENAMIENTO S WHERE " + SESION_SIN_ENTRENAMIENTO, fecha);
	}

	@Override
	public int copiarConceptosActivos(Long origenId, Long destinoId) {
		return jdbcTemplate.update("""
				INSERT INTO CONCEPTOS_PAGO (TEMPORADA_ID, NOMBRE, DESCRIPCION, IMPORTE, ACTIVO)
				SELECT ?, C.NOMBRE, C.DESCRIPCION, C.IMPORTE, 1
				FROM CONCEPTOS_PAGO C
				WHERE C.TEMPORADA_ID = ? AND C.ACTIVO = 1
				  AND NOT EXISTS (SELECT 1 FROM CONCEPTOS_PAGO D WHERE D.TEMPORADA_ID = ? AND D.NOMBRE = C.NOMBRE)
				""", destinoId, origenId, destinoId);
	}

	// Ninguna tabla referencia NOTIFICACIONES_APP en el código ni en sql/ (REFERENCIA_ID es un ID suelto, sin FK):
	// no hay nada que borrar antes. Si en la base de datos hubiera una FK, este DELETE fallaría con ORA-02292 y el
	// cierre se deshace entero. Comprobación (devuelve 0 filas si no hay ninguna):
	// SELECT C.TABLE_NAME, C.CONSTRAINT_NAME FROM USER_CONSTRAINTS C JOIN USER_CONSTRAINTS R
	// ON R.CONSTRAINT_NAME = C.R_CONSTRAINT_NAME WHERE C.CONSTRAINT_TYPE = 'R' AND R.TABLE_NAME = 'NOTIFICACIONES_APP'
	@Override
	public int eliminarNotificaciones() {
		return jdbcTemplate.update("DELETE FROM NOTIFICACIONES_APP");
	}
}
