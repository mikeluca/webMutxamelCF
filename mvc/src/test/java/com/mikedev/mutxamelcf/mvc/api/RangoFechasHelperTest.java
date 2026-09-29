package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RangoFechasHelperTest {

    @Test
    void aceptaUnRangoDentroDelLimite() {
        assertThatCode(() -> RangoFechasHelper.validar(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaFechasNulas() {
        assertThatThrownBy(() -> RangoFechasHelper.validar(null, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RangoFechasHelper.validar(LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaHastaAnteriorADesde() {
        assertThatThrownBy(() -> RangoFechasHelper.validar(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnRangoDeMasDe93Dias() {
        // DB-04: sin este limite, desde=1900-01-01&hasta=2100-01-01 forzaba
        // a la BD a recorrer todo el historico.
        assertThatThrownBy(() -> RangoFechasHelper.validar(
                LocalDate.of(1900, 1, 1), LocalDate.of(2100, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceptaExactamente93Dias() {
        assertThatCode(() -> RangoFechasHelper.validar(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(93)))
                .doesNotThrowAnyException();
    }
}
