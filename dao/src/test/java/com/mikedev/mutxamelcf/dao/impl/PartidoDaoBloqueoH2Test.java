package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class PartidoDaoBloqueoH2Test {

    @Test
    void bloquearPorIdFuncionaDentroDeUnaTransaccionYConUnPartidoQueNoExiste() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE PARTIDOS (ID NUMBER PRIMARY KEY)");
        jdbc.update("INSERT INTO PARTIDOS VALUES (30)");

        PartidoDaoImpl dao = new PartidoDaoImpl(jdbc);

        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(estado -> {
            dao.bloquearPorId(30L);
            dao.bloquearPorId(999L);
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PARTIDOS", Integer.class)).isEqualTo(1);
    }
}
