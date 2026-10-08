package com.mikedev.mutxamelcf.mvc.communication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComunicacionesServiceTest {

    private JavaMailSender emailSender;
    private ComunicacionesService service;

    @BeforeEach
    void setUp() {
        emailSender = mock(JavaMailSender.class);
        service = new ComunicacionesService(emailSender);
        when(emailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    @Test
    void enviarPedidoTiendaDevuelveTrueCuandoElEnvioTieneExito() {
        assertTrue(service.enviarPedidoTienda("Ana", "ana@example.com", "contenido del pedido"));
    }

    @Test
    void enviarPedidoTiendaDevuelveFalseSiElEnvioFalla() {
        doThrow(new org.springframework.mail.MailSendException("fallo smtp")).when(emailSender).send(
                org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertFalse(service.enviarPedidoTienda("Ana", "ana@example.com", "contenido"));
    }

    @Test
    void enviarMensajeContactoDevuelveTrueCuandoElEnvioTieneExito() {
        assertTrue(service.enviarMensajeContacto("Ana", "ana@example.com", "Hola, tengo una duda"));
    }

    @Test
    void enviarInvitacionAppSaludaConElNombreDeLaPersona() {
        assertTrue(service.enviarInvitacionApp("familia@example.com", "Ana", "123456"));
    }

    @Test
    void enviarInvitacionAppUsaSaludoGenericoSiNoHayNombre() {
        assertTrue(service.enviarInvitacionApp("familia@example.com", null, "123456"));
        assertTrue(service.enviarInvitacionApp("familia@example.com", "  ", "123456"));
    }

    @Test
    void enviarInvitacionAppDevuelveFalseSiElEnvioFalla() {
        doThrow(new RuntimeException("fallo smtp")).when(emailSender).send(
                org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertFalse(service.enviarInvitacionApp("familia@example.com", "Ana", "123456"));
    }

    @Test
    void enviarCuentaActivadaDevuelveTrueCuandoElEnvioTieneExito() {
        assertTrue(service.enviarCuentaActivada("ana@example.com", "Ana", java.util.List.of("JUGADOR")));
    }

    @Test
    void enviarCuentaActivadaDevuelveFalseSiElEnvioFalla() {
        doThrow(new RuntimeException("fallo smtp")).when(emailSender).send(
                org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertFalse(service.enviarCuentaActivada("ana@example.com", "Ana", java.util.List.of("JUGADOR")));
    }

    @Test
    void cuentaActivadaVaEnCastellanoYDespuesEnValencianoSeparados() {
        String texto = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("JUGADOR"));

        int separador = texto.indexOf("------------------------------");
        int castellano = texto.indexOf("Tu cuenta de la app oficial");
        int valenciano = texto.indexOf("El teu compte de l'app oficial");

        assertTrue(castellano >= 0 && castellano < separador);
        assertTrue(separador < valenciano);
        assertTrue(texto.startsWith("Hola Ana:"));
    }

    @Test
    void cuentaActivadaIncluyeSoloLosBloquesDeLosRolesDeLaCuenta() {
        String texto = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("FAMILIAR"));

        assertTrue(texto.contains("Como familiar"));
        assertTrue(texto.contains("Com a familiar"));
        assertFalse(texto.contains("Como entrenador"));
        assertFalse(texto.contains("Como jugador"));
        assertFalse(texto.contains("Como coordinador"));
        assertFalse(texto.contains("retransmisión"));
        assertFalse(texto.toLowerCase().contains("cuota"));
    }

    @Test
    void cuentaActivadaConVariosRolesIncluyeTodosSusBloques() {
        String texto = ComunicacionesService.construirCuentaActivada(
                "Ana", java.util.List.of("ENTRENADOR", "FAMILIAR", "RETRANSMISION"));

        assertTrue(texto.contains("Como entrenador"));
        assertTrue(texto.contains("Como familiar"));
        assertTrue(texto.contains("Como responsable de retransmisión"));
        assertTrue(texto.contains("Com a responsable de retransmissió"));
    }

    @Test
    void cuentaActivadaTratandoCoordinadorYAdminComoUnSoloBloque() {
        String texto = ComunicacionesService.construirCuentaActivada(
                "Ana", java.util.List.of("COORDINADOR", "ADMIN_APP"));

        assertTrue(texto.contains("Como coordinador"));
        assertTrue(texto.indexOf("Como coordinador") == texto.lastIndexOf("Como coordinador"));
    }

    @Test
    void cuentaActivadaSaludaDeFormaGenericaSinNombre() {
        assertTrue(ComunicacionesService.construirCuentaActivada(null, java.util.List.of()).startsWith("Hola:"));
        assertTrue(ComunicacionesService.construirCuentaActivada("  ", null).startsWith("Hola:"));
    }

    @Test
    void enviarWhatsappSiempreDevuelveFalse() {
        assertFalse(service.enviarWhatsapp("600000000", "Hola"));
    }

    @Test
    void enviarNotificacionMovilSiempreDevuelveFalse() {
        assertFalse(service.enviarNotificacionMovil("dispositivo-1", "Titulo", "Mensaje"));
    }
}
