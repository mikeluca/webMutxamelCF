package com.mikedev.mutxamelcf.mvc.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class ComunicacionesService {

    private static final Logger logger = LoggerFactory.getLogger(ComunicacionesService.class);

    private static final String REMITENTE = "contacto.web@mutxamelcf.es";
    private static final String DESTINATARIO_PEDIDOS = "mutxamelcf.pedidos@gmail.com";
    private static final String DESTINATARIO_CONTACTO = "mutxamelcf.gestion@gmail.com";

    private final JavaMailSender emailSender;

    public ComunicacionesService(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    public boolean enviarPedidoTienda(String nombre, String emailRespuesta, String contenido) {
        logger.debug("Inicio enviarPedidoTienda: nombre={}, emailRespuesta={}", nombre, emailRespuesta);
        boolean enviado = enviarEmail(REMITENTE, DESTINATARIO_PEDIDOS, emailRespuesta,
                "PEDIDO CREADO EN LA WEB", contenido);
        logger.debug("Fin enviarPedidoTienda: enviado={}", enviado);
        return enviado;
    }

    public boolean enviarMensajeContacto(String nombre, String emailRemitente, String mensaje) {
        logger.debug("Inicio enviarMensajeContacto: nombre={}, email={}", nombre, emailRemitente);
        String contenido = "De: " + nombre + "\nEmail: " + emailRemitente + "\n\nMensaje: " + mensaje;
        boolean enviado = enviarEmail(REMITENTE, DESTINATARIO_CONTACTO, emailRemitente,
                "Contacto desde la PAGINA WEB de: " + nombre, contenido);
        logger.debug("Fin enviarMensajeContacto: enviado={}", enviado);
        return enviado;
    }

    public boolean enviarInvitacionApp(String email, String nombrePersona, String codigoActivacion) {
        logger.debug("Inicio enviarInvitacionApp: email={}", email);

        String saludo = (nombrePersona == null || nombrePersona.isBlank())
                ? "Hola"
                : "Hola " + nombrePersona;

        String contenido = saludo + ",\n\n"
                + "Se ha creado tu acceso a la app oficial del Mutxamel Club de Futbol.\n\n"
                + "Para activar tu cuenta abre la app, entra en \"Área Club - Activar cuenta\" "
                + "e introduce tu email, el siguiente código y la contraseña que quieras usar:\n\n"
                + codigoActivacion + "\n\n"
                + "Este código caduca en 4 horas y solo se puede usar 5 veces. Si no lo usas a "
                + "tiempo o lo introduces mal varias veces, pide a la oficina del club que te "
                + "reenvie la invitacion.\n\n"
                + "Si no esperabas este email, puedes ignorarlo.";

        boolean enviado = enviarEmail(REMITENTE, email, null,
                "Activa tu cuenta de la app del Mutxamel CF", contenido);

        logger.debug("Fin enviarInvitacionApp: enviado={}", enviado);
        return enviado;
    }

    private static final String SEPARADOR_IDIOMAS = "\n\n------------------------------\n\n";

    /**
     * Confirma a la persona que su cuenta de la app está activada y le
     * explica qué puede hacer según sus roles. El correo va en castellano
     * y, tras una línea de separación, en valenciano.
     */
    public boolean enviarCuentaActivada(String email, String nombrePersona, Collection<String> roles) {
        logger.debug("Inicio enviarCuentaActivada");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                "¡Tu cuenta de la app del Mutxamel CF ya está activada! / "
                        + "El teu compte de l'app del Mutxamel CF ja està activat!",
                construirCuentaActivada(nombrePersona, roles));

        logger.debug("Fin enviarCuentaActivada: enviado={}", enviado);
        return enviado;
    }

    static String construirCuentaActivada(String nombrePersona, Collection<String> roles) {

        Set<String> codigos = roles == null ? Set.of() : new HashSet<>(roles);

        boolean tieneNombre = nombrePersona != null && !nombrePersona.isBlank();

        String saludoEs = tieneNombre ? "Hola " + nombrePersona.trim() + ":" : "Hola:";
        String saludoCa = tieneNombre ? "Hola " + nombrePersona.trim() + ":" : "Hola:";

        StringBuilder es = new StringBuilder();

        es.append(saludoEs).append("\n\n")
                .append("Tu cuenta de la app oficial del Mutxamel CF se ha activado correctamente. ")
                .append("A partir de ahora puedes entrar en el Área Club con tu email y la contraseña que elegiste.\n\n")
                .append("Qué puedes hacer con tu cuenta\n")
                .append("• Ver el calendario de entrenamientos y partidos de tus equipos.\n")
                .append("• Consultar los próximos partidos y resultados.\n")
                .append("• Recibir avisos y mensajes privados del club, los entrenadores y la coordinación, ")
                .append("y escribirles desde la app.\n")
                .append("• Recibir notificaciones en el móvil. Puedes elegir cuáles en Ajustes, ")
                .append("donde también cambias el idioma y el tema.\n");

        if (codigos.contains("JUGADOR")) {
            es.append("\nComo jugador\n")
                    .append("• Ves en el calendario si estás convocado o no a cada partido, y tu asistencia a los entrenamientos.\n")
                    .append("• Puedes avisar de que faltarás a un entrenamiento antes de que se celebre.\n")
                    .append("• Puedes escribir a tu entrenador y a la coordinación.\n");
        }

        if (codigos.contains("FAMILIAR")) {
            es.append("\nComo familiar\n")
                    .append("• En «Mis jugadores» ves a los jugadores vinculados a tu cuenta.\n")
                    .append("• Ves sus convocatorias y su asistencia a los entrenamientos.\n")
                    .append("• Puedes justificar sus faltas a un entrenamiento con antelación.\n")
                    .append("• Puedes escribir a sus entrenadores y a la coordinación.\n");
        }

        if (codigos.contains("ENTRENADOR")) {
            es.append("\nComo entrenador\n")
                    .append("• En «Mis equipos» pasas lista y registras la asistencia de cada entrenamiento.\n")
                    .append("• Preparas las convocatorias y registras las estadísticas de los partidos.\n")
                    .append("• Gestionas los horarios y entrenamientos de tus equipos, y puedes cancelar un ")
                    .append("entrenamiento avisando a jugadores y familias.\n")
                    .append("• Recibes un aviso cuando una familia justifica una falta.\n")
                    .append("• Envías avisos a tus equipos y mensajes a jugadores y familias.\n");
        }

        if (codigos.contains("COORDINADOR") || codigos.contains("ADMIN_APP")) {
            es.append("\nComo coordinador\n")
                    .append("• Tienes acceso a todos los equipos del club en «Mis equipos»: asistencia, ")
                    .append("convocatorias, partidos y calendario.\n")
                    .append("• Puedes enviar avisos a equipos o a categorías completas, y mensajes a cualquier ")
                    .append("jugador, familia o entrenador.\n");
        }

        if (codigos.contains("RETRANSMISION")) {
            es.append("\nComo responsable de retransmisión\n")
                    .append("• En «Partido en directo» publicas los avisos en directo del primer equipo ")
                    .append("durante los partidos.\n");
        }

        es.append("\nSi necesitas ayuda, escríbenos a mutxamelcf.gestion@gmail.com.\n")
                .append("Puedes eliminar tu cuenta cuando quieras desde Ajustes › Eliminar mi cuenta.\n\n")
                .append("¡Nos vemos en el campo!\n")
                .append("Mutxamel CF");

        StringBuilder ca = new StringBuilder();

        ca.append(saludoCa).append("\n\n")
                .append("El teu compte de l'app oficial del Mutxamel CF s'ha activat correctament. ")
                .append("A partir d'ara pots entrar a l'Àrea Club amb el teu email i la contrasenya que vas triar.\n\n")
                .append("Què pots fer amb el teu compte\n")
                .append("• Veure el calendari d'entrenaments i partits dels teus equips.\n")
                .append("• Consultar els pròxims partits i resultats.\n")
                .append("• Rebre avisos i missatges privats del club, els entrenadors i la coordinació, ")
                .append("i escriure'ls des de l'app.\n")
                .append("• Rebre notificacions al mòbil. Pots triar quines a Ajustos, ")
                .append("on també canvies l'idioma i el tema.\n");

        if (codigos.contains("JUGADOR")) {
            ca.append("\nCom a jugador\n")
                    .append("• Veus al calendari si estàs convocat o no a cada partit, i la teua assistència als entrenaments.\n")
                    .append("• Pots avisar que faltaràs a un entrenament abans que se celebre.\n")
                    .append("• Pots escriure al teu entrenador i a la coordinació.\n");
        }

        if (codigos.contains("FAMILIAR")) {
            ca.append("\nCom a familiar\n")
                    .append("• A «Els meus jugadors» veus els jugadors vinculats al teu compte.\n")
                    .append("• Veus les seues convocatòries i la seua assistència als entrenaments.\n")
                    .append("• Pots justificar les seues faltes a un entrenament amb antelació.\n")
                    .append("• Pots escriure als seus entrenadors i a la coordinació.\n");
        }

        if (codigos.contains("ENTRENADOR")) {
            ca.append("\nCom a entrenador\n")
                    .append("• A «Els meus equips» passes llista i registres l'assistència de cada entrenament.\n")
                    .append("• Prepares les convocatòries i registres les estadístiques dels partits.\n")
                    .append("• Gestiones els horaris i entrenaments dels teus equips, i pots cancel·lar un ")
                    .append("entrenament avisant jugadors i famílies.\n")
                    .append("• Reps un avís quan una família justifica una falta.\n")
                    .append("• Envies avisos als teus equips i missatges a jugadors i famílies.\n");
        }

        if (codigos.contains("COORDINADOR") || codigos.contains("ADMIN_APP")) {
            ca.append("\nCom a coordinador\n")
                    .append("• Tens accés a tots els equips del club a «Els meus equips»: assistència, ")
                    .append("convocatòries, partits i calendari.\n")
                    .append("• Pots enviar avisos a equips o a categories completes, i missatges a qualsevol ")
                    .append("jugador, família o entrenador.\n");
        }

        if (codigos.contains("RETRANSMISION")) {
            ca.append("\nCom a responsable de retransmissió\n")
                    .append("• A «Partit en directe» publiques els avisos en directe del primer equip ")
                    .append("durant els partits.\n");
        }

        ca.append("\nSi necessites ajuda, escriu-nos a mutxamelcf.gestion@gmail.com.\n")
                .append("Pots eliminar el teu compte quan vulgues des d'Ajustos › Eliminar el meu compte.\n\n")
                .append("Ens veiem al camp!\n")
                .append("Mutxamel CF");

        return es + SEPARADOR_IDIOMAS + ca;
    }

    /**
     * Avisa al club de que un usuario de la app ha denunciado un
     * mensaje o comunicación (Apple Guideline 1.2).
     */
    public boolean enviarReporteContenido(String asunto, String contenido, String emailReportante) {
        logger.debug("Inicio enviarReporteContenido: asunto={}", asunto);
        boolean enviado = enviarEmail(REMITENTE, DESTINATARIO_CONTACTO, emailReportante, asunto, contenido);
        logger.debug("Fin enviarReporteContenido: enviado={}", enviado);
        return enviado;
    }

    public boolean enviarWhatsapp(String telefono, String mensaje) {
        logger.debug("Inicio enviarWhatsapp: telefono={}", telefono);
        logger.warn("WhatsApp no enviado: no hay proveedor configurado");
        logger.debug("Fin enviarWhatsapp: enviado=false");
        return false;
    }

    public boolean enviarNotificacionMovil(String destinatario, String titulo, String mensaje) {
        logger.debug("Inicio enviarNotificacionMovil: destinatario={}, titulo={}", destinatario, titulo);
        logger.warn("Notificacion movil no enviada: no hay proveedor configurado");
        logger.debug("Fin enviarNotificacionMovil: enviada=false");
        return false;
    }

    private boolean enviarEmail(String remitente, String destinatario, String emailRespuesta, String asunto,
            String contenido) {
        logger.debug("Inicio enviarEmail: destinatario={}, asunto={}", destinatario, asunto);
        try {
            MimeMessage mensaje = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true);
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            if (emailRespuesta != null && !emailRespuesta.isBlank()) {
                helper.setReplyTo(emailRespuesta);
            }
            helper.setSubject(asunto);
            helper.setText(contenido);
            emailSender.send(mensaje);
            logger.info("Email enviado correctamente: destinatario={}, asunto={}", destinatario, asunto);
            logger.debug("Fin enviarEmail: enviado=true");
            return true;
        } catch (MessagingException | RuntimeException exception) {
            logger.error("Error al enviar email: destinatario={}, error={}", destinatario, exception.getMessage(),
                    exception);
            logger.debug("Fin enviarEmail: enviado=false");
            return false;
        }
    }
}
