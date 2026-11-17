package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TextoPublicacionRedesTest {

    @Test
    void terminanConLosHashtagsDelClub() {

        LocalDate viernes = LocalDate.of(2026, 11, 13);

        assertThat(TextoPublicacionRedes.previa(viernes, viernes.plusDays(2)))
                .endsWith("#somMTX #mareaAzul #MutxamelCF")
                .contains("del 13 al 15 de noviembre");
        assertThat(TextoPublicacionRedes.resultados(viernes, viernes.plusDays(2)))
                .endsWith("#somMTX #mareaAzul #MutxamelCF");
    }

    @Test
    void elPeriodoQueCruzaDeMesLoDiceCompleto() {

        assertThat(TextoPublicacionRedes.periodo(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 1)))
                .isEqualTo("del 30 de octubre al 1 de noviembre");
    }
}
