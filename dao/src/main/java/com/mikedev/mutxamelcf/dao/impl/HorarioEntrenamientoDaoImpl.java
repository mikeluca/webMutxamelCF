package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.HorarioEntrenamientoDao;
import com.mikedev.mutxamelcf.model.HorarioEntrenamiento;

@Repository
public class HorarioEntrenamientoDaoImpl implements HorarioEntrenamientoDao {

    private static final RowMapper<HorarioEntrenamiento> ROW_MAPPER = HorarioEntrenamientoDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public HorarioEntrenamientoDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HorarioEntrenamiento crear(HorarioEntrenamiento horario) {

        String sql = """
                INSERT INTO HORARIOS_ENTRENAMIENTO (
                    EQUIPO_ID,
                    DIA_SEMANA,
                    HORA,
                    LUGAR,
                    ACTIVO,
                    USUARIO_ACTUALIZO_ID,
                    FECHA_ACTUALIZACION
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });

            ps.setLong(1, horario.getEquipoId());
            ps.setInt(2, horario.getDiaSemana());
            ps.setString(3, horario.getHora());
            ps.setString(4, horario.getLugar());
            ps.setInt(5, horario.isActivo() ? 1 : 0);

            if (horario.getUsuarioActualizoId() != null) {
                ps.setLong(6, horario.getUsuarioActualizoId());
            } else {
                ps.setNull(6, Types.NUMERIC);
            }

            ps.setTimestamp(7, horario.getFechaActualizacion());

            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID generado del horario de entrenamiento");
        }

        horario.setId(key.longValue());

        return horario;
    }

    @Override
    public void actualizar(HorarioEntrenamiento horario) {

        String sql = """
                UPDATE HORARIOS_ENTRENAMIENTO
                SET DIA_SEMANA = ?,
                    HORA = ?,
                    LUGAR = ?,
                    ACTIVO = ?,
                    USUARIO_ACTUALIZO_ID = ?,
                    FECHA_ACTUALIZACION = ?
                WHERE ID = ?
                """;

        jdbcTemplate.update(
                sql,
                horario.getDiaSemana(),
                horario.getHora(),
                horario.getLugar(),
                horario.isActivo() ? 1 : 0,
                horario.getUsuarioActualizoId(),
                horario.getFechaActualizacion(),
                horario.getId());
    }

    @Override
    public HorarioEntrenamiento obtenerPorId(Long id) {

        String sql = """
                SELECT ID, EQUIPO_ID, DIA_SEMANA, HORA, LUGAR, ACTIVO,
                       USUARIO_ACTUALIZO_ID, FECHA_ACTUALIZACION
                FROM HORARIOS_ENTRENAMIENTO
                WHERE ID = ?
                """;

        List<HorarioEntrenamiento> resultado = jdbcTemplate.query(sql, ROW_MAPPER, id);

        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public List<HorarioEntrenamiento> obtenerActivosPorEquipo(Long equipoId) {

        String sql = """
                SELECT ID, EQUIPO_ID, DIA_SEMANA, HORA, LUGAR, ACTIVO,
                       USUARIO_ACTUALIZO_ID, FECHA_ACTUALIZACION
                FROM HORARIOS_ENTRENAMIENTO
                WHERE EQUIPO_ID = ?
                  AND ACTIVO = 1
                ORDER BY DIA_SEMANA ASC, HORA ASC
                """;

        return jdbcTemplate.query(sql, ROW_MAPPER, equipoId);
    }

    @Override
    public List<HorarioEntrenamiento> obtenerTodosActivos() {

        String sql = """
                SELECT ID, EQUIPO_ID, DIA_SEMANA, HORA, LUGAR, ACTIVO,
                       USUARIO_ACTUALIZO_ID, FECHA_ACTUALIZACION
                FROM HORARIOS_ENTRENAMIENTO
                WHERE ACTIVO = 1
                ORDER BY EQUIPO_ID ASC, DIA_SEMANA ASC
                """;

        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    private static HorarioEntrenamiento mapRow(ResultSet rs, int rowNum) throws SQLException {

        HorarioEntrenamiento horario = new HorarioEntrenamiento();

        horario.setId(rs.getLong("ID"));
        horario.setEquipoId(rs.getLong("EQUIPO_ID"));
        horario.setDiaSemana(rs.getInt("DIA_SEMANA"));
        horario.setHora(rs.getString("HORA"));
        horario.setLugar(rs.getString("LUGAR"));
        horario.setActivo(rs.getInt("ACTIVO") == 1);

        long usuarioActualizoId = rs.getLong("USUARIO_ACTUALIZO_ID");
        horario.setUsuarioActualizoId(rs.wasNull() ? null : usuarioActualizoId);

        horario.setFechaActualizacion(rs.getTimestamp("FECHA_ACTUALIZACION"));

        return horario;
    }

}
