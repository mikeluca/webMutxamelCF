package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.model.PartidoLiveEvento;

class TarjetasEventosTest {

    private final List<PartidoLiveEvento> eventos = new ArrayList<>();

    private void tarjeta(String tipo, long jugador) {
        PartidoLiveEvento e = new PartidoLiveEvento();
        e.setId((long) eventos.size() + 1);
        e.setTipo(tipo);
        e.setJugadorId(jugador);
        eventos.add(e);
    }

    @Test
    void laDobleAmarillaNoCuentaAmarillasPeroSiLaExpulsion() {
        tarjeta(PartidoLiveEvento.TARJETA_AMARILLA, 1);
        tarjeta(PartidoLiveEvento.TARJETA_AMARILLA, 1);   // doble: 1 expulsado, 0 amarillas
        tarjeta(PartidoLiveEvento.TARJETA_AMARILLA, 2);   // una amarilla
        tarjeta(PartidoLiveEvento.TARJETA_AMARILLA, 3);
        tarjeta(PartidoLiveEvento.TARJETA_ROJA, 3);       // amarilla y roja directa: la amarilla cuenta

        assertThat(TarjetasEventos.expulsados(eventos)).containsExactlyInAnyOrder(1L, 3L);
        assertThat(TarjetasEventos.dobles(eventos)).containsExactly(2L);
        assertThat(TarjetasEventos.amarillasQueCuentan(eventos)).containsOnly(
                org.assertj.core.api.Assertions.entry(2L, 1), org.assertj.core.api.Assertions.entry(3L, 1));
    }
}
