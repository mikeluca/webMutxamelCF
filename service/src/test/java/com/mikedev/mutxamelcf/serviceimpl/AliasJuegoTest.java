package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Las reglas del alias del ranking del juego de Mutxigol. */
class AliasJuegoTest {

    private final AliasJuego alias = new AliasJuego("");

    @ParameterizedTest
    @ValueSource(strings = { "Mutxigol", "Pepe-Gol", "ñandú_10", "Ana María", "Jugador 7", "ABC", "Mútxi_Gol-10",
            "Vergara", "Particulo", "Calculo", "Pedro" })
    void aliasValidos(String valor) {
        assertThatCode(() -> alias.validar(valor)).doesNotThrowAnyException();
    }

    @Test
    void quitaLosEspaciosDeLosExtremosYLosDobles() {
        assertThat(alias.validar("  Ana   María ")).isEqualTo("Ana María");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  ", "ab", "Un alias demasiado largo" })
    void longitudDe3A15(String valor) {
        assertThatThrownBy(() -> alias.validar(valor)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lasLongitudesLimiteVan() {
        assertThatCode(() -> alias.validar("abc")).doesNotThrowAnyException();
        assertThatCode(() -> alias.validar("abcdefghijklmno")).doesNotThrowAnyException();
        assertThatThrownBy(() -> alias.validar("a".repeat(16))).hasMessageContaining("entre 3 y 15");
        assertThatThrownBy(() -> alias.validar("ab")).hasMessageContaining("entre 3 y 15");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Pepe!", "Pepe.gol", "Pepe*10", "Pepe😀", "a/b/c", "<b>hola</b>" })
    void caracteresNoPermitidos(String valor) {
        assertThatThrownBy(() -> alias.validar(valor)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letras, números");
    }

    @ParameterizedTest
    @ValueSource(strings = { "pepe@gmail", "a@b", "pepe arroba", "pepe punto com", "mi gmail", "HOTMAIL" })
    void noPuedeParecerUnEmail(String valor) {
        assertThatThrownBy(() -> alias.validar(valor)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @ParameterizedTest
    @ValueSource(strings = { "612345678", "612 345 678", "6123-45", "tel 612345", "123456" })
    void noPuedeParecerUnTelefono(String valor) {
        assertThatThrownBy(() -> alias.validar(valor)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teléfono");
    }

    @Test
    void cincoCifrasSiValen() {
        assertThatCode(() -> alias.validar("Mutxi12345")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "puta", "PUTA", "Pút4", "p u t a", "p-u-t-a", "pu7a", "gilipollas",
            "Soy_GILIPOLLAS", "mierda", "m1erda", "Joder", "Cabrón", "COÑO", "culo", "mi culo", "nazi",
            "Hitler10" })
    void palabrasNoPermitidas(String valor) {
        assertThatThrownBy(() -> alias.validar(valor)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está permitido");
    }

    @Test
    void lasPalabrasLargasSeBuscanDentroYLasCortasSoloEnteras() {
        // "polla" (5 letras) y "culo" (4) solo como palabra entera: no saltan en Particulo, Calculo ni Pollastre
        assertThatCode(() -> alias.validar("Particulo")).doesNotThrowAnyException();
        assertThatCode(() -> alias.validar("Calculo")).doesNotThrowAnyException();
        assertThatCode(() -> alias.validar("Pollastre")).doesNotThrowAnyException();
        // "mierda" (6) salta aunque vaya pegada
        assertThatThrownBy(() -> alias.validar("Sumierda")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laClaveDeUnicidadIgnoraMayusculasYAcentosYEspaciosDeMas() {
        assertThat(AliasJuego.normalizar("Mútxi")).isEqualTo("MUTXI");
        assertThat(AliasJuego.normalizar("  mutxi ")).isEqualTo("MUTXI");
        assertThat(AliasJuego.normalizar("Ana   María")).isEqualTo(AliasJuego.normalizar("ANA MARIA"));
        assertThat(AliasJuego.normalizar("Ñandú")).isEqualTo("NANDU");
        assertThat(AliasJuego.normalizar(null)).isNull();
    }

    @Test
    void laListaSeAmpliaConUnFicheroSinRecompilarNiReiniciar(@TempDir Path carpeta) throws Exception {

        Path extra = carpeta.resolve("extra.txt");
        AliasJuego conExtra = new AliasJuego(extra.toString());

        // Sin fichero: solo la lista básica
        assertThatCode(() -> conExtra.validar("Trolero")).doesNotThrowAnyException();

        Files.writeString(extra, "# mis palabras\ntrolero\n\nbanana split\n");
        Files.setLastModifiedTime(extra, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        assertThatThrownBy(() -> conExtra.validar("Trolero")).hasMessageContaining("no está permitido");
        assertThatThrownBy(() -> conExtra.validar("Banana Split")).hasMessageContaining("no está permitido");
        assertThatCode(() -> conExtra.validar("Mutxigol")).doesNotThrowAnyException();

        // Al quitar la palabra y cambiar el fichero, se vuelve a leer
        Files.writeString(extra, "otra\n");
        Files.setLastModifiedTime(extra, FileTime.fromMillis(System.currentTimeMillis() + 10_000));

        assertThatCode(() -> conExtra.validar("Trolero")).doesNotThrowAnyException();
        assertThatThrownBy(() -> conExtra.validar("Otra cosa")).hasMessageContaining("no está permitido");
    }
}
