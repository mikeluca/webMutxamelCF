package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Condiciones SQL del recordatorio de resultado pendiente. El JdbcTemplate
 * es simulado, así que se comprueba el texto de la consulta y sus parámetros.
 */
class PartidoDaoImplAvisoResultadoTest {

    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 10, 14, 12, 0);
    private static final LocalDateTime HASTA = LocalDateTime.of(2026, 10, 17, 10, 0);

    @Test
    @SuppressWarnings("unchecked")
    void laConsultaExigeQueNoHayaResultadoPorNingunaViaNiPartidoCancelado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        dao.obtenerPendientesDeAvisoResultado(DESDE, HASTA);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(Timestamp.valueOf(DESDE)),
                eq(Timestamp.valueOf(HASTA)));

        assertThat(sql.getValue().replaceAll("\s+", " "))
                .contains("P.CANCELADO = 0")
                .contains("P.GOLES_FAVOR IS NULL")
                .contains("(P.RESULTADO IS NULL OR TRIM(REPLACE(P.RESULTADO, '-', '')) IS NULL)")
                .contains("P.AVISO_RESULTADO_ENVIADO = 0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void laHoraSoloSeUsaSiEsValidaYSiNoSeUsaElDia() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        dao.obtenerPendientesDeAvisoResultado(DESDE, HASTA);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object.class), any(Object.class));

        String consulta = sql.getValue().replaceAll("\s+", " ");

        // REGEXP_LIKE evita que TO_DATE falle (ORA-01850/01858) con "17h", "17.30" o "29:00".
        assertThat(consulta).contains("REGEXP_LIKE(TRIM(P.HORA), '^([01]?[0-9]|2[0-3]):[0-5][0-9]$')");
        assertThat(consulta).contains("TO_DATE(TO_CHAR(P.DIA, 'YYYY-MM-DD') || ' ' || TRIM(P.HORA)");
        assertThat(consulta).contains("ELSE P.DIA END");
    }

    @Test
    void marcarAvisosAntiguosEsUnaSolaUpdateConLasMismasCondiciones() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        dao.marcarAvisosAntiguosComoEnviados(DESDE);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(Timestamp.valueOf(DESDE)));

        String consulta = sql.getValue().replaceAll("\s+", " ");
        assertThat(consulta)
                .startsWith("UPDATE PARTIDOS P SET P.AVISO_RESULTADO_ENVIADO = 1 WHERE")
                .contains("P.GOLES_FAVOR IS NULL")
                .contains("TRIM(REPLACE(P.RESULTADO, '-', '')) IS NULL")
                .contains("P.AVISO_RESULTADO_ENVIADO = 0")
                .contains("< ?");
    }

}
