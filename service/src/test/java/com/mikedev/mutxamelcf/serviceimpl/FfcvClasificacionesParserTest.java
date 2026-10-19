package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.model.FfcvClasificacionesParseResult;
import com.mikedev.mutxamelcf.model.FfcvFilaClasificacion;

class FfcvClasificacionesParserTest {

    @Test
    void leeCabeceraConEspacioDuroYLimpiaLosTextos() {

        byte[] xlsx = new ClasificacionesXlsx()
                // tabulador al final de la competición y espacios sobrantes en el grupo
                .fila("Segona FFCV\t", "Grup - 7 ", "Mutxamel C.F. ", "-", "3010", "5", "2", "10", "5", "3", "1",
                        "1", "9", "4", "12-10-2026")
                .fila("Segona FFCV Aleví 1r. any Alacant  ", "F.C. - Grup 2 ", "Mutxamel C.F.", "B", 3010, 5, 1, 12,
                        5, 4, 0, 1, 14, 6, "11-10-2026")
                .bytes();

        FfcvClasificacionesParseResult resultado = FfcvClasificacionesParser.parsear(xlsx);

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.filas()).hasSize(2);

        FfcvFilaClasificacion primera = resultado.filas().get(0);

        // misma clave que EQUIPOS_FFCV: "Grup - 7" -> "Grup 7", sin tabuladores
        assertThat(primera.competicion()).isEqualTo("Segona FFCV, Grup 7");
        assertThat(primera.equipo()).isEqualTo("Mutxamel C.F.");
        // "-" en la letra significa sin letra
        assertThat(primera.letra()).isNull();
        // números que llegan como texto
        assertThat(primera.club()).isEqualTo(3010);
        assertThat(primera.jornada()).isEqualTo(5);
        assertThat(primera.posicion()).isEqualTo(2);
        assertThat(primera.puntos()).isEqualTo(10);
        assertThat(primera.pj()).isEqualTo(5);
        assertThat(primera.pg()).isEqualTo(3);
        assertThat(primera.pe()).isEqualTo(1);
        assertThat(primera.pp()).isEqualTo(1);
        assertThat(primera.gf()).isEqualTo(9);
        assertThat(primera.gc()).isEqualTo(4);
        assertThat(primera.fechaUltPartido()).isEqualTo(LocalDate.of(2026, 10, 12));

        // números que llegan como numéricos, espacios dobles y "F.C. - Grup 2"
        FfcvFilaClasificacion segunda = resultado.filas().get(1);
        assertThat(segunda.competicion()).isEqualTo("Segona FFCV Aleví 1r. any Alacant, F.C. - Grup 2");
        assertThat(segunda.letra()).isEqualTo("B");
        assertThat(segunda.club()).isEqualTo(3010);
        assertThat(segunda.posicion()).isEqualTo(1);
    }

    @Test
    void laClaveDeGrupoCoincideConLaDeEquiposFfcv() {

        byte[] xlsx = new ClasificacionesXlsx()
                .fila("Primera FFCV", "Grup - 4", "Mutxamel C.F.", "-", 3010, 3, 1, 9, 3, 3, 0, 0, 8, 1, "10-10-2026")
                .bytes();

        assertThat(FfcvClasificacionesParser.parsear(xlsx).filas().get(0).competicion())
                .isEqualTo("Primera FFCV, Grup 4");
    }

    @Test
    void elCodigoDeClubDistingueMutxamelDeMonnegreYBenifutsal() {

        byte[] xlsx = new ClasificacionesXlsx()
                .fila("Primera FFCV", "Grup - 4", "Mutxamel C.F.", "-", 3010, 3, 1, 9, 3, 3, 0, 0, 8, 1, "10-10-2026")
                .fila("Primera FFCV", "Grup - 4", "C.F. Monnegre de Mutxamel", "-", 6574, 3, 2, 6, 3, 2, 0, 1, 5, 3,
                        "10-10-2026")
                .fila("Primera FFCV", "Grup - 4", "Club Mutxamel Benifutsal", "-", 6606, 3, 3, 4, 3, 1, 1, 1, 4, 4,
                        "10-10-2026")
                .bytes();

        FfcvClasificacionesParseResult resultado = FfcvClasificacionesParser.parsear(xlsx);

        // El parser no filtra por club: las tres filas están, cada una con su código.
        assertThat(resultado.filas()).extracting(FfcvFilaClasificacion::club).containsExactly(3010, 6574, 6606);
        assertThat(resultado.filas()).extracting(FfcvFilaClasificacion::equipo)
                .allMatch(nombre -> nombre.contains("Mutxamel"));
        assertThat(resultado.filas().stream().filter(f -> f.club() == 3010)).hasSize(1);
    }

    @Test
    void localizaLasColumnasPorNombreNoPorPosicion() {

        // sin la columna "Delegación" y sin "Ult. Jornada Jugada" todo se desplaza
        byte[] xlsx = new ClasificacionesXlsx()
                .sinColumna("Delegación")
                .sinColumna("Ult. Jornada Jugada")
                .fila("Primera FFCV", "Grup - 4", "Mutxamel C.F.", "-", 3010, 3, 1, 9, 3, 3, 0, 0, 8, 1, "10-10-2026")
                .bytes();

        FfcvFilaClasificacion fila = FfcvClasificacionesParser.parsear(xlsx).filas().get(0);

        assertThat(fila.posicion()).isEqualTo(1);
        assertThat(fila.puntos()).isEqualTo(9);
        assertThat(fila.gf()).isEqualTo(8);
    }

    @Test
    void siFaltaUnaCabeceraElErrorDiceCual() {

        byte[] xlsx = new ClasificacionesXlsx()
                .sinColumna("Goles en contra")
                .sinColumna("Puntos")
                .fila("Primera FFCV", "Grup - 4", "Mutxamel C.F.", "-", 3010, 3, 1, 9, 3, 3, 0, 0, 8, 1, "10-10-2026")
                .bytes();

        assertThatThrownBy(() -> FfcvClasificacionesParser.parsear(xlsx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goles en contra")
                .hasMessageContaining("Puntos");
    }

    @Test
    void unaFilaIlegibleSeReportaYMarcaSuGrupo() {

        byte[] xlsx = new ClasificacionesXlsx()
                .fila("Primera FFCV", "Grup - 4", "Mutxamel C.F.", "-", 3010, 3, 1, 9, 3, 3, 0, 0, 8, 1, "10-10-2026")
                .fila("Primera FFCV", "Grup - 4", "Rival", "-", 1234, 3, "dos", 6, 3, 2, 0, 1, 5, 3, "10-10-2026")
                .bytes();

        FfcvClasificacionesParseResult resultado = FfcvClasificacionesParser.parsear(xlsx);

        assertThat(resultado.filas()).hasSize(1);
        assertThat(resultado.errores()).hasSize(1);
        assertThat(resultado.errores().get(0).mensaje()).contains("Posición");
        assertThat(resultado.gruposConErrores()).containsExactly("Primera FFCV, Grup 4");
    }

    @Test
    void rechazaArchivosQueNoSonXlsx() {

        assertThatThrownBy(() -> FfcvClasificacionesParser.parsear("a;b;c".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");

        assertThatThrownBy(() -> FfcvClasificacionesParser.parsear(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
