package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.SesionEntrenamientoDao;
import com.mikedev.mutxamelcf.model.SesionEntrenamiento;

@Repository
public class SesionEntrenamientoDaoImpl implements SesionEntrenamientoDao {

    private static final RowMapper<SesionEntrenamiento> ROW_MAPPER = SesionEntrenamientoDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public SesionEntrenamientoDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SesionEntrenamiento crear(SesionEntrenamiento sesion) {

        String sql = """
                INSERT INTO SESIONES_ENTRENAMIENTO (
                    EQUIPO_ID,
                    HORARIO_ID,
                    FECHA,
                    HORA,
                    LUGAR,
                    ESTADO
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });

            ps.setLong(1, sesion.getEquipoId());

            if (sesion.getHorarioId() != null) {
                ps.setLong(2, sesion.getHorarioId());
            } else {
                ps.setNull(2, Types.NUMERIC);
            }

            ps.setDate(3, Date.valueOf(sesion.getFecha()));
            ps.setString(4, sesion.getHora());
            ps.setString(5, sesion.getLugar());
            ps.setString(6, sesion.getEstado() != null ? sesion.getEstado() : SesionEntrenamiento.ESTADO_PROGRAMADA);

            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID generado de la sesión de entrenamiento");
        }

        sesion.setId(key.longValue());

        return sesion;
    }

    @Override
    public void actualizar(SesionEntrenamiento sesion) {

        String sql = """
                UPDATE SESIONES_ENTRENAMIENTO
                SET HORA = ?,
                    LUGAR = ?
                WHERE ID = ?
                """;

        jdbcTemplate.update(sql, sesion.getHora(), sesion.getLugar(), sesion.getId());
    }

    @Override
    public void cancelar(Long id, String motivo) {

        jdbcTemplate.update(
                "UPDATE SESIONES_ENTRENAMIENTO SET ESTADO = ?, MOTIVO_CANCELACION = ? WHERE ID = ?",
                SesionEntrenamiento.ESTADO_CANCELADA,
                motivo,
                id);
    }

    @Override
    public SesionEntrenamiento obtenerPorId(Long id) {

        String sql = """
                SELECT ID, EQUIPO_ID, HORARIO_ID, FECHA, HORA, LUGAR, ESTADO, MOTIVO_CANCELACION, FECHA_CREACION
                FROM SESIONES_ENTRENAMIENTO
                WHERE ID = ?
                """;

        List<SesionEntrenamiento> resultado = jdbcTemplate.query(sql, ROW_MAPPER, id);

        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public List<SesionEntrenamiento> obtenerPorEquipoYRango(Long equipoId, LocalDate desde, LocalDate hasta) {

        String sql = """
                SELECT ID, EQUIPO_ID, HORARIO_ID, FECHA, HORA, LUGAR, ESTADO, MOTIVO_CANCELACION, FECHA_CREACION
                FROM SESIONES_ENTRENAMIENTO
                WHERE EQUIPO_ID = ?
                  AND FECHA BETWEEN ? AND ?
                ORDER BY FECHA ASC, HORA ASC
                """;

        return jdbcTemplate.query(sql, ROW_MAPPER, equipoId, Date.valueOf(desde), Date.valueOf(hasta));
    }

    @Override
    public boolean existePorHorarioYFecha(Long horarioId, LocalDate fecha) {

        String sql = """
                SELECT COUNT(*)
                FROM SESIONES_ENTRENAMIENTO
                WHERE HORARIO_ID = ?
                  AND FECHA = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, horarioId, Date.valueOf(fecha));

        return count != null && count > 0;
    }

    @Override
    public LocalDate obtenerUltimaFechaGenerada(Long horarioId) {

        String sql = """
                SELECT MAX(FECHA)
                FROM SESIONES_ENTRENAMIENTO
                WHERE HORARIO_ID = ?
                """;

        Date ultima = jdbcTemplate.queryForObject(sql, Date.class, horarioId);

        return ultima != null ? ultima.toLocalDate() : null;
    }

    @Override
    public void cancelarFuturasProgramadasPorHorario(Long horarioId, LocalDate desde) {

        String sql = """
                UPDATE SESIONES_ENTRENAMIENTO
                SET ESTADO = ?
                WHERE HORARIO_ID = ?
                  AND FECHA >= ?
                  AND ESTADO = ?
                """;

        jdbcTemplate.update(
                sql,
                SesionEntrenamiento.ESTADO_CANCELADA,
                horarioId,
                Date.valueOf(desde),
                SesionEntrenamiento.ESTADO_PROGRAMADA);
    }

    private static SesionEntrenamiento mapRow(ResultSet rs, int rowNum) throws SQLException {

        SesionEntrenamiento sesion = new SesionEntrenamiento();

        sesion.setId(rs.getLong("ID"));
        sesion.setEquipoId(rs.getLong("EQUIPO_ID"));

        long horarioId = rs.getLong("HORARIO_ID");
        sesion.setHorarioId(rs.wasNull() ? null : horarioId);

        Date fecha = rs.getDate("FECHA");
        sesion.setFecha(fecha != null ? fecha.toLocalDate() : null);

        sesion.setHora(rs.getString("HORA"));
        sesion.setLugar(rs.getString("LUGAR"));
        sesion.setEstado(rs.getString("ESTADO"));
        sesion.setMotivoCancelacion(rs.getString("MOTIVO_CANCELACION"));

        Timestamp fechaCreacion = rs.getTimestamp("FECHA_CREACION");
        sesion.setFechaCreacion(fechaCreacion != null ? fechaCreacion.toLocalDateTime() : null);

        return sesion;
    }

}
