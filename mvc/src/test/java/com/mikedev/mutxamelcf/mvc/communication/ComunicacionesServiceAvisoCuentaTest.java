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
    void elAvisoVaEnCastellanoYValencianoConLaFechaDeDesactivacion() {
        String texto = ComunicacionesService.construirAvisoCuentaSinJugadores("Ana", LocalDate.of(2027, 6, 10));

        assertThat(texto).startsWith("Hola Ana:")
                .contains("se desactivará el 10/06/2027")
                .contains("------------------------------")
                .contains("es desactivarà el 10/06/2027")
                .contains("mutxamelcf.gestion@gmail.com");
    }

    @Test
    void sinNombreSaludaSinNombre() {
        assertThat(ComunicacionesService.construirAvisoCuentaSinJugadores(null, LocalDate.of(2027, 6, 10)))
                .startsWith("Hola:");
    }

    @Test
    void elEnvioUsaElAsuntoBilingueYElDestinatario() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mensaje = new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
        when(sender.createMimeMessage()).thenReturn(mensaje);

        boolean enviado = new ComunicacionesService(sender).enviarAvisoCuentaSinJugadores("ana@x.es", "Ana",
                LocalDate.of(2027, 6, 10));

        assertThat(enviado).isTrue();
        verify(sender).send(mensaje);
    }
}
