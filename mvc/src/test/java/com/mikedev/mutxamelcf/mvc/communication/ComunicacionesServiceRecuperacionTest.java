package com.mikedev.mutxamelcf.mvc.communication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComunicacionesServiceRecuperacionTest {

    @Test
    void elCorreoDelCodigoVaEnUnSoloIdiomaConCodigoCaducidadYAvisoDeIgnorar() {
        String es = ComunicacionesService.construirCodigoRecuperacion("Ana", "042517", 30, "es");
        String ca = ComunicacionesService.construirCodigoRecuperacion("Ana", "042517", 30, "ca");
        String en = ComunicacionesService.construirCodigoRecuperacion("Ana", "042517", 30, "en");

        assertThat(es).contains("Hola Ana:").contains("042517").contains("caduca en 30 minutos")
                .contains("Si no lo has pedido tú, ignora este mensaje")
                .doesNotContain("caduca en 30 minuts").doesNotContain("------------------------------");
        assertThat(ca).contains("042517").contains("caduca en 30 minuts")
                .contains("Si no ho has demanat tu, ignora aquest missatge").doesNotContain("Si no lo has pedido");
        assertThat(en).startsWith("Hello Ana:").contains("042517").contains("expires in 30 minutes")
                .contains("If it wasn't you, ignore this message").doesNotContain("{");
    }

    @Test
    void elCorreoDeConfirmacionVaEnElIdiomaDeLaCuentaYSinNombreSaludaSinNombre() {
        String es = ComunicacionesService.construirContrasenaCambiada(null, "es");

        assertThat(es).startsWith("Hola:").contains("Tu contraseña se ha cambiado")
                .doesNotContain("La teua contrasenya").doesNotContain("------------------------------");
        assertThat(ComunicacionesService.construirContrasenaCambiada(null, "ca"))
                .contains("La teua contrasenya s'ha canviat").doesNotContain("Tu contraseña");
        assertThat(ComunicacionesService.construirContrasenaCambiada("Ana", "en"))
                .startsWith("Hello Ana:").contains("Your password has been changed");
    }
}
