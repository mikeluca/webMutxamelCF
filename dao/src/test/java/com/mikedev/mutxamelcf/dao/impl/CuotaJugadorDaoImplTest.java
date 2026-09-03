package com.mikedev.mutxamelcf.dao.impl;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class CuotaJugadorDaoImplTest {

    @Test
    void eliminarDebeBorrarPagosAntesDeLaCuota() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update("DELETE FROM PAGOS WHERE CUOTA_JUGADOR_ID = ?", 42L)).thenReturn(1);
        when(jdbcTemplate.update("DELETE FROM CUOTAS_JUGADOR WHERE ID = ?", 42L)).thenReturn(1);

        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        dao.eliminar(42L);

        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).update("DELETE FROM PAGOS WHERE CUOTA_JUGADOR_ID = ?", 42L);
        inOrder.verify(jdbcTemplate).update("DELETE FROM CUOTAS_JUGADOR WHERE ID = ?", 42L);
    }
}
