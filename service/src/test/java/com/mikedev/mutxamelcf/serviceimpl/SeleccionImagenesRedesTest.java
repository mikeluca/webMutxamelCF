package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.model.PartidoImagen;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Estado;

class SeleccionImagenesRedesTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 11, 14);

    private static PartidoImagen partido(String equipo, String orden, String modalidad) {
        return new PartidoImagen(1L, 1L, equipo, orden, modalidad, "Pego C.F. B", null, SABADO, "10:00", "Campo", true,
                false, null, null, null);
    }

    private static PartidoImagen cancelado(String equipo, String orden, String modalidad) {
        return new PartidoImagen(1L, 1L, equipo, orden, modalidad, "Pego C.F.", null, SABADO, "10:00", "Campo", true,
                true, null, null, null);
    }

    private static List<String> nombres(List<PartidoImagen> lista) {
        return lista.stream().map(PartidoImagen::equipo).toList();
    }

    @Test
    void primerEquipoFilialYVeteranosEntranEnFutbol11() {

        Map<String, List<PartidoImagen>> r = SeleccionImagenesRedes.seleccionar(List.of(
                partido("Primer Equipo", "1", null),
                partido("Filial", "2", null),
                partido("Veteranos", "3", ""),
                partido("Alevín A", "20", "F8")), "PREVIA", Set.of());

        assertThat(nombres(r.get("F11"))).containsExactly("Primer Equipo", "Veteranos", "Filial");
        assertThat(nombres(r.get("F8"))).containsExactly("Alevín A");
        assertThat(r).doesNotContainKey("F5");
    }

    @Test
    void sinPartidosNoHayImagenDeEsaModalidad() {

        Map<String, List<PartidoImagen>> r = SeleccionImagenesRedes.seleccionar(
                List.of(partido("Alevín A", "20", "F8")), "PREVIA", Set.of());

        assertThat(r).containsOnlyKeys("F8");
        assertThat(SeleccionImagenesRedes.seleccionar(List.of(), "PREVIA", Set.of())).isEmpty();
    }

    @Test
    void f5SoloSaleSiHayPartidosDeF5() {

        Map<String, List<PartidoImagen>> r = SeleccionImagenesRedes.seleccionar(List.of(
                partido("Prebenjamín A", "30", "F5"), partido("Cadete A", "10", "F11")), "PREVIA", Set.of());

        assertThat(r.keySet()).containsExactly("F11", "F5");
    }

    @Test
    void lasEscuelasQuedanFuera() {

        Map<String, List<PartidoImagen>> r = SeleccionImagenesRedes.seleccionar(List.of(
                partido("Esc. Prebenjamín", "40", "F5"), partido("Escuela Fútbol 7", "41", "F8"),
                partido("Cadete A", "10", "F11")), "PREVIA", Set.of());

        assertThat(r).containsOnlyKeys("F11");
    }

    @Test
    void laExclusionConfigurableNoDistingueMayusculasNiEspacios() {

        Map<String, List<PartidoImagen>> r = SeleccionImagenesRedes.seleccionar(List.of(
                partido("Primer Equipo", "1", null), partido("Cadete A", "10", "F11"),
                partido("Veteranos", "3", null)),
                "PREVIA", ImagenesRedesServiceImpl.parsearExcluidos(" primer  equipo , VETERANOS ,, "));

        assertThat(nombres(r.get("F11"))).containsExactly("Cadete A");
    }

    @Test
    void sinExclusionesEntranTodos() {

        assertThat(ImagenesRedesServiceImpl.parsearExcluidos("")).isEmpty();
        assertThat(ImagenesRedesServiceImpl.parsearExcluidos(null)).isEmpty();

        Map<String, List<PartidoImagen>> r = SeleccionImagenesRedes.seleccionar(
                List.of(partido("Primer Equipo", "1", null)), "PREVIA", ImagenesRedesServiceImpl.parsearExcluidos(""));

        assertThat(nombres(r.get("F11"))).containsExactly("Primer Equipo");
    }

    @Test
    void ordenDelClubNoPorHoraYNumericoNoDeTexto() {

        PartidoImagen tarde = new PartidoImagen(1L, 1L, "Cadete A", "2", "F11", "X", null, SABADO, "09:00", "C", true,
                false, null, null, null);
        PartidoImagen temprano = new PartidoImagen(2L, 2L, "Juvenil A", "10", "F11", "X", null, SABADO, "08:00", "C",
                true, false, null, null, null);
        PartidoImagen primero = new PartidoImagen(3L, 3L, "Filial", "1", "F11", "X", null, SABADO, "20:00", "C", true,
                false, null, null, null);

        Map<String, List<PartidoImagen>> r = SeleccionImagenesRedes.seleccionar(List.of(temprano, tarde, primero),
                "PREVIA", Set.of());

        // El orden es el fijo del club: no depende de EQUIPO.ORDEN ni de la hora.
        assertThat(nombres(r.get("F11"))).containsExactly("Filial", "Juvenil A", "Cadete A");
    }

    private static List<PartidoImagen> desordenados(String modalidad, String... equipos) {

        List<PartidoImagen> lista = new ArrayList<>();

        // EQUIPO.ORDEN al revés a propósito: no debe influir.
        for (int i = 0; i < equipos.length; i++) {
            lista.add(partido(equipos[i], String.valueOf(100 - i), modalidad));
        }

        java.util.Collections.shuffle(lista, new java.util.Random(7));

        return lista;
    }

    @Test
    void ordenFijoDeFutbol11() {

        List<PartidoImagen> entrada = desordenados("F11", "Infantil B", "Cadete A", "Veteranos", "Juvenil B",
                "Filial", "Infantil A", "Cadete B", "Juvenil A");

        assertThat(nombres(SeleccionImagenesRedes.seleccionar(entrada, "PREVIA", Set.of()).get("F11")))
                .containsExactly("Veteranos", "Filial", "Juvenil A", "Juvenil B", "Cadete A", "Cadete B", "Infantil A",
                        "Infantil B");
    }

    @Test
    void ordenFijoDeFutbol8() {

        List<PartidoImagen> entrada = desordenados("F8", "Prebenjamín B", "Benjamín C", "Alevín B", "Prebenjamín A",
                "Alevín A", "Benjamín A", "Alevín C", "Benjamín B");

        assertThat(nombres(SeleccionImagenesRedes.seleccionar(entrada, "PREVIA", Set.of()).get("F8")))
                .containsExactly("Alevín A", "Alevín B", "Alevín C", "Benjamín A", "Benjamín B", "Benjamín C",
                        "Prebenjamín A", "Prebenjamín B");
    }

    @Test
    void unEquipoNuevoSeColocaPorCategoriaYLetraSinTocarNada() {

        List<PartidoImagen> entrada = desordenados("F8", "Benjamín A", "Alevín D", "Alevín A", "Alevín C");
        List<PartidoImagen> f11 = desordenados("F11", "Cadete C", "Cadete B", "Infantil A", "Cadete A");

        assertThat(nombres(SeleccionImagenesRedes.seleccionar(entrada, "PREVIA", Set.of()).get("F8")))
                .containsExactly("Alevín A", "Alevín C", "Alevín D", "Benjamín A");
        assertThat(nombres(SeleccionImagenesRedes.seleccionar(f11, "PREVIA", Set.of()).get("F11")))
                .containsExactly("Cadete A", "Cadete B", "Cadete C", "Infantil A");
    }

    @Test
    void categoriaYLetraSeComparanSinMayusculasNiAcentos() {

        List<PartidoImagen> entrada = desordenados("F8", "BENJAMIN A", "alevin b", "Alevín A");

        assertThat(nombres(SeleccionImagenesRedes.seleccionar(entrada, "PREVIA", Set.of()).get("F8")))
                .containsExactly("Alevín A", "alevin b", "BENJAMIN A");
    }

    @Test
    void dosPartidosDelMismoEquipoVanJuntosPorDiaYHora() {

        PartidoImagen domingo = new PartidoImagen(1L, 1L, "Cadete A", "10", "F11", "X", null, SABADO.plusDays(1),
                "09:00", "C", true, false, null, null, null);
        PartidoImagen sabadoTarde = new PartidoImagen(2L, 1L, "Cadete A", "10", "F11", "X", null, SABADO, "17:00", "C",
                true, false, null, null, null);
        PartidoImagen sabadoMañana = new PartidoImagen(3L, 1L, "Cadete A", "10", "F11", "X", null, SABADO, "09:00",
                "C", true, false, null, null, null);
        PartidoImagen otro = partido("Cadete B", "1", "F11");
        PartidoImagen juvenil = partido("Juvenil A", "99", "F11");

        List<PartidoImagen> r = SeleccionImagenesRedes
                .seleccionar(List.of(otro, domingo, juvenil, sabadoTarde, sabadoMañana), "PREVIA", Set.of()).get("F11");

        assertThat(r).containsExactly(juvenil, sabadoMañana, sabadoTarde, domingo, otro);
    }

    @Test
    void lasCategoriasFueraDeLaListaVanAlFinalPorNombre() {

        List<PartidoImagen> entrada = desordenados("F11", "Femenino B", "Cadete A", "Amateur", "Femenino A",
                "Primer Equipo");

        assertThat(nombres(SeleccionImagenesRedes.seleccionar(entrada, "PREVIA", Set.of()).get("F11")))
                .containsExactly("Primer Equipo", "Cadete A", "Amateur", "Femenino A", "Femenino B");
    }

    @Test
    void elReparteEnVariasImagenesRespetaElOrden() {

        List<PartidoImagen> entrada = desordenados("F8", "Alevín A", "Alevín B", "Alevín C", "Alevín D", "Benjamín A",
                "Benjamín B", "Benjamín C", "Prebenjamín A", "Prebenjamín B");

        List<List<PartidoImagen>> paginas = SeleccionImagenesRedes
                .paginar(SeleccionImagenesRedes.seleccionar(entrada, "PREVIA", Set.of()).get("F8"));

        assertThat(nombres(paginas.get(0))).containsExactly("Alevín A", "Alevín B", "Alevín C", "Alevín D",
                "Benjamín A");
        assertThat(nombres(paginas.get(1))).containsExactly("Benjamín B", "Benjamín C", "Prebenjamín A",
                "Prebenjamín B");
    }

    @Test
    void losCanceladosSalenEnResultadosPeroNoEnLaPrevia() {

        List<PartidoImagen> partidos = List.of(partido("Cadete A", "10", "F11"), cancelado("Cadete B", "11", "F11"));

        assertThat(nombres(SeleccionImagenesRedes.seleccionar(partidos, "PREVIA", Set.of()).get("F11")))
                .containsExactly("Cadete A");
        assertThat(nombres(SeleccionImagenesRedes.seleccionar(partidos, "RESULTADOS", Set.of()).get("F11")))
                .containsExactly("Cadete A", "Cadete B");
        assertThat(SeleccionImagenesRedes.estado(cancelado("Cadete B", "11", "F11"))).isEqualTo(Estado.APLAZADO);
    }

    @Test
    void paginasEquilibradasSinDejarUnaUltimaConUnoODosPartidos() {

        for (int n = 1; n <= 40; n++) {

            List<Integer> filas = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                filas.add(i);
            }

            List<List<Integer>> paginas = SeleccionImagenesRedes.paginar(filas);

            assertThat(paginas.stream().flatMap(List::stream).toList()).as("orden con %d filas", n).isEqualTo(filas);
            assertThat(paginas).allSatisfy(p -> assertThat(p.size()).isBetween(1, 8));

            if (n > 8) {
                assertThat(paginas.get(paginas.size() - 1).size()).as("última con %d filas", n).isGreaterThanOrEqualTo(4);
            }

            int max = paginas.stream().mapToInt(List::size).max().getAsInt();
            int min = paginas.stream().mapToInt(List::size).min().getAsInt();

            assertThat(max - min).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void nuevePartidosSonCincoMasCuatro() {

        List<Integer> filas = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertThat(SeleccionImagenesRedes.paginar(filas)).containsExactly(List.of(1, 2, 3, 4, 5), List.of(6, 7, 8, 9));
        assertThat(SeleccionImagenesRedes.paginar(filas.subList(0, 8))).hasSize(1);
        assertThat(SeleccionImagenesRedes.paginar(List.of())).isEmpty();
    }

    @Test
    void rivalConLaLetraDelEquipoEntreComillas() {

        assertThat(SeleccionImagenesRedes.lineaRival("CD Betis Florida B")).isEqualTo("VS CD BETIS FLORIDA “B”");
        assertThat(SeleccionImagenesRedes.lineaRival("Villajoyosa CF \"B\"")).isEqualTo("VS VILLAJOYOSA CF “B”");
        assertThat(SeleccionImagenesRedes.lineaRival("Elda Unión “E”")).isEqualTo("VS ELDA UNIÓN “E”");
        assertThat(SeleccionImagenesRedes.lineaRival("Pego C.F.")).isEqualTo("VS PEGO C.F.");
        assertThat(SeleccionImagenesRedes.lineaRival("SCD Carolinas a")).isEqualTo("VS SCD CAROLINAS “A”");
    }

    @Test
    void horaYMarcador() {

        assertThat(SeleccionImagenesRedes.hora("19:15")).isEqualTo("19:15 H");
        assertThat(SeleccionImagenesRedes.hora(" ")).isEqualTo("-- H");

        PartidoImagen local = new PartidoImagen(1L, 1L, "A", "1", "F11", "R", null, SABADO, "10:00", "C", true, false, 3,
                1, null);
        PartidoImagen visitante = new PartidoImagen(1L, 1L, "A", "1", "F11", "R", null, SABADO, "10:00", "C", false,
                false, 3, 1, null);

        assertThat(SeleccionImagenesRedes.marcador(local)).isEqualTo("3 - 1");
        assertThat(SeleccionImagenesRedes.marcador(visitante)).isEqualTo("1 - 3");
        assertThat(SeleccionImagenesRedes.estado(local)).isEqualTo(Estado.VICTORIA);
        assertThat(SeleccionImagenesRedes.estado(visitante)).isEqualTo(Estado.VICTORIA);
    }

    @Test
    void estadosDeUnPartido() {

        PartidoImagen empate = new PartidoImagen(1L, 1L, "A", "1", "F11", "R", null, SABADO, "10:00", "C", true, false,
                2, 2, null);
        PartidoImagen derrota = new PartidoImagen(1L, 1L, "A", "1", "F11", "R", null, SABADO, "10:00", "C", true, false,
                0, 1, null);

        assertThat(SeleccionImagenesRedes.estado(empate)).isEqualTo(Estado.EMPATE);
        assertThat(SeleccionImagenesRedes.estado(derrota)).isEqualTo(Estado.DERROTA);
        assertThat(SeleccionImagenesRedes.estado(partido("A", "1", "F11"))).isEqualTo(Estado.SIN_RESULTADO);
        assertThat(SeleccionImagenesRedes.marcador(partido("A", "1", "F11"))).isNull();
    }

    @Test
    void resultadoEscritoComoTextoSeAprovecha() {

        PartidoImagen texto = new PartidoImagen(1L, 1L, "Cadete A", "10", "F11", "R", null, SABADO, "10:00", "C", true,
                false, null, null, "2-1");

        PartidoImagen elegido = SeleccionImagenesRedes.seleccionar(List.of(texto), "RESULTADOS", Set.of()).get("F11")
                .get(0);

        assertThat(SeleccionImagenesRedes.marcador(elegido)).isEqualTo("2 - 1");
        assertThat(SeleccionImagenesRedes.estado(elegido)).isEqualTo(Estado.VICTORIA);
    }
}
