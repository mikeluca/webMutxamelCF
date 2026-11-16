package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NombreClubTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "C.D. Alicante 'A'|CD ALICANTE",
            "CD Alicante B|CD ALICANTE",
            "Atlético Mutxamel|ATLETICO MUTXAMEL",
            "C.D. Alicante \"B\"|CD ALICANTE",
            "CD Alicante  C|CD ALICANTE",
            "CD ALICANTE   |CD ALICANTE",
            "C.F. Peñíscola 'A'|CF PENISCOLA",
            "Ç.D. Cañada|CD CANADA",
            "Ñ Club|N CLUB",
            "Vila-real C.F. (B)|VILA REAL CF",
            "Alcoy - D|ALCOY",
            "Atlético ‘Saguntino’ “A”|ATLETICO SAGUNTINO",
            "  U.D.   Altea   |UD ALTEA",
            "Elx C.F.|ELX CF",
            "Mutxamel C.F.|MUTXAMEL CF" })
    void normalizaMayusculasAcentosPuntosComillasGuionesParentesisYElSufijoDeEquipo(String entrada, String esperado) {
        assertThat(NombreClub.normalizar(entrada)).isEqualTo(esperado);
    }

    @Test
    void unaSolaLetraNoSeQuitaSiNoQuedaNingunNombre() {
        assertThat(NombreClub.normalizar("A")).isEqualTo("A");
        assertThat(NombreClub.normalizar("'B'")).isEqualTo("B");
    }

    @Test
    void soloSeQuitaUnaLetraSueltaNoLaFinalDeUnaPalabra() {
        assertThat(NombreClub.normalizar("Club Deportivo Alcoia")).isEqualTo("CLUB DEPORTIVO ALCOIA");
        assertThat(NombreClub.normalizar("Benifato CF B")).isEqualTo("BENIFATO CF");
    }

    @Test
    void nuncaDevuelveNull() {
        assertThat(NombreClub.normalizar(null)).isEmpty();
        assertThat(NombreClub.normalizar("   ")).isEmpty();
        assertThat(NombreClub.nombreVisible(null)).isEmpty();
    }

    @Test
    void esIdempotente() {
        String una = NombreClub.normalizar("C.D. Alicante 'A'");

        assertThat(NombreClub.normalizar(una)).isEqualTo(una);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "C.D. Alicante 'A'|C.D. Alicante",
            "CD Alicante B|CD Alicante",
            "Atlético Mutxamel|Atlético Mutxamel",
            "Villena C.F. \"B\"|Villena C.F.",
            "Orba (C)|Orba",
            "Alcoy - D|Alcoy",
            "  Elx   C.F.  |Elx C.F." })
    void elNombreVisibleConservaElFormatoDelExcelSinLaLetraDelEquipo(String entrada, String esperado) {
        assertThat(NombreClub.nombreVisible(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Mutxamel CF", "Mutxamel C.F.", "C.F. Mutxamel", "CF Mutxamel 'B'", "Mutxamel C.F. B",
            "MUTXAMEL CF INFANTIL", "Mutxamel C.F. Cadete A" })
    void nuestroClubYSusEquiposSonPropios(String nombre) {
        assertThat(NombreClub.esPropio(nombre)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Atlético Mutxamel", "C.F. Monnegre de Mutxamel", "Club Mutxamel Benifutsal",
            "Mutxamelense CF", "Elx C.F.", "" })
    void otrosClubesConMutxamelEnElNombreNoSonPropios(String nombre) {
        assertThat(NombreClub.esPropio(nombre)).isFalse();
    }
}
