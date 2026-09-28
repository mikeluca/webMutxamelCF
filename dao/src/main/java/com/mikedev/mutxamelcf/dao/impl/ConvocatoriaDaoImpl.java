package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

        /*
         * El rival/campo/fecha/hora del partido ya NO se guardan en
         * CONVOCATORIAS: se leen siempre en vivo mediante JOIN con
         * PARTIDOS (fuente única de verdad). Este mapper se usa
         * únicamente en los SELECT que incluyen ese JOIN.
         */
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

                        convocatoria.setPartidoId(
                                        rs.getLong("PARTIDO_ID"));

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
                                    PARTIDO_ID,
                                    HORA_CONVOCATORIA,
                                    LUGAR_CONVOCATORIA,
                                    USUARIO_ENTRENADOR_ID
                                )
                                VALUES (?, ?, ?, ?, ?)
                                """;

                KeyHolder keyHolder = new GeneratedKeyHolder();

                jdbcTemplate.update(connection -> {
                        PreparedStatement ps = connection.prepareStatement(
                                        sql,
                                        new String[] { "ID" });

                        ps.setLong(1, convocatoria.getEquipoId());
                        ps.setLong(2, convocatoria.getPartidoId());
                        ps.setObject(3, convocatoria.getHoraConvocatoria());
                        ps.setString(4, convocatoria.getLugarConvocatoria());
                        ps.setLong(5, convocatoria.getUsuarioEntrenadorId());

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
                                SET PARTIDO_ID = ?,
                                    HORA_CONVOCATORIA = ?,
                                    LUGAR_CONVOCATORIA = ?
                                WHERE ID = ?
                                """;

                jdbcTemplate.update(
                                sql,
                                convocatoria.getPartidoId(),
                                convocatoria.getHoraConvocatoria(),
                                convocatoria.getLugarConvocatoria(),
                                convocatoria.getId());
        }

        @Override
        public Convocatoria obtenerPorId(
                        Long id) {

                String sql = """
                                SELECT
                                    C.ID,
                                    C.EQUIPO_ID,
                                    C.PARTIDO_ID,
                                    P.RIVAL AS RIVAL,
                                    P.CAMPO AS CAMPO,
                                    P.DIA AS FECHA_PARTIDO,
                                    P.HORA AS HORA_PARTIDO,
                                    C.HORA_CONVOCATORIA,
                                    C.LUGAR_CONVOCATORIA,
                                    C.USUARIO_ENTRENADOR_ID,
                                    C.FECHA_CREACION
                                FROM CONVOCATORIAS C
                                JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
                                WHERE C.ID = ?
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
                                    C.ID,
                                    C.EQUIPO_ID,
                                    C.PARTIDO_ID,
                                    P.RIVAL AS RIVAL,
                                    P.CAMPO AS CAMPO,
                                    P.DIA AS FECHA_PARTIDO,
                                    P.HORA AS HORA_PARTIDO,
                                    C.HORA_CONVOCATORIA,
                                    C.LUGAR_CONVOCATORIA,
                                    C.USUARIO_ENTRENADOR_ID,
                                    C.FECHA_CREACION
                                FROM CONVOCATORIAS C
                                JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
                                WHERE C.EQUIPO_ID = ?
                                ORDER BY P.DIA DESC, C.ID DESC
                                """;

                return jdbcTemplate.query(
                                sql,
                                rowMapper,
                                equipoId);
        }

        @Override
        public boolean existePorPartido(
                        Long partidoId,
                        Long convocatoriaIdExcluir) {

                String sql = convocatoriaIdExcluir == null
                                ? """
                                                SELECT COUNT(*)
                                                FROM CONVOCATORIAS
                                                WHERE PARTIDO_ID = ?
                                                """
                                : """
                                                SELECT COUNT(*)
                                                FROM CONVOCATORIAS
                                                WHERE PARTIDO_ID = ?
                                                  AND ID <> ?
                                                """;

                Integer count = convocatoriaIdExcluir == null
                                ? jdbcTemplate.queryForObject(
                                                sql,
                                                Integer.class,
                                                partidoId)
                                : jdbcTemplate.queryForObject(
                                                sql,
                                                Integer.class,
                                                partidoId,
                                                convocatoriaIdExcluir);

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
