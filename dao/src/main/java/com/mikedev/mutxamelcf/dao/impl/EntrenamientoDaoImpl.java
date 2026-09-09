package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Date;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.model.Entrenamiento;

@Repository
public class EntrenamientoDaoImpl implements EntrenamientoDao {

        private final JdbcTemplate jdbcTemplate;

        public EntrenamientoDaoImpl(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        private final RowMapper<Entrenamiento> rowMapper = new RowMapper<Entrenamiento>() {

                @Override
                public Entrenamiento mapRow(
                                ResultSet rs,
                                int rowNum) throws SQLException {

                        Entrenamiento entrenamiento = new Entrenamiento();

                        entrenamiento.setId(
                                        rs.getLong("ID"));

                        entrenamiento.setEquipoId(
                                        rs.getLong("EQUIPO_ID"));

                        Date fecha = rs.getDate("FECHA");

                        if (fecha != null) {
                                entrenamiento.setFecha(
                                                fecha.toLocalDate());
                        }

                        entrenamiento.setUsuarioEntrenadorId(
                                        rs.getLong("USUARIO_ENTRENADOR_ID"));

                        if (rs.getTimestamp("FECHA_CREACION") != null) {
                                entrenamiento.setFechaCreacion(
                                                rs.getTimestamp("FECHA_CREACION")
                                                                .toLocalDateTime());
                        }

                        return entrenamiento;
                }
        };

        @Override
        public Entrenamiento guardar(Entrenamiento entrenamiento) {

                String sql = """
                                INSERT INTO ENTRENAMIENTOS (
                                    EQUIPO_ID,
                                    FECHA,
                                    USUARIO_ENTRENADOR_ID
                                )
                                VALUES (?, ?, ?)
                                """;

                KeyHolder keyHolder = new GeneratedKeyHolder();

                jdbcTemplate.update(connection -> {
                        PreparedStatement ps = connection.prepareStatement(
                                        sql,
                                        new String[] { "ID" });

                        ps.setLong(1, entrenamiento.getEquipoId());
                        ps.setDate(
                                        2,
                                        java.sql.Date.valueOf(entrenamiento.getFecha()));
                        ps.setLong(3, entrenamiento.getUsuarioEntrenadorId());

                        return ps;
                }, keyHolder);

                Number key = keyHolder.getKey();

                if (key == null) {
                        throw new IllegalStateException(
                                        "No se pudo obtener el ID generado del entrenamiento");
                }

                entrenamiento.setId(key.longValue());

                return entrenamiento;
        }

        @Override
        public void actualizar(Entrenamiento entrenamiento) {

                String sql = """
                                UPDATE ENTRENAMIENTOS
                                SET FECHA = ?
                                WHERE ID = ?
                                """;

                jdbcTemplate.update(
                                sql,
                                entrenamiento.getFecha(),
                                entrenamiento.getId());
        }

        @Override
        public Entrenamiento obtenerPorId(
                        Long id) {

                String sql = """
                                SELECT
                                    ID,
                                    EQUIPO_ID,
                                    FECHA,
                                    USUARIO_ENTRENADOR_ID,
                                    FECHA_CREACION
                                FROM ENTRENAMIENTOS
                                WHERE ID = ?
                                """;

                List<Entrenamiento> resultado = jdbcTemplate.query(
                                sql,
                                rowMapper,
                                id);

                return resultado.isEmpty()
                                ? null
                                : resultado.get(0);
        }

        @Override
        public List<Entrenamiento> obtenerPorEquipo(
                        Long equipoId) {

                String sql = """
                                SELECT
                                    ID,
                                    EQUIPO_ID,
                                    FECHA,
                                    USUARIO_ENTRENADOR_ID,
                                    FECHA_CREACION
                                FROM ENTRENAMIENTOS
                                WHERE EQUIPO_ID = ?
                                ORDER BY FECHA DESC, ID DESC
                                """;

                return jdbcTemplate.query(
                                sql,
                                rowMapper,
                                equipoId);
        }

        @Override
        public boolean existe(Long id) {

                String sql = """
                                SELECT COUNT(*)
                                FROM ENTRENAMIENTOS
                                WHERE ID = ?
                                """;

                Integer count = jdbcTemplate.queryForObject(
                                sql,
                                Integer.class,
                                id);

                return count != null && count > 0;
        }

        @Override
        public void eliminar(Long id) {

                jdbcTemplate.update(
                                "DELETE FROM ENTRENAMIENTOS WHERE ID = ?",
                                id);
        }
}