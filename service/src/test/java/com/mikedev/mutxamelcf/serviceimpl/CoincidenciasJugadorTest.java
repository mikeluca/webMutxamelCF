package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.model.JugadorCandidato;
import com.mikedev.mutxamelcf.serviceimpl.CoincidenciasJugador.Coincidencia;

class CoincidenciasJugadorTest {

    private static JugadorCandidato j(long id, String nombre, String apellidos, Long equipoId, String equipo,
            String categoria) {
        return new JugadorCandidato(id, nombre, apellidos, equipoId, equipo, categoria);
    }

    private static final List<JugadorCandidato> ACTIVOS = List.of(
            j(1, "Luis", "Pérez Gómez", 8L, "Alevín B", "Alevín"),
            j(2, "Luis", "Pérez Ruiz", 9L, "Benjamín A", "Benjamín"),
            j(3, "Eva", "García", 8L, "Alevín B", "Alevín"),
            j(4, "Álvaro", "Núñez Ibáñez", null, null, "Prueba"));

    private static List<Long> ids(List<Coincidencia> lista) {
        return lista.stream().map(c -> c.jugador().id()).toList();
    }

    @Test
    void normalizaAcentosMayusculasSignosYEspacios() {
        assertThat(CoincidenciasJugador.normalizar("  ÁLVARO   Núñez-Ibáñez. ")).isEqualTo("alvaro nunez ibanez");
        assertThat(CoincidenciasJugador.normalizar(null)).isEmpty();
        assertThat(CoincidenciasJugador.normalizar("Alevín B")).isEqualTo(CoincidenciasJugador.normalizar("ALEVIN  b"));
    }

    @Test
    void coincidePorNombreYApellidosSinAcentosNiMayusculas() {

        List<Coincidencia> r = CoincidenciasJugador.buscar("ALVARO", "nunez  ibanez", null, ACTIVOS);

        assertThat(ids(r)).containsExactly(4L);
    }

    @Test
    void toleraQueFalteElSegundoApellido() {

        // "Pérez" es el principio de "Pérez Gómez" y de "Pérez Ruiz": las dos son candidatas.
        assertThat(ids(CoincidenciasJugador.buscar("Luis", "Pérez", null, ACTIVOS))).containsExactlyInAnyOrder(1L, 2L);

        // Con el segundo apellido solo coincide una.
        assertThat(ids(CoincidenciasJugador.buscar("Luis", "Pérez Gómez", null, ACTIVOS))).containsExactly(1L);
    }

    @Test
    void siLaFamiliaEscribeMasApellidosQueLaFichaTambienCoincide() {

        assertThat(ids(CoincidenciasJugador.buscar("Eva", "García López", null, ACTIVOS))).containsExactly(3L);
    }

    @Test
    void unApellidoParcialNoCoincide() {

        assertThat(CoincidenciasJugador.buscar("Luis", "Per", null, ACTIVOS)).isEmpty();
        assertThat(CoincidenciasJugador.buscar("Lui", "Pérez", null, ACTIVOS)).isEmpty();
    }

    @Test
    void sinNombreOSinApellidosNoDevuelveNada() {

        assertThat(CoincidenciasJugador.buscar("", "Pérez", null, ACTIVOS)).isEmpty();
        assertThat(CoincidenciasJugador.buscar("Luis", null, null, ACTIVOS)).isEmpty();
    }

    @Test
    void elEquipoEscritoMarcaYOrdenaPeroNoExcluye() {

        List<Coincidencia> r = CoincidenciasJugador.buscar("Luis", "Pérez", "benjamin a", ACTIVOS);

        assertThat(ids(r)).containsExactly(2L, 1L);
        assertThat(r.get(0).coincideEquipo()).isTrue();
        assertThat(r.get(1).coincideEquipo()).isFalse();
    }

    @Test
    void laCategoriaTambienCuentaComoEquipo() {

        List<Coincidencia> r = CoincidenciasJugador.buscar("Luis", "Pérez", "Alevín", ACTIVOS);

        assertThat(ids(r)).containsExactly(1L, 2L);
        assertThat(r.get(0).coincideEquipo()).isTrue();
        assertThat(r.get(1).coincideEquipo()).isFalse();
    }

    @Test
    void unEquipoEscritoMalNoMarcaNada() {

        List<Coincidencia> r = CoincidenciasJugador.buscar("Luis", "Pérez", "Juvenil", ACTIVOS);

        assertThat(ids(r)).containsExactlyInAnyOrder(1L, 2L);
        assertThat(r).noneMatch(Coincidencia::coincideEquipo);
    }

    @Test
    void laLetraDelEquipoDistingueEquiposDeLaMismaCategoria() {

        List<JugadorCandidato> mismos = List.of(
                j(1, "Luis", "Pérez", 8L, "Alevín A", "Alevín"),
                j(2, "Luis", "Pérez", 9L, "Alevín B", "Alevín"));

        List<Coincidencia> r = CoincidenciasJugador.buscar("Luis", "Pérez", "Alevín B", mismos);

        assertThat(ids(r)).containsExactly(2L, 1L);
        assertThat(r.get(0).coincideEquipo()).isTrue();
        assertThat(r.get(1).coincideEquipo()).isFalse();
    }

    @Test
    void buscaAManoPorTodasLasPalabrasConMinimoTresLetras() {

        assertThat(CoincidenciasJugador.buscarPorTexto("nunez alv", ACTIVOS, 20)).extracting(JugadorCandidato::id)
                .containsExactly(4L);
        assertThat(CoincidenciasJugador.buscarPorTexto("PEREZ", ACTIVOS, 20)).extracting(JugadorCandidato::id)
                .containsExactly(1L, 2L);
        assertThat(CoincidenciasJugador.buscarPorTexto("pe", ACTIVOS, 20)).isEmpty();
        assertThat(CoincidenciasJugador.buscarPorTexto(null, ACTIVOS, 20)).isEmpty();
        assertThat(CoincidenciasJugador.buscarPorTexto("perez", ACTIVOS, 1)).hasSize(1);
    }
}
