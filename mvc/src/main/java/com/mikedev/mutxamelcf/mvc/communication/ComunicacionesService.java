package com.mikedev.mutxamelcf.mvc.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
