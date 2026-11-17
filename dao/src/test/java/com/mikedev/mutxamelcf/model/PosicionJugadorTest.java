package com.mikedev.mutxamelcf.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PosicionJugadorTest {

    @Test
    void laListaEsCerradaYEnOrden() {

        assertThat(PosicionJugador.textos())
                .containsExactly("Portero", "Defensa", "Centrocampista", "Extremo", "Delantero");
        assertThat(PosicionJugador.CENTROCAMPISTA.codigo()).isEqualTo("CENTROCAMPISTA");
        assertThat(PosicionJugador.CENTROCAMPISTA.texto()).isEqualTo("Centrocampista");
    }

    @Test
    void elCodigoSoloAceptaLosDeLaLista() {

        assertThat(PosicionJugador.desdeCodigo("DEFENSA")).isEqualTo(PosicionJugador.DEFENSA);
        assertThat(PosicionJugador.desdeCodigo(" extremo ")).isEqualTo(PosicionJugador.EXTREMO);
        assertThat(PosicionJugador.desdeCodigo("LATERAL")).isNull();
        assertThat(PosicionJugador.desdeCodigo("Defensa central")).isNull();
        assertThat(PosicionJugador.desdeCodigo("")).isNull();
        assertThat(PosicionJugador.desdeCodigo(null)).isNull();
    }

    @Test
    void elTextoGuardadoCoincideSinMayusculasNiAcentos() {

        assertThat(PosicionJugador.codigoDeTexto("Portero")).isEqualTo("PORTERO");
        assertThat(PosicionJugador.codigoDeTexto("portero")).isEqualTo("PORTERO");
        assertThat(PosicionJugador.codigoDeTexto("PORTERO")).isEqualTo("PORTERO");
        assertThat(PosicionJugador.codigoDeTexto("  Centrocampista ")).isEqualTo("CENTROCAMPISTA");
        assertThat(PosicionJugador.codigoDeTexto("DÉFENSA")).isEqualTo("DEFENSA");
        assertThat(PosicionJugador.codigoDeTexto("delantero")).isEqualTo("DELANTERO");
    }

    @Test
    void unTextoQueNoEsDeLaListaNoTieneCodigo() {

        assertThat(PosicionJugador.codigoDeTexto("DEL")).isNull();
        assertThat(PosicionJugador.codigoDeTexto("Lateral derecho")).isNull();
        assertThat(PosicionJugador.codigoDeTexto("")).isNull();
        assertThat(PosicionJugador.codigoDeTexto(null)).isNull();
    }
}
