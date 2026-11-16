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

    private void tarjetaRival(String tipo, Integer dorsal) {
        PartidoLiveEvento e = new PartidoLiveEvento();
        e.setId((long) eventos.size() + 1);
        e.setTipo(tipo);
        e.setRival(true);
        e.setDorsalRival(dorsal);
        eventos.add(e);
    }

    @Test
    void lasTarjetasDelRivalSeAgrupanPorDorsalYNuncaSeMezclanConNuestrosJugadores() {
        tarjeta(PartidoLiveEvento.TARJETA_AMARILLA, 9);        // id 1: nuestro jugador 9 (id de jugador, no dorsal)
        tarjetaRival(PartidoLiveEvento.TARJETA_AMARILLA, 9);   // id 2: dorsal 9 del rival
        tarjetaRival(PartidoLiveEvento.TARJETA_AMARILLA, 9);   // id 3: segunda del dorsal 9: doble
        tarjetaRival(PartidoLiveEvento.TARJETA_AMARILLA, null);// id 4: sin dorsal: suelta
        tarjetaRival(PartidoLiveEvento.TARJETA_AMARILLA, null);// id 5: sin dorsal: suelta, no es doble
        tarjetaRival(PartidoLiveEvento.TARJETA_AMARILLA, 4);   // id 6: una amarilla del 4

        assertThat(TarjetasEventos.dobles(eventos)).containsExactly(3L);
        assertThat(TarjetasEventos.dorsalesRivalConAmarilla(eventos)).containsExactly(4);

        // Nada del rival cuenta para los nuestros: ni expulsados ni amarillas.
        assertThat(TarjetasEventos.expulsados(eventos)).isEmpty();
        assertThat(TarjetasEventos.amarillasQueCuentan(eventos))
                .containsOnly(org.assertj.core.api.Assertions.entry(9L, 1));
    }

    @Test
    void laRojaDelRivalNoExpulsaANadieDeLosNuestros() {
        tarjeta(PartidoLiveEvento.TARJETA_ROJA, 5);
        tarjetaRival(PartidoLiveEvento.TARJETA_ROJA, 5);

        assertThat(TarjetasEventos.expulsados(eventos)).containsExactly(5L);
        assertThat(TarjetasEventos.dorsalesRivalConAmarilla(eventos)).isEmpty();
    }
}
