package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CampoCartelTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Campo Mpal. Les Costeres F-11 Gata|Les Costeres Gata",
            "Campo de Fútbol Municipal El Pla F-11|El Pla",
            "Campo de Futbol La Murta F-8|La Murta",
            "Campo Municipal Pepico Amat F11 C.A.|Pepico Amat",
            "Camp Municipal Les Eres F-7|Les Eres",
            "Camp de Futbol Sant Joan F-11|Sant Joan",
            "Estadio Municipal Rico Pérez césped artificial|Rico Pérez",
            "Estadio Municipal Pedro Ferrándiz F-11 C.A.|Pedro Ferrándiz",
            "CAMPO MPAL. EL SALADAR F-11|EL SALADAR",
            "Campo   Municipal    La   Foia   F-8|La Foia",
            "Ciudad Deportiva Muchamiel F-11|Ciudad Deportiva Muchamiel",
            "Les Costeres|Les Costeres" })
    void recortaPrefijosEIdentificadoresDeTerreno(String original, String esperado) {
        assertThat(CampoCartel.recortar(original)).isEqualTo(esperado);
    }

    @Test
    void elEjemploDelEncargoQuedaEnMayusculasComoEnElCartel() {
        assertThat(CampoCartel.paraCartel(false, "Campo Mpal. Les Costeres F-11 Gata")).isEqualTo("LES COSTERES GATA");
    }

    @Test
    void siAlRecortarNoQuedaNadaSeDejaElTextoOriginal() {
        assertThat(CampoCartel.recortar("Campo Municipal")).isEqualTo("Campo Municipal");
        assertThat(CampoCartel.recortar("F-11")).isEqualTo("F-11");
    }

    @Test
    void sinCampoNoHayTexto() {
        assertThat(CampoCartel.recortar(null)).isEmpty();
        assertThat(CampoCartel.recortar("   ")).isEmpty();
        assertThat(CampoCartel.paraCartel(false, null)).isEmpty();
        assertThat(CampoCartel.paraTexto(false, null)).isEmpty();
    }

    @Test
    void enCasaSiempreEsElCampNouLosOlmosSeaCualSeaElCampoDelPartido() {
        assertThat(CampoCartel.paraCartel(true, "Campo cualquiera F-11")).isEqualTo("CAMP NOU\nLOS OLMOS\n(MUTXAMEL)");
        assertThat(CampoCartel.paraCartel(true, null)).isEqualTo("CAMP NOU\nLOS OLMOS\n(MUTXAMEL)");
        assertThat(CampoCartel.paraTexto(true, "otro")).isEqualTo("Camp Nou Los Olmos (Mutxamel)");
    }

    @Test
    void elResultadoNoTieneEspaciosDoblesNiSeparadoresSueltos() {
        assertThat(CampoCartel.recortar("Campo Mpal. - Les Costeres  F-11  -  Gata"))
                .isEqualTo("Les Costeres - Gata").doesNotContain("  ");
    }
}
