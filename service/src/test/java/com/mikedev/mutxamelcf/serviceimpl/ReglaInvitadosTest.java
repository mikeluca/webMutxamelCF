package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.serviceimpl.ReglaInvitados.EquipoRegla;

class ReglaInvitadosTest {

    private static final List<CategoriaEdad> CATEGORIAS = List.of(
            new CategoriaEdad("Escuelita", 4, 6, 1),
            new CategoriaEdad("Prebenjamín", 7, 8, 2),
            new CategoriaEdad("Benjamín", 9, 10, 3),
            new CategoriaEdad("Alevín", 11, 12, 4),
            new CategoriaEdad("Infantil", 13, 14, 5),
            new CategoriaEdad("Cadete", 15, 16, 6),
            new CategoriaEdad("Juvenil", 17, 18, 7));

    private final ReglaInvitados regla = new ReglaInvitados();

    private static long siguiente = 1;

    private static EquipoRegla equipo(String categoria, String grupo) {
        return new EquipoRegla(siguiente++, categoria, grupo, false);
    }

    private boolean puede(EquipoRegla destino, EquipoRegla origen) {
        return regla.puedeInvitar(destino, origen, CATEGORIAS);
    }

    // ------------------------------------------------------------------ mismo categoría: A > C > B > D

    @ParameterizedTest(name = "{0} invita a {1}: {2}")
    @CsvSource({
            "A, B, true", "A, C, true", "A, D, true",
            "C, B, true", "C, D, true", "C, A, false",
            "B, D, true", "B, A, false", "B, C, false",
            "D, A, false", "D, B, false", "D, C, false",
            "A, A, false", "B, B, false"
    })
    void dentroDeLaCategoriaSoloSeSubeDeNivel(String destino, String origen, boolean esperado) {
        // El mismo equipo (misma categoría y grupo) tampoco se invita a sí mismo: tienen ids distintos pero igual nivel.
        assertThat(puede(equipo("Cadete", destino), equipo("Cadete", origen))).isEqualTo(esperado);
    }

    @Test
    void sinEquipoCSoloBInvitaDeAAlA() {
        assertThat(puede(equipo("Alevín", "A"), equipo("Alevín", "B"))).isTrue();
        assertThat(puede(equipo("Alevín", "B"), equipo("Alevín", "A"))).isFalse();
    }

    @Test
    void unEquipoNoSeInvitaASiMismo() {
        EquipoRegla a = equipo("Cadete", "A");

        assertThat(puede(a, a)).isFalse();
    }

    @Test
    void elGrupoSeCompararSinImportarMayusculasNiEspacios() {
        assertThat(puede(equipo("Cadete", " a "), equipo("Cadete", "b"))).isTrue();
    }

    @Test
    void entreCategoriasDistintasUnGrupoSinNivelNoParticipaEnLaMismaCategoria() {
        assertThat(puede(equipo("Cadete", "A"), equipo("Cadete", "X"))).isFalse();
    }

    // ------------------------------------------------------------------ categoría inmediatamente inferior

    @ParameterizedTest(name = "{0} {1} invita a {2} {3}: {4}")
    @CsvSource({
            "Cadete, A, Infantil, A, true",
            "Cadete, A, Infantil, D, true",
            "Cadete, D, Infantil, A, true",
            "Cadete, B, Infantil, C, true",
            "Cadete, A, Alevín, A, false",
            "Cadete, A, Juvenil, D, false",
            "Infantil, A, Cadete, A, false",
            "Juvenil, A, Cadete, B, true",
            "Alevín, A, Infantil, A, false",
            "Benjamín, A, Prebenjamín, B, true"
    })
    void desdeLaCategoriaInmediatamenteInferiorValeCualquierEquipo(
            String categoriaDestino, String grupoDestino, String categoriaOrigen, String grupoOrigen,
            boolean esperado) {

        assertThat(puede(equipo(categoriaDestino, grupoDestino), equipo(categoriaOrigen, grupoOrigen)))
                .isEqualTo(esperado);
    }

    @Test
    void laCategoriaInferiorSeCalculaPorOrdenYNoPorPosicion() {
        List<CategoriaEdad> conHueco = List.of(
                new CategoriaEdad("Alevín", 11, 12, 10),
                new CategoriaEdad("Infantil", 13, 14, 30));

        assertThat(regla.puedeInvitar(equipo("Infantil", "A"), equipo("Alevín", "D"), conHueco)).isTrue();
    }

    @Test
    void laCategoriaMenorNoTieneInferior() {
        assertThat(puede(equipo("Escuelita", "A"), equipo("Prebenjamín", "A"))).isFalse();
    }

    // ------------------------------------------------------------------ Filial y Primer Equipo

    @Test
    void filialInvitaACualquierEquipoDeJuvenil() {
        EquipoRegla filial = equipo("Filial", "A");

        assertThat(puede(filial, equipo("Juvenil", "A"))).isTrue();
        assertThat(puede(filial, equipo("Juvenil", "D"))).isTrue();
        assertThat(puede(filial, equipo("Cadete", "A"))).isFalse();
        assertThat(puede(filial, equipo("Primer Equipo", "A"))).isFalse();
    }

    @Test
    void primerEquipoInvitaAFilialYACualquierJuvenil() {
        EquipoRegla primero = equipo("Primer Equipo", "A");

        assertThat(puede(primero, equipo("Filial", "A"))).isTrue();
        assertThat(puede(primero, equipo("Juvenil", "B"))).isTrue();
        assertThat(puede(primero, equipo("Cadete", "A"))).isFalse();
        assertThat(puede(primero, equipo("Veteranos", "A"))).isFalse();
    }

    @Test
    void juvenilNoInvitaAFilialNiAPrimerEquipo() {
        EquipoRegla juvenil = equipo("Juvenil", "A");

        assertThat(puede(juvenil, equipo("Filial", "A"))).isFalse();
        assertThat(puede(juvenil, equipo("Primer Equipo", "A"))).isFalse();
    }

    // ------------------------------------------------------------------ quién no participa

    @Test
    void veteranosNoInvitaNiPuedeSerInvitado() {
        EquipoRegla veteranos = equipo("Veteranos", "A");

        assertThat(puede(veteranos, equipo("Juvenil", "A"))).isFalse();
        assertThat(puede(veteranos, equipo("Primer Equipo", "A"))).isFalse();
        assertThat(puede(equipo("Primer Equipo", "A"), veteranos)).isFalse();
        assertThat(puede(equipo("Filial", "A"), veteranos)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "Esc", "Fem", "esc", "FEM", "P" })
    void losGruposEscFemYPretemporadaNoParticipan(String grupo) {
        EquipoRegla excluido = equipo("Infantil", grupo);

        assertThat(regla.participa(excluido)).isFalse();
        assertThat(puede(equipo("Cadete", "A"), excluido)).isFalse();
        assertThat(puede(excluido, equipo("Alevín", "A"))).isFalse();
    }

    @Test
    void unEquipoDePretemporadaNoParticipaAunqueSuGrupoSeaNormal() {
        EquipoRegla pretemporada = new EquipoRegla(900L, "Infantil", "A", true);

        assertThat(regla.participa(pretemporada)).isFalse();
        assertThat(puede(equipo("Cadete", "A"), pretemporada)).isFalse();
        assertThat(puede(pretemporada, equipo("Alevín", "A"))).isFalse();
    }

    @Test
    void ningunEquipoONuloNoParticipa() {
        assertThat(regla.participa(null)).isFalse();
        assertThat(regla.puedeInvitar(null, equipo("Cadete", "A"), CATEGORIAS)).isFalse();
        assertThat(regla.puedeInvitar(equipo("Cadete", "A"), null, CATEGORIAS)).isFalse();
    }

    @Test
    void sinCategoriasConReglaDeEdadSoloQuedanFilialYPrimerEquipo() {
        assertThat(regla.puedeInvitar(equipo("Cadete", "A"), equipo("Infantil", "A"), List.of())).isFalse();
        assertThat(regla.puedeInvitar(equipo("Filial", "A"), equipo("Juvenil", "A"), List.of())).isTrue();
    }

    @Test
    void elMaximoEsTres() {
        assertThat(ReglaInvitados.MAXIMO_INVITADOS).isEqualTo(3);
    }
}
