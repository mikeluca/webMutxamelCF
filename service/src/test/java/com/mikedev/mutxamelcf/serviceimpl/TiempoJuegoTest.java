package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TiempoJuegoTest {

    @Test
    void elTiempoDeParejasSeEnseñaComoMinutosSegundosYDecimas() {
        assertThat(TiempoJuego.formatear(31_400)).isEqualTo("0:31,4");
        assertThat(TiempoJuego.formatear(83_450)).isEqualTo("1:23,4"); // las décimas se truncan
        assertThat(TiempoJuego.formatear(65_300)).isEqualTo("1:05,3");
        assertThat(TiempoJuego.formatear(8_000)).isEqualTo("0:08,0");
        assertThat(TiempoJuego.formatear(600_000)).isEqualTo("10:00,0");
        assertThat(TiempoJuego.formatear(7_200_000)).isEqualTo("120:00,0");
    }

    @Test
    void sinMarcaSeEnseñaUnGuion() {
        assertThat(TiempoJuego.formatear(0)).isEqualTo("—");
        assertThat(TiempoJuego.formatear(-5)).isEqualTo("—");
    }
}
