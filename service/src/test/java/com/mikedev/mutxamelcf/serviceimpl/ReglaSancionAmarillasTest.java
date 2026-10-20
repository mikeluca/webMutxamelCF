package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReglaSancionAmarillasTest {

    private final ReglaSancionAmarillas regla = new ReglaSancionAmarillas(5, 1);

    @Test
    void estadoSegunLasAmarillas() {
        assertThat(regla.estado(0)).isEqualTo("OK");
        assertThat(regla.estado(1)).isEqualTo("OK");
        assertThat(regla.estado(4)).isEqualTo("A_UNA");
        assertThat(regla.estado(5)).isEqualTo("SANCION_POSIBLE");
        assertThat(regla.estado(6)).isEqualTo("OK");
        assertThat(regla.estado(9)).isEqualTo("A_UNA");
        assertThat(regla.estado(10)).isEqualTo("SANCION_POSIBLE");
    }

    @Test
    void cruzarUmbrales() {
        assertThat(regla.cruzaAvisoPrevio(3, 4)).isTrue();
        assertThat(regla.cruzaAvisoPrevio(4, 4)).isFalse();
        assertThat(regla.cruzaAvisoPrevio(4, 5)).isFalse();
        assertThat(regla.cruzaSancion(4, 5)).isTrue();
        assertThat(regla.cruzaSancion(5, 5)).isFalse();
        assertThat(regla.cruzaSancion(5, 9)).isFalse();
        assertThat(regla.umbralSancionCruzado(3, 11)).isEqualTo(10);
        assertThat(regla.umbralSancionCruzado(5, 9)).isZero();
    }

    @Test
    void unaConfiguracionInvalidaUsaLosValoresPorDefecto() {
        ReglaSancionAmarillas invalida = new ReglaSancionAmarillas(1, 7);

        assertThat(invalida.ciclo()).isEqualTo(5);
        assertThat(invalida.estado(4)).isEqualTo("A_UNA");
    }

    @Test
    void cicloYAvisoPersonalizados() {
        ReglaSancionAmarillas otra = new ReglaSancionAmarillas(3, 1);

        assertThat(otra.estado(2)).isEqualTo("A_UNA");
        assertThat(otra.estado(3)).isEqualTo("SANCION_POSIBLE");
    }
}
