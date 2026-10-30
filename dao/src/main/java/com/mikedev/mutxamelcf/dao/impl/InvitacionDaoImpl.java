package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.InvitacionDao;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;
import com.mikedev.mutxamelcf.model.InvitacionDetalle;
import com.mikedev.mutxamelcf.model.JugadorInvitable;

@Repository
public class InvitacionDaoImpl implements InvitacionDao {

    /*
     * Una invitación con su jugador, su equipo de origen y el partido de destino. El usuario que invitó es el de la
     * fila o, en las filas que no lo guardan, el de la convocatoria.
     */
    private static final String DETALLE = """
            SELECT CJ.ID, CJ.CONVOCATORIA_ID, CJ.JUGADOR_ID, J.NOMBRE, J.APELLIDOS,
                   CJ.EQUIPO_ORIGEN_ID, EO.NOMBRE AS EQUIPO_ORIGEN, CJ.ESTADO_INVITACION,
                   C.EQUIPO_ID AS EQUIPO_DESTINO_ID, ED.NOMBRE AS EQUIPO_DESTINO,
                   C.PARTIDO_ID, P.DIA, P.HORA, P.RIVAL, P.CANCELADO,
                   COALESCE(CJ.USUARIO_INVITA_ID, C.USUARIO_ENTRENADOR_ID) AS USUARIO_INVITA_ID,
                   CJ.FECHA_SOLICITUD, CJ.FECHA_PETICION_ENTRENADOR, CJ.AVISO_12H_ENVIADO, CJ.MOTIVO_RECHAZO
            FROM CONVOCATORIA_JUGADOR CJ
            JOIN JUGADORES J ON J.ID = CJ.JUGADOR_ID
            JOIN CONVOCATORIAS C ON C.ID = CJ.CONVOCATORIA_ID
            JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
            JOIN EQUIPO EO ON EO.ID = CJ.EQUIPO_ORIGEN_ID
            JOIN EQUIPO ED ON ED.ID = C.EQUIPO_ID
            WHERE CJ.EQUIPO_ORIGEN_ID IS NOT NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public InvitacionDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<InvitacionDetalle> DETALLE_ROW_MAPPER = (rs, n) -> {

        Date dia = rs.getDate("DIA");
        Timestamp solicitud = rs.getTimestamp("FECHA_SOLICITUD");
        Timestamp peticion = rs.getTimestamp("FECHA_PETICION_ENTRENADOR");

        return new InvitacionDetalle(
                rs.getLong("ID"),
                rs.getLong("CONVOCATORIA_ID"),
                rs.getLong("JUGADOR_ID"),
                nombreCompleto(rs),
                rs.getLong("EQUIPO_ORIGEN_ID"),
                rs.getString("EQUIPO_ORIGEN"),
                EstadoInvitacion.valueOf(rs.getString("ESTADO_INVITACION")),
                rs.getLong("EQUIPO_DESTINO_ID"),
                rs.getString("EQUIPO_DESTINO"),
                rs.getLong("PARTIDO_ID"),
                dia != null ? dia.toLocalDate() : null,
                rs.getString("HORA"),
                rs.getString("RIVAL"),
                rs.getInt("CANCELADO") == 1,
                largoONulo(rs, "USUARIO_INVITA_ID"),
                solicitud != null ? solicitud.toLocalDateTime() : null,
                peticion != null ? peticion.toLocalDateTime() : null,
                rs.getInt("AVISO_12H_ENVIADO") == 1,
                rs.getString("MOTIVO_RECHAZO"));
    };

    private static String nombreCompleto(ResultSet rs) throws SQLException {

        String nombre = rs.getString("NOMBRE") == null ? "" : rs.getString("NOMBRE").trim();
        String apellidos = rs.getString("APELLIDOS") == null ? "" : rs.getString("APELLIDOS").trim();

        return (nombre + " " + apellidos).trim();
    }

    private static Long largoONulo(ResultSet rs, String columna) throws SQLException {

        long valor = rs.getLong(columna);

        return rs.wasNull() ? null : valor;
    }

    private static Date dia(LocalDate fecha) {
        return Date.valueOf(fecha);
    }

    @Override
    public InvitacionDetalle obtenerDetalle(Long convocatoriaJugadorId) {

        List<InvitacionDetalle> resultado = jdbcTemplate.query(
                DETALLE + " AND CJ.ID = ?",
                DETALLE_ROW_MAPPER,
                convocatoriaJugadorId);

        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public List<InvitacionDetalle> porConvocatoria(Long convocatoriaId) {

        return jdbcTemplate.query(
                DETALLE + " AND CJ.CONVOCATORIA_ID = ? ORDER BY CJ.ID",
                DETALLE_ROW_MAPPER,
                convocatoriaId);
    }

    @Override
    public List<InvitacionDetalle> pendientes(LocalDate desde) {

        return jdbcTemplate.query(
                DETALLE + """
                          AND CJ.ESTADO_INVITACION IN ('PENDIENTE_COORDINACION', 'PENDIENTE_ENTRENADOR')
                          AND P.CANCELADO = 0
                          AND P.DIA >= ?
                        ORDER BY P.DIA, P.HORA, CJ.FECHA_SOLICITUD, CJ.ID
                        """,
                DETALLE_ROW_MAPPER,
                dia(desde));
    }

    @Override
    public List<InvitacionDetalle> delJugadorEnDia(Long jugadorId, LocalDate diaPartido, Long excluirId) {

        return jdbcTemplate.query(
                DETALLE + """
                          AND CJ.JUGADOR_ID = ?
                          AND CJ.ESTADO_INVITACION <> 'RECHAZADO'
                          AND CJ.ID <> ?
                          AND P.CANCELADO = 0
                          AND P.DIA >= ? AND P.DIA < ?
                        ORDER BY CJ.ID
                        """,
                DETALLE_ROW_MAPPER,
                jugadorId,
                excluirId != null ? excluirId : -1L,
                dia(diaPartido),
                dia(diaPartido.plusDays(1)));
    }

    @Override
    public List<InvitacionDetalle> autorizadasDelJugador(Long jugadorId, LocalDate desde, LocalDate hasta) {

        return jdbcTemplate.query(
                DETALLE + """
                          AND CJ.JUGADOR_ID = ?
                          AND CJ.ESTADO_INVITACION = 'AUTORIZADO'
                          AND P.CANCELADO = 0
                          AND P.DIA BETWEEN ? AND ?
                        ORDER BY P.DIA, P.HORA, CJ.ID
                        """,
                DETALLE_ROW_MAPPER,
                jugadorId,
                dia(desde),
                dia(hasta));
    }

    @Override
    public List<InvitacionDetalle> vencidasSinAviso(LocalDateTime limite) {

        return jdbcTemplate.query(
                DETALLE + """
                          AND CJ.ESTADO_INVITACION = 'PENDIENTE_ENTRENADOR'
                          AND CJ.AVISO_12H_ENVIADO = 0
                          AND CJ.FECHA_PETICION_ENTRENADOR <= ?
                          AND P.CANCELADO = 0
                        ORDER BY CJ.FECHA_PETICION_ENTRENADOR, CJ.ID
                        """,
                DETALLE_ROW_MAPPER,
                Timestamp.valueOf(limite));
    }

    @Override
    public List<PartidoDelDia> partidosDelEquipoEnDia(Long equipoId, LocalDate diaPartido) {

        return jdbcTemplate.query("""
                SELECT P.ID, P.HORA, P.RIVAL
                FROM PARTIDOS P
                WHERE P.EQUIPO_ID = ?
                  AND P.CANCELADO = 0
                  AND P.DIA >= ? AND P.DIA < ?
                ORDER BY P.HORA, P.ID
                """,
                (rs, n) -> new PartidoDelDia(rs.getLong("ID"), rs.getString("HORA"), rs.getString("RIVAL")),
                equipoId,
                dia(diaPartido),
                dia(diaPartido.plusDays(1)));
    }

    @Override
    public List<ConvocatoriaJugador> filasPropiasDelJugadorEnDia(Long equipoId, Long jugadorId, LocalDate diaPartido) {

        List<ConvocatoriaJugador> filas = new ArrayList<>();

        jdbcTemplate.query("""
                SELECT CJ.ID, CJ.CONVOCATORIA_ID, CJ.JUGADOR_ID
                FROM CONVOCATORIA_JUGADOR CJ
                JOIN CONVOCATORIAS C ON C.ID = CJ.CONVOCATORIA_ID
                JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
                WHERE CJ.JUGADOR_ID = ?
                  AND CJ.EQUIPO_ORIGEN_ID IS NULL
                  AND C.EQUIPO_ID = ?
                  AND P.CANCELADO = 0
                  AND P.DIA >= ? AND P.DIA < ?
                ORDER BY CJ.ID
                """,
                rs -> {
                    filas.add(new ConvocatoriaJugador(rs.getLong("ID"), rs.getLong("CONVOCATORIA_ID"),
                            rs.getLong("JUGADOR_ID")));
                },
                jugadorId,
                equipoId,
                dia(diaPartido),
                dia(diaPartido.plusDays(1)));

        return filas;
    }

    @Override
    public List<JugadorInvitable> jugadoresInvitables(Long equipoId, Long temporadaId) {

        return jdbcTemplate.query("""
                SELECT J.ID, J.NOMBRE, J.APELLIDOS, COALESCE(JT.DORSAL, J.DORSAL) AS DORSAL
                FROM JUGADORES J
                JOIN JUGADOR_TEMPORADA JT ON JT.JUGADOR_ID = J.ID AND JT.TEMPORADA_ID = ?
                WHERE JT.EQUIPO_ID = ?
                  AND JT.ESTADO = 'CONFIRMADO'
                  AND JT.EQUIPO_DEFINITIVO = 1
                  AND J.FECHA_BAJA IS NULL
                ORDER BY COALESCE(JT.DORSAL, J.DORSAL) NULLS LAST, J.APELLIDOS, J.NOMBRE, J.ID
                """,
                (rs, n) -> {
                    long id = rs.getLong("ID");
                    String nombre = rs.getString("NOMBRE");
                    String apellidos = rs.getString("APELLIDOS");
                    int dorsal = rs.getInt("DORSAL");
                    boolean sinDorsal = rs.wasNull();

                    return new JugadorInvitable(id, nombre, apellidos, sinDorsal ? null : dorsal);
                },
                temporadaId,
                equipoId);
    }
}
