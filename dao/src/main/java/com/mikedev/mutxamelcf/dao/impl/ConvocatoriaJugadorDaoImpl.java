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

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;

@Repository
public class ConvocatoriaJugadorDaoImpl
        implements ConvocatoriaJugadorDao {

    private static final String COLUMNAS = """
            ID,
            CONVOCATORIA_ID,
            JUGADOR_ID,
            EQUIPO_ORIGEN_ID,
            ESTADO_INVITACION,
            FECHA_SOLICITUD,
            USUARIO_INVITA_ID,
            USUARIO_COORDINADOR_ID,
            FECHA_COORDINACION,
            FECHA_PETICION_ENTRENADOR,
            AVISO_12H_ENVIADO,
            FECHA_AUTORIZACION,
            MOTIVO_RECHAZO
            """;

    private final JdbcTemplate jdbcTemplate;

    public ConvocatoriaJugadorDaoImpl(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ConvocatoriaJugador> rowMapper = (rs, rowNum) -> {

        ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();

        convocatoriaJugador.setId(
                rs.getLong("ID"));

        convocatoriaJugador.setConvocatoriaId(
                rs.getLong("CONVOCATORIA_ID"));

        convocatoriaJugador.setJugadorId(
                rs.getLong("JUGADOR_ID"));

        convocatoriaJugador.setEquipoOrigenId(
                largoONulo(rs, "EQUIPO_ORIGEN_ID"));

        String estado = rs.getString("ESTADO_INVITACION");

        convocatoriaJugador.setEstadoInvitacion(
                estado != null ? EstadoInvitacion.valueOf(estado) : null);

        convocatoriaJugador.setFechaSolicitud(
                fechaONula(rs, "FECHA_SOLICITUD"));

        convocatoriaJugador.setUsuarioInvitaId(
                largoONulo(rs, "USUARIO_INVITA_ID"));

        convocatoriaJugador.setUsuarioCoordinadorId(
                largoONulo(rs, "USUARIO_COORDINADOR_ID"));

        convocatoriaJugador.setFechaCoordinacion(
                fechaONula(rs, "FECHA_COORDINACION"));

        convocatoriaJugador.setFechaPeticionEntrenador(
                fechaONula(rs, "FECHA_PETICION_ENTRENADOR"));

        convocatoriaJugador.setAviso12hEnviado(
                rs.getInt("AVISO_12H_ENVIADO") == 1);

        convocatoriaJugador.setFechaAutorizacion(
                fechaONula(rs, "FECHA_AUTORIZACION"));

        convocatoriaJugador.setMotivoRechazo(
                rs.getString("MOTIVO_RECHAZO"));

        return convocatoriaJugador;
    };

    private static Long largoONulo(ResultSet rs, String columna) throws SQLException {

        long valor = rs.getLong(columna);

        return rs.wasNull() ? null : valor;
    }

    private static LocalDateTime fechaONula(ResultSet rs, String columna) throws SQLException {

        Timestamp fecha = rs.getTimestamp(columna);

        return fecha != null ? fecha.toLocalDateTime() : null;
    }

    @Override
    public ConvocatoriaJugador guardar(
            ConvocatoriaJugador convocatoriaJugador) {

        String sql = """
                INSERT INTO CONVOCATORIA_JUGADOR (
                    CONVOCATORIA_ID,
                    JUGADOR_ID,
                    EQUIPO_ORIGEN_ID,
                    ESTADO_INVITACION,
                    FECHA_SOLICITUD,
                    USUARIO_INVITA_ID,
                    USUARIO_COORDINADOR_ID,
                    FECHA_COORDINACION,
                    FECHA_PETICION_ENTRENADOR,
                    AVISO_12H_ENVIADO,
                    FECHA_AUTORIZACION,
                    MOTIVO_RECHAZO
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {

            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    new String[] { "ID" });

            ps.setLong(
                    1,
                    convocatoriaJugador.getConvocatoriaId());

            ps.setLong(
                    2,
                    convocatoriaJugador.getJugadorId());

            asignarInvitacion(ps, 3, convocatoriaJugador);

            return ps;

        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "No se pudo obtener el ID generado de la convocatoria-jugador");
        }

        convocatoriaJugador.setId(key.longValue());

        return convocatoriaJugador;
    }

    /* Los 10 campos de la invitación, desde la posición indicada. */
    private static void asignarInvitacion(
            PreparedStatement ps,
            int desde,
            ConvocatoriaJugador cj) throws SQLException {

        asignarLargo(ps, desde, cj.getEquipoOrigenId());

        if (cj.getEstadoInvitacion() != null) {
            ps.setString(desde + 1, cj.getEstadoInvitacion().name());
        } else {
            ps.setNull(desde + 1, Types.VARCHAR);
        }

        asignarFecha(ps, desde + 2, cj.getFechaSolicitud());
        asignarLargo(ps, desde + 3, cj.getUsuarioInvitaId());
        asignarLargo(ps, desde + 4, cj.getUsuarioCoordinadorId());
        asignarFecha(ps, desde + 5, cj.getFechaCoordinacion());
        asignarFecha(ps, desde + 6, cj.getFechaPeticionEntrenador());
        ps.setInt(desde + 7, cj.isAviso12hEnviado() ? 1 : 0);
        asignarFecha(ps, desde + 8, cj.getFechaAutorizacion());

        if (cj.getMotivoRechazo() != null) {
            ps.setString(desde + 9, cj.getMotivoRechazo());
        } else {
            ps.setNull(desde + 9, Types.VARCHAR);
        }
    }

    private static void asignarLargo(PreparedStatement ps, int posicion, Long valor) throws SQLException {

        if (valor != null) {
            ps.setLong(posicion, valor);
        } else {
            ps.setNull(posicion, Types.NUMERIC);
        }
    }

    private static void asignarFecha(PreparedStatement ps, int posicion, LocalDateTime valor) throws SQLException {

        if (valor != null) {
            ps.setTimestamp(posicion, Timestamp.valueOf(valor));
        } else {
            ps.setNull(posicion, Types.TIMESTAMP);
        }
    }

    @Override
    public List<ConvocatoriaJugador> obtenerPorConvocatoria(
            Long convocatoriaId) {

        String sql = "SELECT " + COLUMNAS + """
                FROM CONVOCATORIA_JUGADOR
                WHERE CONVOCATORIA_ID = ?
                  AND (EQUIPO_ORIGEN_ID IS NULL OR ESTADO_INVITACION = 'AUTORIZADO')
                ORDER BY JUGADOR_ID
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                convocatoriaId);
    }

    @Override
    public List<ConvocatoriaJugador> obtenerTodasPorConvocatoria(
            Long convocatoriaId) {

        String sql = "SELECT " + COLUMNAS + """
                FROM CONVOCATORIA_JUGADOR
                WHERE CONVOCATORIA_ID = ?
                ORDER BY JUGADOR_ID
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                convocatoriaId);
    }

    @Override
    public ConvocatoriaJugador obtenerPorId(
            Long id) {

        String sql = "SELECT " + COLUMNAS + """
                FROM CONVOCATORIA_JUGADOR
                WHERE ID = ?
                """;

        List<ConvocatoriaJugador> resultado = jdbcTemplate.query(
                sql,
                rowMapper,
                id);

        return resultado.isEmpty()
                ? null
                : resultado.get(0);
    }

    @Override
    public ConvocatoriaJugador obtenerPorConvocatoriaYJugador(
            Long convocatoriaId,
            Long jugadorId) {

        String sql = "SELECT " + COLUMNAS + """
                FROM CONVOCATORIA_JUGADOR
                WHERE CONVOCATORIA_ID = ?
                  AND JUGADOR_ID = ?
                """;

        List<ConvocatoriaJugador> resultado = jdbcTemplate.query(
                sql,
                rowMapper,
                convocatoriaId,
                jugadorId);

        return resultado.isEmpty()
                ? null
                : resultado.get(0);
    }

    @Override
    public void actualizarInvitacion(
            ConvocatoriaJugador cj) {

        jdbcTemplate.update(connection -> {

            PreparedStatement ps = connection.prepareStatement("""
                    UPDATE CONVOCATORIA_JUGADOR
                    SET EQUIPO_ORIGEN_ID = ?,
                        ESTADO_INVITACION = ?,
                        FECHA_SOLICITUD = ?,
                        USUARIO_INVITA_ID = ?,
                        USUARIO_COORDINADOR_ID = ?,
                        FECHA_COORDINACION = ?,
                        FECHA_PETICION_ENTRENADOR = ?,
                        AVISO_12H_ENVIADO = ?,
                        FECHA_AUTORIZACION = ?,
                        MOTIVO_RECHAZO = ?
                    WHERE ID = ?
                    """);

            asignarInvitacion(ps, 1, cj);

            ps.setLong(11, cj.getId());

            return ps;
        });
    }

    @Override
    public void eliminarPorConvocatoria(
            Long convocatoriaId) {

        jdbcTemplate.update(
                """
                        DELETE FROM CONVOCATORIA_JUGADOR
                        WHERE CONVOCATORIA_ID = ?
                        """,
                convocatoriaId);
    }

    @Override
    public int eliminarPorConvocatoriaYJugador(
            Long convocatoriaId,
            Long jugadorId) {

        return jdbcTemplate.update(
                """
                        DELETE FROM CONVOCATORIA_JUGADOR
                        WHERE CONVOCATORIA_ID = ?
                          AND JUGADOR_ID = ?
                        """,
                convocatoriaId,
                jugadorId);
    }

    @Override
    public boolean existePorJugador(
            Long jugadorId) {

        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM CONVOCATORIA_JUGADOR
                        WHERE JUGADOR_ID = ?
                        """,
                Integer.class,
                jugadorId);

        return count != null && count > 0;
    }
}
