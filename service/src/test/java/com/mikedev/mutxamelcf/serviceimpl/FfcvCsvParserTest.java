package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.model.FfcvFilaCsv;
import com.mikedev.mutxamelcf.model.FfcvParseResult;

class FfcvCsvParserTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);

    private static byte[] ejemplo() throws IOException {
        try (InputStream in = FfcvCsvParserTest.class.getResourceAsStream("/ffcv/LstPartidos_ejemplo.csv")) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    private static FfcvFilaCsv fila(List<FfcvFilaCsv> filas, String competicion, String letra) {
        return filas.stream()
                .filter(f -> f.competicion().equals(competicion) && java.util.Objects.equals(f.letra(), letra))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay fila para " + competicion + " " + letra));
    }

    private static String csv(String... lineas) {
        return String.join("\r\n", lineas);
    }

    private static final String CABECERA = ";Campo;Dirección Campo;Hora;Jornada;Árbitro;Resul.;Observaciones";

    // ---- CSV de ejemplo -------------------------------------------------

    @Test
    void elEjemploTieneQuincePartidosEnDosDiasSinErrores() throws IOException {
        FfcvParseResult resultado = FfcvCsvParser.parsear(ejemplo());

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.filas()).hasSize(15);
        assertThat(resultado.filas().stream().map(FfcvFilaCsv::fecha).distinct())
                .containsExactlyInAnyOrder(SABADO, DOMINGO);
        assertThat(resultado.filas().stream().filter(f -> f.fecha().equals(SABADO))).hasSize(10);
        assertThat(resultado.filas().stream().filter(f -> f.fecha().equals(DOMINGO))).hasSize(5);
    }

    @Test
    void elEjemploDevuelveLasQuinceCompeticionesNormalizadasConSuLetra() throws IOException {
        List<FfcvFilaCsv> filas = FfcvCsvParser.parsear(ejemplo()).filas();

        List<String> claves = filas.stream()
                .map(f -> f.competicion() + " | " + (f.letra() == null ? "" : f.letra()))
                .collect(Collectors.toList());

        assertThat(claves).containsExactlyInAnyOrder(
                "Primera FFCV, Grup 4 | ",
                "Segona FFCV, Grup 7 | B",
                "Segona FFCV Juvenil, Grup 7 | A",
                "Tercera FFCV Juvenil, Grup 19 | B",
                "Primera Cadet, Grup 10 | A",
                "Segona Cadet Alacant, Grup 3 | B",
                "Primera Infantil, Grup 10 | A",
                "Segona Infantil Alacant, Grup 3 | B",
                "Primera FFCV Aleví 2n. any Alacant, Grup 1 | A",
                "Segona FFCV Aleví 1r. any Alacant, F.C. - Grup 5 | B",
                "Segona FFCV Aleví 2n. any Alacant, F.C. - Grup 6 | C",
                "Primera FFCV Benjamí 2n. any Alacant, Grup 1 | A",
                "Segona FFCV Benjamí 1r. any Alacant, F.C. - Grup 6 | B",
                "Segona FFCV Prebenjamí 2n. any Alacant, F.C. - Grup 2 | A",
                "Segona FFCV Prebenjamí 1r. any Alacant, F.C. - Grup 2 | B");
    }

    @Test
    void pegoContraMutxamelEsVisitanteSinLetra() throws IOException {
        FfcvFilaCsv pego = fila(FfcvCsvParser.parsear(ejemplo()).filas(), "Primera FFCV, Grup 4", null);

        assertThat(pego.fecha()).isEqualTo(DOMINGO);
        assertThat(pego.local()).isFalse();
        assertThat(pego.letra()).isNull();
        assertThat(pego.rival()).isEqualTo("Pego C.F.");
        assertThat(pego.campo()).isEqualTo("Campo Mpal. Cervantes F-11 Pego");
        assertThat(pego.hora()).isEqualTo("17:00");
        assertThat(pego.jornada()).isEqualTo(6);
    }

    @Test
    void mutxamelBLocalConRivalConSuPropiaLetra() throws IOException {
        FfcvFilaCsv filial = fila(FfcvCsvParser.parsear(ejemplo()).filas(), "Segona FFCV, Grup 7", "B");

        assertThat(filial.fecha()).isEqualTo(SABADO);
        assertThat(filial.local()).isTrue();
        assertThat(filial.rival()).isEqualTo("C.F.U.D. Calpe \"B\"");
        assertThat(filial.campo()).isEqualTo("Camp Nou Los Olmos Mutxamel F-11");
        assertThat(filial.hora()).isEqualTo("19:30");
        assertThat(filial.jornada()).isEqualTo(4);
    }

    @Test
    void mutxamelVisitanteConLetraYRivalTalCualVieneEnElCsv() throws IOException {
        FfcvFilaCsv alevin = fila(FfcvCsvParser.parsear(ejemplo()).filas(),
                "Primera FFCV Aleví 2n. any Alacant, Grup 1", "A");

        assertThat(alevin.local()).isFalse();
        assertThat(alevin.rival()).isEqualTo("Club de Futbol Fenix Alicante \"A\"");
        assertThat(alevin.campo()).isEqualTo("Campo Mpal. Virgen del Remedio F-8 Alicante");
        assertThat(alevin.hora()).isEqualTo("11:30");
    }

    @Test
    void elCampoEsElNombreNoLaDireccion() throws IOException {
        assertThat(FfcvCsvParser.parsear(ejemplo()).filas())
                .allSatisfy(f -> assertThat(f.campo()).doesNotContain("Calle ").doesNotContain(", Alicante"));
    }

    @Test
    void elArbitroNoSeGuardaEnNingunaFila() throws IOException {
        FfcvParseResult resultado = FfcvCsvParser.parsear(ejemplo());

        assertThat(resultado.filas().toString()).doesNotContain("rbitro Ejemplo");
        assertThat(resultado.errores().toString()).doesNotContain("rbitro Ejemplo");
    }

    // ---- Codificación ----------------------------------------------------

    @Test
    void sinBomSeLeeComoWindows1252() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera FFCV Aleví, Grup - 1" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Racing Alacant;Camp Nou;Calle Camí Vell;10:00 ;1;Árbitro Ñ  ; - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto.getBytes(Charset.forName("windows-1252")));

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.filas()).singleElement()
                .satisfies(f -> assertThat(f.competicion()).isEqualTo("Primera FFCV Aleví, Grup 1"));
    }

    @Test
    void conBomUtf8SeLeeComoUtf8() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera FFCV Aleví, Grup - 1" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Racing Alacant;Camp Nou;Calle Camí Vell;10:00 ;1;Árbitro;  - ;");

        byte[] utf8 = texto.getBytes(StandardCharsets.UTF_8);
        byte[] conBom = new byte[utf8.length + 3];
        conBom[0] = (byte) 0xEF;
        conBom[1] = (byte) 0xBB;
        conBom[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, conBom, 3, utf8.length);

        FfcvParseResult resultado = FfcvCsvParser.parsear(conBom);

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.filas()).singleElement()
                .satisfies(f -> assertThat(f.competicion()).isEqualTo("Primera FFCV Aleví, Grup 1"));
    }

    // ---- Estructura ------------------------------------------------------

    @Test
    void ignoraLineasVaciasEspaciosYElTituloDelListado() {
        String texto = csv(
                "Listado de Partidos",
                " ",
                "",
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Rival;Campo X;Dir;09:00 ;3;Ar;  - ;",
                " ",
                "   ");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.filas()).hasSize(1);
    }

    @Test
    void aceptaFinesDeLineaLf() {
        String texto = "  17-10-2026, (Sábado)\n Primera Cadet, Grup - 10" + CABECERA
                + "\nMutxamel C.F. \"A\"  -  Rival;Campo X;Dir;09:00 ;3;Ar;  - ;\n";

        assertThat(FfcvCsvParser.parsear(texto).filas()).hasSize(1);
    }

    @Test
    void cadaPartidoTomaLaFechaYLaCompeticionDeSuBloque() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Rival 1;Campo X;Dir;09:00 ;3;Ar;  - ;",
                "Mutxamel C.F. \"B\"  -  Rival 2;Campo Y;Dir;11:00 ;3;Ar;  - ;",
                " ",
                "  18-10-2026, (Domingo)",
                " Segona Infantil Alacant, Grup - 3" + CABECERA,
                "Rival 3  -  Mutxamel C.F. \"B\";Campo Z;Dir;12:00 ;4;Ar;  - ;");

        List<FfcvFilaCsv> filas = FfcvCsvParser.parsear(texto).filas();

        assertThat(filas).extracting(FfcvFilaCsv::fecha).containsExactly(SABADO, SABADO, DOMINGO);
        assertThat(filas).extracting(FfcvFilaCsv::competicion)
                .containsExactly("Primera Cadet, Grup 10", "Primera Cadet, Grup 10",
                        "Segona Infantil Alacant, Grup 3");
        assertThat(filas).extracting(FfcvFilaCsv::letra).containsExactly("A", "B", "B");
        assertThat(filas).extracting(FfcvFilaCsv::local).containsExactly(true, true, false);
    }

    @Test
    void elResultadoDelCsvSeIgnora() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Rival;Campo X;Dir;09:00 ;3;Ar;3 - 1;");

        FfcvFilaCsv fila = FfcvCsvParser.parsear(texto).filas().get(0);

        assertThat(fila.toString()).doesNotContain("3 - 1");
    }

    @Test
    void horaVaciaSeConvierteEnNullYLaJornadaVaciaTambien() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Rival;Campo X;Dir; ; ;Ar;  - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.filas().get(0).hora()).isNull();
        assertThat(resultado.filas().get(0).jornada()).isNull();
    }

    @Test
    void unEquipoDelMutxamelContraOtroGeneraUnaFilaPorEquipo() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Mutxamel C.F. \"B\";Campo X;Dir;09:00 ;3;Ar;  - ;");

        List<FfcvFilaCsv> filas = FfcvCsvParser.parsear(texto).filas();

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).letra()).isEqualTo("A");
        assertThat(filas.get(0).local()).isTrue();
        assertThat(filas.get(0).rival()).isEqualTo("Mutxamel C.F. \"B\"");
        assertThat(filas.get(1).letra()).isEqualTo("B");
        assertThat(filas.get(1).local()).isFalse();
        assertThat(filas.get(1).rival()).isEqualTo("Mutxamel C.F. \"A\"");
    }

    @Test
    void unRivalConNombreParecidoNoSeConfundeConElMutxamel() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. Juvenil  -  Otro C.F.;Campo X;Dir;09:00 ;3;Ar;  - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.filas()).isEmpty();
        assertThat(resultado.errores()).hasSize(1);
    }

    // ---- Errores ---------------------------------------------------------

    @Test
    void siNingunLadoEsMutxamelLaFilaSeReportaComoErrorConSuNumeroDeLinea() {
        String texto = csv(
                "Listado de Partidos",
                " ",
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Otro C.F.  -  Y C.F.;Campo X;Dir;09:00 ;3;Árbitro Secreto;  - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.filas()).isEmpty();
        assertThat(resultado.errores()).singleElement().satisfies(error -> {
            assertThat(error.linea()).isEqualTo(5);
            assertThat(error.mensaje()).contains("Mutxamel");
            assertThat(error.mensaje()).doesNotContain("Secreto");
        });
    }

    @Test
    void filaSinSeparadorLocalVisitanteEsError() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\" - Rival con un solo espacio;Campo X;Dir;09:00 ;3;Ar;  - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.filas()).isEmpty();
        assertThat(resultado.errores()).singleElement().satisfies(e -> assertThat(e.linea()).isEqualTo(3));
    }

    @Test
    void partidoAntesDeCualquierFechaOCompeticionEsError() {
        String texto = csv("Mutxamel C.F. \"A\"  -  Rival;Campo X;Dir;09:00 ;3;Ar;  - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.filas()).isEmpty();
        assertThat(resultado.errores()).singleElement().satisfies(e -> assertThat(e.linea()).isEqualTo(1));
    }

    @Test
    void horaOJornadaNoValidasSonError() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Rival;Campo X;Dir;mañana;3;Ar;  - ;",
                "Mutxamel C.F. \"B\"  -  Rival;Campo X;Dir;10:00;tres;Ar;  - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.filas()).isEmpty();
        assertThat(resultado.errores()).extracting(e -> e.linea()).containsExactly(3, 4);
    }

    @Test
    void filaConPocasColumnasEsError() {
        String texto = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Rival;Campo X");

        assertThat(FfcvCsvParser.parsear(texto).errores()).hasSize(1);
    }

    @Test
    void fechaImposibleEsErrorYLosPartidosDeEseBloqueTambien() {
        String texto = csv(
                "  32-13-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                "Mutxamel C.F. \"A\"  -  Rival;Campo X;Dir;09:00 ;3;Ar;  - ;");

        FfcvParseResult resultado = FfcvCsvParser.parsear(texto);

        assertThat(resultado.filas()).isEmpty();
        assertThat(resultado.errores()).extracting(e -> e.linea()).containsExactly(1, 3);
    }

    @Test
    void archivoVacioONuloNoDevuelveNada() {
        assertThat(FfcvCsvParser.parsear(new byte[0]).filas()).isEmpty();
        assertThat(FfcvCsvParser.parsear((byte[]) null).filas()).isEmpty();
        assertThat(FfcvCsvParser.parsear("").errores()).isEmpty();
    }

    // ---- Normalización de la competición --------------------------------

    @Test
    void normalizarCompeticion() {
        assertThat(FfcvCsvParser.normalizarCompeticion(" Segona FFCV\t, Grup - 7"))
                .isEqualTo("Segona FFCV, Grup 7");
        assertThat(FfcvCsvParser.normalizarCompeticion(" Segona FFCV Aleví 1r. any Alacant , F.C. - Grup 5"))
                .isEqualTo("Segona FFCV Aleví 1r. any Alacant, F.C. - Grup 5");
        assertThat(FfcvCsvParser.normalizarCompeticion("Segona   FFCV  Prebenjamí 1r. any Alacant, F.C. - Grup 2 "))
                .isEqualTo("Segona FFCV Prebenjamí 1r. any Alacant, F.C. - Grup 2");
        assertThat(FfcvCsvParser.normalizarCompeticion("Tercera FFCV Juvenil, Grup - 19"))
                .isEqualTo("Tercera FFCV Juvenil, Grup 19");
        assertThat(FfcvCsvParser.normalizarCompeticion(null)).isEmpty();
    }

    @Test
    void normalizarEsIdempotente() {
        String una = FfcvCsvParser.normalizarCompeticion(" Primera FFCV Benjamí 2n. any Alacant , Grup - 1 ");

        assertThat(FfcvCsvParser.normalizarCompeticion(una)).isEqualTo(una);
    }

}
