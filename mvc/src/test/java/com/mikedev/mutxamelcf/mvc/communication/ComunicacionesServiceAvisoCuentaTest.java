package com.mikedev.mutxamelcf.mvc.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;

class ComunicacionesServiceAvisoCuentaTest {

    @Test
    void elAvisoSaleEnElIdiomaDeLaCuentaConLaFechaDeDesactivacion() {
        LocalDate fecha = LocalDate.of(2027, 6, 10);

        assertThat(ComunicacionesService.construirAvisoCuentaSinJugadores("Ana", fecha, "es"))
                .startsWith("Hola Ana:")
                .contains("se desactivará el 10/06/2027")
                .contains("mutxamelcf.gestion@gmail.com")
                .doesNotContain("------------------------------")
                .doesNotContain("es desactivarà");
        assertThat(ComunicacionesService.construirAvisoCuentaSinJugadores("Ana", fecha, "ca"))
                .contains("es desactivarà el 10/06/2027")
                .doesNotContain("se desactivará");
        assertThat(ComunicacionesService.construirAvisoCuentaSinJugadores("Ana", fecha, "en"))
                .startsWith("Hello Ana:")
                .contains("deactivated on 10/06/2027");
    }

    @Test
    void sinNombreSaludaSinNombre() {
        assertThat(ComunicacionesService.construirAvisoCuentaSinJugadores(null, LocalDate.of(2027, 6, 10), "es"))
                .startsWith("Hola:");
    }

    @Test
    void elEnvioUsaElAsuntoDelIdiomaYElDestinatario() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mensaje = new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
        when(sender.createMimeMessage()).thenReturn(mensaje);

        boolean enviado = new ComunicacionesService(sender).enviarAvisoCuentaSinJugadores("ana@x.es", "Ana",
                LocalDate.of(2027, 6, 10), "ca");

        assertThat(enviado).isTrue();
        assertThat(mensaje.getSubject()).isEqualTo("El teu compte de l'app del Mutxamel CF es desactivarà");
        verify(sender).send(mensaje);
    }
}
