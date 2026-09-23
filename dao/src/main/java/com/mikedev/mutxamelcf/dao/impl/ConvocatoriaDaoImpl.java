package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.model.Convocatoria;

@Repository
public class ConvocatoriaDaoImpl
                implements ConvocatoriaDao {

        private final JdbcTemplate jdbcTemplate;

        public ConvocatoriaDaoImpl(
                        JdbcTemplate jdbcTemplate) {

                this.jdbcTemplate = jdbcTemplate;
        }

        private final RowMapper<Convocatoria> rowMapper = new RowMapper<Convocatoria>() {

                @Override
                public Convocatoria mapRow(
                                ResultSet rs,
                                int rowNum) throws SQLException {

                        Convocatoria convocatoria = new Convocatoria();

                        convocatoria.setId(
                                        rs.getLong("ID"));

                        convocatoria.setEquipoId(
                                        rs.getLong("EQUIPO_ID"));

                        convocatoria.setRival(
                                        rs.getString("RIVAL"));

                        convocatoria.setCampo(
                                        rs.getString("CAMPO"));

                        Date fecha = rs.getDate("FECHA_PARTIDO");

                        if (fecha != null) {
                                convocatoria.setFechaPartido(
                                                fecha.toLocalDate());
                        }

                        convocatoria.setHoraPartido(
                                        rs.getString("HORA_PARTIDO"));

                        convocatoria.setHoraConvocatoria(
                                        rs.getString("HORA_CONVOCATORIA"));

                        convocatoria.setLugarConvocatoria(
                                        rs.getString("LUGAR_CONVOCATORIA"));

                        convocatoria.setUsuarioEntrenadorId(
                                        rs.getLong(
                                                        "USUARIO_ENTRENADOR_ID"));

                        if (rs.getTimestamp(
                                        "FECHA_CREACION") != null) {

                                convocatoria.setFechaCreacion(
                                                rs.getTimestamp(
                                                                "FECHA_CREACION")
                                                                .toLocalDateTime());
                        }

                        return convocatoria;
                }
        };

        @Override
        public Convocatoria guardar(Convocatoria convocatoria) {

                String sql = """
                                INSERT INTO CONVOCATORIAS (
                                    EQUIPO_ID,
                                    RIVAL,
                                    CAMPO,
                                    FECHA_PARTIDO,
                                    HORA_PARTIDO,
                                    HORA_CONVOCATORIA,
                                    LUGAR_CONVOCATORIA,
                                    USUARIO_ENTRENADOR_ID
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """;

                KeyHolder keyHolder = new GeneratedKeyHolder();

                jdbcTemplate.update(connection -> {
                        PreparedStatement ps = connection.prepareStatement(
                                        sql,
                                        new String[] { "ID" });

                        ps.setLong(1, convocatoria.getEquipoId());
                        ps.setString(2, convocatoria.getRival());
                        ps.setString(3, convocatoria.getCampo());
                        ps.setDate(
                                        4,
                                        java.sql.Date.valueOf(convocatoria.getFechaPartido()));
                        ps.setObject(5, convocatoria.getHoraPartido());
                        ps.setObject(6, convocatoria.getHoraConvocatoria());
                        ps.setString(7, convocatoria.getLugarConvocatoria());
                        ps.setLong(8, convocatoria.getUsuarioEntrenadorId());

                        return ps;
                }, keyHolder);

                Number key = keyHolder.getKey();

                if (key == null) {
                        throw new IllegalStateException(
                                        "No se pudo obtener el ID generado de la convocatoria");
                }

                convocatoria.setId(key.longValue());

                return convocatoria;
        }

        @Override
        public void actualizar(Convocatoria convocatoria) {

                String sql = """
                                UPDATE CONVOCATORIAS
                                SET RIVAL = ?,
                                    CAMPO = ?,
                                    FECHA_PARTIDO = ?,
                                    HORA_PARTIDO = ?,
                                    HORA_CONVOCATORIA = ?,
                                    LUGAR_CONVOCATORIA = ?
                                WHERE ID = ?
                                """;

                jdbcTemplate.update(
                                sql,
                                convocatoria.getRival(),
                                convocatoria.getCampo(),
                                convocatoria.getFechaPartido(),
                                convocatoria.getHoraPartido(),
                                convocatoria.getHoraConvocatoria(),
                                convocatoria.getLugarConvocatoria(),
                                convocatoria.getId());
        }

        @Override
        public Convocatoria obtenerPorId(
                        Long id) {

                String sql = """
                                SELECT
                                    ID,
                                    EQUIPO_ID,
                                    RIVAL,
                                    CAMPO,
                                    FECHA_PARTIDO,
                                    HORA_PARTIDO,
                                    HORA_CONVOCATORIA,
                                    LUGAR_CONVOCATORIA,
                                    USUARIO_ENTRENADOR_ID,
                                    FECHA_CREACION
                                FROM CONVOCATORIAS
                                WHERE ID = ?
                                """;

                List<Convocatoria> resultado = jdbcTemplate.query(
                                sql,
                                rowMapper,
                                id);

                return resultado.isEmpty()
                                ? null
                                : resultado.get(0);
        }

        @Override
        public List<Convocatoria> obtenerPorEquipo(
                        Long equipoId) {

                String sql = """
                                SELECT
                                    ID,
                                    EQUIPO_ID,
                                    RIVAL,
                                    CAMPO,
                                    FECHA_PARTIDO,
                                    HORA_PARTIDO,
                                    HORA_CONVOCATORIA,
                                    LUGAR_CONVOCATORIA,
                                    USUARIO_ENTRENADOR_ID,
                                    FECHA_CREACION
                                FROM CONVOCATORIAS
                                WHERE EQUIPO_ID = ?
                                ORDER BY FECHA_PARTIDO DESC, ID DESC
                                """;

                return jdbcTemplate.query(
                                sql,
                                rowMapper,
                                equipoId);
        }

        @Override
        public boolean existePorEquipoYFecha(
                        Long equipoId,
                        LocalDate fechaPartido) {

                String sql = """
                                SELECT COUNT(*)
                                FROM CONVOCATORIAS
                                WHERE EQUIPO_ID = ?
                                  AND FECHA_PARTIDO = ?
                                """;

                Integer count = jdbcTemplate.queryForObject(
                                sql,
                                Integer.class,
                                equipoId,
                                java.sql.Date.valueOf(fechaPartido));

                return count != null && count > 0;
        }

        @Override
        public void eliminar(
                        Long id) {

                jdbcTemplate.update(
                                "DELETE FROM CONVOCATORIAS WHERE ID = ?",
                                id);
        }
}