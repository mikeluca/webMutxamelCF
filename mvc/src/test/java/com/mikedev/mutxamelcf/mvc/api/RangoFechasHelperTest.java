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
    void rechazaUnRangoExcesivo() {
        // DB-04: sin este limite, desde=1900-01-01&hasta=2100-01-01 forzaba
        // a la BD a recorrer todo el historico.
        assertThatThrownBy(() -> RangoFechasHelper.validar(
                LocalDate.of(1900, 1, 1), LocalDate.of(2100, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceptaExactamente450Dias() {
        assertThatCode(() -> RangoFechasHelper.validar(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(450)))
                .doesNotThrowAnyException();
    }

    @Test
    void rechaza451Dias() {
        assertThatThrownBy(() -> RangoFechasHelper.validar(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(451)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceptaElRangoRealQuePideLaAppDeCalendario() {
        // N-02: calendario_page.dart pide desde el inicio de temporada
        // (1 de septiembre) hasta hoy + 2 meses. Con una temporada de 10
        // meses (septiembre a junio), ese rango debe seguir aceptandose.
        LocalDate inicioTemporada = LocalDate.of(2026, 9, 1);
        LocalDate hastaHoyMasDosMeses = LocalDate.of(2027, 6, 30).plusMonths(2);

        assertThatCode(() -> RangoFechasHelper.validar(inicioTemporada, hastaHoyMasDosMeses))
                .doesNotThrowAnyException();
    }

    @Test
    void aceptaElRespaldoDeUnAnioMasDosMesesSinTemporadaActiva() {
        // Bug post-N-02: sin temporada activa configurada, el cliente cae
        // a "hoy - 1 año" como 'desde' (ver calendario_page.dart), lo que
        // con 'hasta' = hoy + 2 meses puede llegar a ~428 días.
        LocalDate hoy = LocalDate.of(2026, 9, 29);
        LocalDate desde = hoy.minusYears(1);
        LocalDate hasta = hoy.plusMonths(2);

        assertThatCode(() -> RangoFechasHelper.validar(desde, hasta))
                .doesNotThrowAnyException();
    }
}
