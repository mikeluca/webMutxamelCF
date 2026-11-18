package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.SolicitudFamiliaDao;
import com.mikedev.mutxamelcf.model.SolicitudFamilia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaJugador;

@Repository
public class SolicitudFamiliaDaoImpl implements SolicitudFamiliaDao {

    private static final String COLUMNAS = """
            ID, USUARIO_APP_ID, NOMBRE, APELLIDOS, EMAIL, TELEFONO, PARENTESCO, ESTADO, MOTIVO_RECHAZO,
            FECHA_SOLICITUD, FECHA_REVISION, REVISADA_POR_USUARIO_APP_ID, REVISADA_POR_PANEL""";

    private static final RowMapper<SolicitudFamilia> SOLICITUD = SolicitudFamiliaDaoImpl::solicitud;

    private static final RowMapper<SolicitudFamiliaJugador> HIJO = (rs, n) -> new SolicitudFamiliaJugador(
            rs.getLong("ID"), rs.getLong("SOLICITUD_ID"), rs.getString("NOMBRE_JUGADOR"),
            rs.getString("APELLIDOS_JUGADOR"), rs.getString("EQUIPO_TEXTO"), largoONulo(rs, "JUGADOR_ID"),
            rs.getString("ESTADO"));

    private final JdbcTemplate jdbcTemplate;

    public SolicitudFamiliaDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static SolicitudFamilia solicitud(ResultSet rs, int n) throws SQLException {

        Timestamp solicitud = rs.getTimestamp("FECHA_SOLICITUD");
        Timestamp revision = rs.getTimestamp("FECHA_REVISION");

        return new SolicitudFamilia(rs.getLong("ID"), rs.getLong("USUARIO_APP_ID"),
                rs.getString("NOMBRE"), rs.getString("APELLIDOS"), rs.getString("EMAIL"), rs.getString("TELEFONO"),
                rs.getString("PARENTESCO"), rs.getString("ESTADO"), rs.getString("MOTIVO_RECHAZO"),
                solicitud == null ? null : solicitud.toLocalDateTime(),
                revision == null ? null : revision.toLocalDateTime(), largoONulo(rs, "REVISADA_POR_USUARIO_APP_ID"),
                rs.getString("REVISADA_POR_PANEL"));
    }

    private static Long largoONulo(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    @Override
    public long insertar(SolicitudFamilia s, List<SolicitudFamiliaJugador> hijos) {

        KeyHolder claves = new GeneratedKeyHolder();

        jdbcTemplate.update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement("""
                    INSERT INTO SOLICITUDES_FAMILIA (USUARIO_APP_ID, NOMBRE, APELLIDOS, EMAIL, TELEFONO,
                        PARENTESCO, ESTADO, FECHA_SOLICITUD, ACEPTA_PRIVACIDAD, DECLARA_REPRESENTANTE)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1)""", new String[] { "ID" });
            ps.setLong(1, s.usuarioAppId());
            ps.setString(2, s.nombre());
            ps.setString(3, s.apellidos());
            ps.setString(4, s.email());
            ps.setString(5, s.telefono());
            ps.setString(6, s.parentesco());
            ps.setString(7, s.estado());
            ps.setTimestamp(8, Timestamp.valueOf(s.fechaSolicitud()));
            return ps;
        }, claves);

        long id = claves.getKey().longValue();

        insertarHijos(id, hijos);

        return id;
    }

    private void insertarHijos(long solicitudId, List<SolicitudFamiliaJugador> hijos) {
        for (SolicitudFamiliaJugador h : hijos) {
            jdbcTemplate.update("""
                    INSERT INTO SOLICITUDES_FAMILIA_JUGADOR (SOLICITUD_ID, NOMBRE_JUGADOR, APELLIDOS_JUGADOR,
                        EQUIPO_TEXTO, ESTADO)
                    VALUES (?, ?, ?, ?, 'PENDIENTE')""", solicitudId, h.nombre(), h.apellidos(), h.equipoTexto());
        }
    }

    @Override
    public void reemplazarDatos(long solicitudId, SolicitudFamilia d, List<SolicitudFamiliaJugador> hijos) {

        jdbcTemplate.update("""
                UPDATE SOLICITUDES_FAMILIA SET NOMBRE = ?, APELLIDOS = ?, TELEFONO = ?, PARENTESCO = ?,
                    FECHA_SOLICITUD = ?
                WHERE ID = ? AND ESTADO = 'PENDIENTE_EMAIL'""", d.nombre(), d.apellidos(), d.telefono(),
                d.parentesco(), Timestamp.valueOf(d.fechaSolicitud()), solicitudId);

        jdbcTemplate.update("DELETE FROM SOLICITUDES_FAMILIA_JUGADOR WHERE SOLICITUD_ID = ?", solicitudId);
        insertarHijos(solicitudId, hijos);
    }

    @Override
    public SolicitudFamilia obtenerPorId(long id) {
        List<SolicitudFamilia> lista = jdbcTemplate.query(
                "SELECT " + COLUMNAS + " FROM SOLICITUDES_FAMILIA WHERE ID = ?", SOLICITUD, id);
        return lista.isEmpty() ? null : lista.get(0);
    }

    @Override
    public List<SolicitudFamilia> obtenerPorUsuario(long usuarioAppId) {
        return jdbcTemplate.query("SELECT " + COLUMNAS
                + " FROM SOLICITUDES_FAMILIA WHERE USUARIO_APP_ID = ? ORDER BY FECHA_SOLICITUD DESC, ID DESC",
                SOLICITUD, usuarioAppId);
    }

    @Override
    public SolicitudFamilia obtenerAbiertaDeUsuario(long usuarioAppId) {
        List<SolicitudFamilia> lista = jdbcTemplate.query("SELECT " + COLUMNAS + """

                FROM SOLICITUDES_FAMILIA
                WHERE USUARIO_APP_ID = ? AND ESTADO IN ('PENDIENTE_EMAIL', 'PENDIENTE')
                ORDER BY FECHA_SOLICITUD DESC, ID DESC""", SOLICITUD, usuarioAppId);
        return lista.isEmpty() ? null : lista.get(0);
    }

    @Override
    public List<SolicitudFamiliaJugador> obtenerHijos(long solicitudId) {
        return jdbcTemplate.query(
                "SELECT * FROM SOLICITUDES_FAMILIA_JUGADOR WHERE SOLICITUD_ID = ? ORDER BY ID", HIJO, solicitudId);
    }

    @Override
    public SolicitudFamiliaJugador obtenerHijo(long hijoId) {
        List<SolicitudFamiliaJugador> lista = jdbcTemplate.query(
                "SELECT * FROM SOLICITUDES_FAMILIA_JUGADOR WHERE ID = ?", HIJO, hijoId);
        return lista.isEmpty() ? null : lista.get(0);
    }

    @Override
    public int confirmarEmail(long solicitudId) {
        return jdbcTemplate.update(
                "UPDATE SOLICITUDES_FAMILIA SET ESTADO = 'PENDIENTE' WHERE ID = ? AND ESTADO = 'PENDIENTE_EMAIL'",
                solicitudId);
    }

    @Override
    public List<SolicitudFamilia> listarPorEstado(String estado) {
        return jdbcTemplate.query("SELECT " + COLUMNAS
                + " FROM SOLICITUDES_FAMILIA WHERE ESTADO = ? ORDER BY FECHA_SOLICITUD, ID", SOLICITUD, estado);
    }

    @Override
    public int contarPorEstado(String estado) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SOLICITUDES_FAMILIA WHERE ESTADO = ?", Integer.class, estado);
        return total == null ? 0 : total;
    }

    @Override
    public List<SolicitudFamilia> listarRevisadasDesde(LocalDateTime desde) {
        return jdbcTemplate.query("SELECT " + COLUMNAS + """

                FROM SOLICITUDES_FAMILIA
                WHERE ESTADO IN ('APROBADA', 'RECHAZADA') AND FECHA_REVISION >= ?
                ORDER BY FECHA_REVISION DESC, ID DESC""", SOLICITUD, Timestamp.valueOf(desde));
    }

    @Override
    public void aprobarHijo(long hijoId, long jugadorId) {
        jdbcTemplate.update("""
                UPDATE SOLICITUDES_FAMILIA_JUGADOR SET ESTADO = 'APROBADO', JUGADOR_ID = ?
                WHERE ID = ?""", jugadorId, hijoId);
    }

    @Override
    public void rechazarHijo(long hijoId) {
        jdbcTemplate.update("""
                UPDATE SOLICITUDES_FAMILIA_JUGADOR SET ESTADO = 'RECHAZADO', JUGADOR_ID = NULL
                WHERE ID = ?""", hijoId);
    }

    @Override
    public void actualizarMotivo(long solicitudId, String motivo) {
        jdbcTemplate.update("UPDATE SOLICITUDES_FAMILIA SET MOTIVO_RECHAZO = ? WHERE ID = ?", motivo, solicitudId);
    }

    @Override
    public void cerrar(long solicitudId, String estado, String motivo, LocalDateTime fecha, Long revisadaPorId,
            String revisadaPorPanel) {
        jdbcTemplate.update("""
                UPDATE SOLICITUDES_FAMILIA SET ESTADO = ?, MOTIVO_RECHAZO = ?, FECHA_REVISION = ?,
                    REVISADA_POR_USUARIO_APP_ID = ?, REVISADA_POR_PANEL = ?
                WHERE ID = ?""", estado, motivo, Timestamp.valueOf(fecha), revisadaPorId, revisadaPorPanel,
                solicitudId);
    }

    @Override
    public List<SolicitudFamilia> listarCaducables(LocalDateTime limite) {
        Timestamp t = Timestamp.valueOf(limite);

        return jdbcTemplate.query("SELECT " + COLUMNAS + """

                FROM SOLICITUDES_FAMILIA
                WHERE (ESTADO IN ('PENDIENTE_EMAIL', 'PENDIENTE') AND FECHA_SOLICITUD < ?)
                   OR (ESTADO = 'RECHAZADA' AND FECHA_REVISION < ?)
                ORDER BY ID""", SOLICITUD, t, t);
    }

    @Override
    public List<SolicitudFamilia> listarPendientesAnterioresA(LocalDateTime limite) {
        return jdbcTemplate.query("SELECT " + COLUMNAS + """

                FROM SOLICITUDES_FAMILIA
                WHERE ESTADO = 'PENDIENTE' AND FECHA_SOLICITUD < ?
                ORDER BY FECHA_SOLICITUD, ID""", SOLICITUD, Timestamp.valueOf(limite));
    }

    @Override
    public void caducar(long solicitudId) {
        jdbcTemplate.update("DELETE FROM SOLICITUDES_FAMILIA_JUGADOR WHERE SOLICITUD_ID = ?", solicitudId);
        jdbcTemplate.update("DELETE FROM SOLICITUDES_FAMILIA WHERE ID = ?", solicitudId);
    }

    @Override
    public List<com.mikedev.mutxamelcf.model.JugadorCandidato> listarJugadoresActivos() {
        return jdbcTemplate.query("""
                SELECT J.ID, J.NOMBRE, J.APELLIDOS, J.EQUIPO_ID, E.NOMBRE AS EQUIPO_NOMBRE,
                       COALESCE(E.CATEGORIA, J.CATEGORIA) AS CATEGORIA_JUGADOR
                FROM JUGADORES J
                LEFT JOIN EQUIPO E ON E.ID = J.EQUIPO_ID
                WHERE J.FECHA_BAJA IS NULL
                ORDER BY J.APELLIDOS, J.NOMBRE, J.ID""",
                (rs, n) -> {
                    long equipo = rs.getLong("EQUIPO_ID");
                    Long equipoId = rs.wasNull() ? null : equipo;

                    return new com.mikedev.mutxamelcf.model.JugadorCandidato(rs.getLong("ID"), rs.getString("NOMBRE"),
                            rs.getString("APELLIDOS"), equipoId, rs.getString("EQUIPO_NOMBRE"),
                            rs.getString("CATEGORIA_JUGADOR"));
                });
    }
}
