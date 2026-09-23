package com.mikedev.mutxamelcf.dao.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PartidoLiveDao;
import com.mikedev.mutxamelcf.model.PartidoLiveEstado;

@Repository
public class PartidoLiveDaoImpl implements PartidoLiveDao {

    private final JdbcTemplate jdbcTemplate;

    public PartidoLiveDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PartidoLiveEstado obtenerEstado() {

        PartidoLiveEstado estado = jdbcTemplate.queryForObject(
                """
                SELECT GOLES_FAVOR, GOLES_CONTRA
                FROM PARTIDO_LIVE
                """,
                (rs, rowNum) -> new PartidoLiveEstado(
                        rs.getInt("GOLES_FAVOR"),
                        rs.getInt("GOLES_CONTRA"),
                        null));

        List<String> goleadores = jdbcTemplate.query(
                """
                SELECT NOMBRE
                FROM PARTIDO_LIVE_GOLEADOR
                ORDER BY ID ASC
                """,
                (rs, rowNum) -> rs.getString("NOMBRE"));

        estado.setGoleadores(goleadores);

        return estado;
    }

    @Override
    public void reiniciar() {

        jdbcTemplate.update(
                """
                UPDATE PARTIDO_LIVE
                SET GOLES_FAVOR = 0,
                    GOLES_CONTRA = 0
                """);

        jdbcTemplate.update("DELETE FROM PARTIDO_LIVE_GOLEADOR");
    }

    @Override
    public void sumarGolFavor(String autor) {

        jdbcTemplate.update(
                """
                UPDATE PARTIDO_LIVE
                SET GOLES_FAVOR = GOLES_FAVOR + 1
                """);

        jdbcTemplate.update(
                "INSERT INTO PARTIDO_LIVE_GOLEADOR (NOMBRE) VALUES (?)",
                autor);
    }

    @Override
    public void sumarGolContra() {

        jdbcTemplate.update(
                """
                UPDATE PARTIDO_LIVE
                SET GOLES_CONTRA = GOLES_CONTRA + 1
                """);
    }
}
