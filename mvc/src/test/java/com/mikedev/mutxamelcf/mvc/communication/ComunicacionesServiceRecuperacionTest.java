package com.mikedev.mutxamelcf.mvc.communication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComunicacionesServiceRecuperacionTest {

    @Test
    void elCorreoDelCodigoVaEnCastellanoYValencianoConCodigoCaducidadYAvisoDeIgnorar() {
        String texto = ComunicacionesService.construirCodigoRecuperacion("Ana", "042517", 30);

        assertThat(texto).contains("Hola Ana:");
        assertThat(texto).contains("042517");
        assertThat(texto).contains("caduca en 30 minutos");
        assertThat(texto).contains("Si no lo has pedido tú, ignora este mensaje");
        assertThat(texto).contains("caduca en 30 minuts");
        assertThat(texto).contains("Si no ho has demanat tu, ignora aquest missatge");
    }

    @Test
    void elCorreoDeConfirmacionVaEnLosDosIdiomasYSinNombreSaludaSinNombre() {
        String texto = ComunicacionesService.construirContrasenaCambiada(null);

        assertThat(texto).startsWith("Hola:");
        assertThat(texto).contains("Tu contraseña se ha cambiado");
        assertThat(texto).contains("La teua contrasenya s'ha canviat");
    }
}
