package com.mikedev.mutxamelcf.mvc.communication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void enviarRecordatorioImportacionFfcvVaAGestionConElTextoAcordado() throws Exception {
        org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);

        assertTrue(service.enviarRecordatorioImportacionFfcv());

        org.mockito.Mockito.verify(emailSender).send(captor.capture());
        MimeMessage enviado = captor.getValue();
        assertTrue(enviado.getAllRecipients()[0].toString().equals("mutxamelcf.gestion@gmail.com"));
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        enviado.writeTo(salida);
        assertTrue(salida.toString().contains("Recuerda importar los partidos de la jornada desde la intranet de la FFCV"));
    }

    @Test
    void enviarRecordatorioImportacionFfcvDevuelveFalseSiElEnvioFalla() {
        doThrow(new org.springframework.mail.MailSendException("fallo smtp")).when(emailSender).send(
                org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertFalse(service.enviarRecordatorioImportacionFfcv());
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
    void laInvitacionVaEnLosTresIdiomasSeparadosYSaludaConElNombre() {
        String texto = ComunicacionesService.construirInvitacionApp("Ana", "123456");

        int separador = texto.indexOf("------------------------------");
        int castellano = texto.indexOf("Se ha creado tu acceso a la app oficial");
        int valenciano = texto.indexOf("S'ha creat el teu accés a l'app oficial");
        int ingles = texto.indexOf("Your access to the official Mutxamel Club de Futbol app has been created");

        assertTrue(texto.startsWith("Hola Ana,"));
        assertTrue(castellano >= 0 && castellano < separador);
        assertTrue(separador < valenciano && valenciano < ingles);
        assertTrue(texto.contains("Hello Ana,"));
        // El código sale en las tres versiones y los marcadores no quedan sin sustituir.
        assertEquals(3, texto.split("123456", -1).length - 1);
        assertFalse(texto.matches("(?s).*\\{[a-z]+}.*"));
    }

    @Test
    void laInvitacionSinNombreSaludaSinNombre() {
        assertTrue(ComunicacionesService.construirInvitacionApp(null, "123456").startsWith("Hola,"));
        assertTrue(ComunicacionesService.construirInvitacionApp("  ", "123456").contains("Hello,"));
    }

    @Test
    void cuentaActivadaDevuelveTrueCuandoElEnvioTieneExito() {
        assertTrue(service.enviarCuentaActivada("ana@example.com", "Ana", java.util.List.of("JUGADOR"), "es"));
    }

    @Test
    void cuentaActivadaDevuelveFalseSiElEnvioFalla() {
        doThrow(new RuntimeException("fallo smtp")).when(emailSender).send(
                org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertFalse(service.enviarCuentaActivada("ana@example.com", "Ana", java.util.List.of("JUGADOR"), "ca"));
    }

    @Test
    void cuentaActivadaVaSoloEnElIdiomaDeLaCuenta() {
        String es = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("JUGADOR"), "es");
        String ca = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("JUGADOR"), "ca");
        String en = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("JUGADOR"), "en");

        assertTrue(es.startsWith("Hola Ana:"));
        assertTrue(es.contains("Tu cuenta de la app oficial") && !es.contains("El teu compte") && !es.contains("Your "));
        assertTrue(ca.startsWith("Hola Ana:"));
        assertTrue(ca.contains("El teu compte de l'app oficial") && !ca.contains("Tu cuenta") && !ca.contains("Your "));
        assertTrue(en.startsWith("Hello Ana:"));
        assertTrue(en.contains("Your official Mutxamel CF app account") && !en.contains("Tu cuenta")
                && !en.contains("El teu"));
        assertFalse(es.contains("------------------------------"));
        assertFalse(ca.contains("------------------------------"));
        assertFalse(en.contains("------------------------------"));
    }

    @Test
    void cuentaActivadaIncluyeSoloLosBloquesDeLosRolesDeLaCuenta() {
        for (String idioma : java.util.List.of("es", "ca", "en")) {
            String texto = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("FAMILIAR"), idioma);

            assertTrue(texto.contains(titulo(idioma, "FAMILIAR")), idioma);
            assertFalse(texto.contains(titulo(idioma, "ENTRENADOR")), idioma);
            assertFalse(texto.contains(titulo(idioma, "JUGADOR")), idioma);
            assertFalse(texto.contains(titulo(idioma, "COORDINADOR")), idioma);
            assertFalse(texto.contains(titulo(idioma, "RETRANSMISION")), idioma);
            assertFalse(texto.toLowerCase().contains("cuota"));
            assertFalse(texto.matches("(?s).*\\{[a-z]+}.*"), idioma);
        }
    }

    private static String titulo(String idioma, String rol) {
        return switch (idioma) {
            case "ca" -> switch (rol) {
                case "JUGADOR" -> "Com a jugador";
                case "FAMILIAR" -> "Com a familiar";
                case "ENTRENADOR" -> "Com a entrenador";
                case "COORDINADOR" -> "Com a coordinador";
                default -> "Com a responsable de retransmissió";
            };
            case "en" -> switch (rol) {
                case "JUGADOR" -> "As a player";
                case "FAMILIAR" -> "As a family member";
                case "ENTRENADOR" -> "As a coach";
                case "COORDINADOR" -> "As a coordinator";
                default -> "As the live broadcast person";
            };
            default -> switch (rol) {
                case "JUGADOR" -> "Como jugador";
                case "FAMILIAR" -> "Como familiar";
                case "ENTRENADOR" -> "Como entrenador";
                case "COORDINADOR" -> "Como coordinador";
                default -> "Como responsable de retransmisión";
            };
        };
    }

    @Test
    void cuentaActivadaConVariosRolesIncluyeTodosSusBloques() {
        String texto = ComunicacionesService.construirCuentaActivada(
                "Ana", java.util.List.of("ENTRENADOR", "FAMILIAR", "RETRANSMISION"), "es");

        assertTrue(texto.contains("Como entrenador"));
        assertTrue(texto.contains("Como familiar"));
        assertTrue(texto.contains("Como responsable de retransmisión"));
    }

    @Test
    void cuentaActivadaTratandoCoordinadorYAdminComoUnSoloBloque() {
        String texto = ComunicacionesService.construirCuentaActivada(
                "Ana", java.util.List.of("COORDINADOR", "ADMIN_APP"), "es");

        assertTrue(texto.contains("Como coordinador"));
        assertTrue(texto.indexOf("Como coordinador") == texto.lastIndexOf("Como coordinador"));
        assertTrue(ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("ADMIN_APP"), "es")
                .contains("Como coordinador"));
    }

    @Test
    void cuentaActivadaLlevaLasNovedadesDeCadaRol() {
        String jugador = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("JUGADOR"), "es");
        String familiar = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("FAMILIAR"), "es");
        String entrenador = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("ENTRENADOR"), "es");
        String coordinador = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("COORDINADOR"), "es");

        assertTrue(jugador.contains("Si te convocan con otro equipo del club, también lo verás en tu calendario."));
        assertTrue(familiar.contains("Al final de la temporada, la app te preguntará si seguirán en el club."));
        assertTrue(entrenador.contains("Preparas las convocatorias (con el equipaje) y puedes invitar a jugadores"));
        assertTrue(entrenador.contains("Recibes un aviso cuando una familia justifica una falta o una ausencia."));
        assertTrue(coordinador.contains("Apruebas las invitaciones de jugadores a otros equipos"));

        String ingles = ComunicacionesService.construirCuentaActivada("Ana", java.util.List.of("COORDINADOR"), "en");
        assertTrue(ingles.contains("You approve the invitations of players to other teams"));
    }

    @Test
    void cuentaActivadaSaludaDeFormaGenericaSinNombre() {
        assertTrue(ComunicacionesService.construirCuentaActivada(null, java.util.List.of(), "es").startsWith("Hola:"));
        assertTrue(ComunicacionesService.construirCuentaActivada("  ", null, "ca").startsWith("Hola:"));
        assertTrue(ComunicacionesService.construirCuentaActivada(null, null, "en").startsWith("Hello:"));
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
