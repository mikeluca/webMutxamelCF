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
    void enviarWhatsappSiempreDevuelveFalse() {
        assertFalse(service.enviarWhatsapp("600000000", "Hola"));
    }

    @Test
    void enviarNotificacionMovilSiempreDevuelveFalse() {
        assertFalse(service.enviarNotificacionMovil("dispositivo-1", "Titulo", "Mensaje"));
    }
}
