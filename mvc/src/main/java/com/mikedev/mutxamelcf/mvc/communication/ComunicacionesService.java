package com.mikedev.mutxamelcf.mvc.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.model.IdiomaApp;
import com.mikedev.mutxamelcf.service.AvisosCuentaEmail;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Emails del club. Los que van a usuarios de la app salen de las plantillas {@code emails/emails_es|ca|en.properties}
 * (ver {@link PlantillasEmail}): en el idioma de la cuenta, uno solo, salvo la invitación para activar la cuenta, que
 * va en los tres idiomas porque quien la recibe todavía no tiene la app. Los emails internos del club (pedidos,
 * contacto, importación de la FFCV, reportes) van en castellano.
 */
@Service
public class ComunicacionesService implements AvisosCuentaEmail {

    private static final Logger logger = LoggerFactory.getLogger(ComunicacionesService.class);

    private static final DateTimeFormatter FECHA_AVISO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String REMITENTE = "contacto.web@mutxamelcf.es";
    private static final String DESTINATARIO_PEDIDOS = "mutxamelcf.pedidos@gmail.com";
    private static final String DESTINATARIO_CONTACTO = "mutxamelcf.gestion@gmail.com";

    private static final String SEPARADOR_IDIOMAS = "\n\n------------------------------\n\n";

    /** Orden de las secciones por rol en el email de cuenta activada. */
    private static final List<String> SECCIONES_DE_ROL = List.of(
            "JUGADOR", "FAMILIAR", "ENTRENADOR", "COORDINADOR", "RETRANSMISION", "RRSS", "ADMIN_APP");

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

    /**
     * Invitación para activar la cuenta. La persona todavía no tiene la app (ni idioma elegido), así que va en
     * castellano, valenciano e inglés, separados por una línea.
     */
    public boolean enviarInvitacionApp(String email, String nombrePersona, String codigoActivacion) {
        logger.debug("Inicio enviarInvitacionApp: email={}", email);

        boolean enviado = enviarEmail(REMITENTE, email, null,
                asuntoEnLosTresIdiomas("invitacion.asunto"),
                construirInvitacionApp(nombrePersona, codigoActivacion));

        logger.debug("Fin enviarInvitacionApp: enviado={}", enviado);
        return enviado;
    }

    static String construirInvitacionApp(String nombrePersona, String codigoActivacion) {

        return IdiomaApp.TODOS.stream()
                .map(idioma -> PlantillasEmail.texto(idioma, "invitacion.cuerpo",
                        "saludo", saludo(idioma, nombrePersona, true),
                        "codigo", codigoActivacion,
                        "dias", String.valueOf(UsuarioAppService.DIAS_VALIDEZ_ACTIVACION)))
                .collect(Collectors.joining(SEPARADOR_IDIOMAS));
    }

    /**
     * Aviso a la gestión del club de que falta importar el CSV semanal de
     * partidos de la intranet de la FFCV.
     */
    public boolean enviarRecordatorioImportacionFfcv() {
        logger.debug("Inicio enviarRecordatorioImportacionFfcv");
        boolean enviado = enviarEmail(REMITENTE, DESTINATARIO_CONTACTO, null,
                "Recordatorio: importar los partidos de la jornada",
                "Recuerda importar los partidos de la jornada (LstPartidos.csv de la intranet de la FFCV) "
                        + "y el Excel de clasificaciones (ListadoClasificaciones.xlsx).\n"
                        + "Se suben en el panel web o desde la app: Área Club › Administración › Subir datos FFCV.\n"
                        + "Si hay jornada de Veteranos, sube también la clasificación de la LVCA "
                        + "(la página guardada de ligaveteranosalicante.es/scoretable) en el mismo sitio.");
        logger.debug("Fin enviarRecordatorioImportacionFfcv: enviado={}", enviado);
        return enviado;
    }

    /**
     * Confirma a la persona que su cuenta de la app está activada y le explica qué puede hacer según sus roles, en el
     * idioma de la cuenta.
     */
    public boolean enviarCuentaActivada(String email, String nombrePersona, Collection<String> roles, String idioma) {
        logger.debug("Inicio enviarCuentaActivada");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "cuentaActivada.asunto"),
                construirCuentaActivada(nombrePersona, roles, idioma));

        logger.debug("Fin enviarCuentaActivada: enviado={}", enviado);
        return enviado;
    }

    static String construirCuentaActivada(String nombrePersona, Collection<String> roles, String idioma) {

        Set<String> codigos = roles == null ? new HashSet<>() : new HashSet<>(roles);

        // ADMIN_APP ve lo mismo que un coordinador.
        if (codigos.contains("ADMIN_APP")) {
            codigos.add("COORDINADOR");
        }

        String secciones = SECCIONES_DE_ROL.stream()
                .filter(codigos::contains)
                .map(rol -> PlantillasEmail.texto(idioma, "rol." + rol))
                .collect(Collectors.joining());

        return PlantillasEmail.texto(idioma, "cuentaActivada.cuerpo",
                "saludo", saludo(idioma, nombrePersona, false),
                "roles", secciones);
    }

    /** Código para cambiar la contraseña desde la app, en el idioma indicado. */
    public boolean enviarCodigoRecuperacion(String email, String nombrePersona, String codigo, int minutosValidez,
            String idioma) {
        logger.debug("Inicio enviarCodigoRecuperacion");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "codigoRecuperacion.asunto"),
                construirCodigoRecuperacion(nombrePersona, codigo, minutosValidez, idioma));

        logger.debug("Fin enviarCodigoRecuperacion: enviado={}", enviado);
        return enviado;
    }

    static String construirCodigoRecuperacion(String nombrePersona, String codigo, int minutosValidez,
            String idioma) {

        return PlantillasEmail.texto(idioma, "codigoRecuperacion.cuerpo",
                "saludo", saludo(idioma, nombrePersona, false),
                "codigo", codigo,
                "minutos", String.valueOf(minutosValidez));
    }

    /**
     * Avisa a una cuenta sin ningún jugador activo de que se desactivará en la fecha indicada, en el idioma de la
     * cuenta.
     */
    @Override
    public boolean enviarAvisoCuentaSinJugadores(String email, String nombrePersona, LocalDate fechaDesactivacion,
            String idioma) {
        logger.debug("Inicio enviarAvisoCuentaSinJugadores");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "cuentaSinJugadores.asunto"),
                construirAvisoCuentaSinJugadores(nombrePersona, fechaDesactivacion, idioma));

        logger.debug("Fin enviarAvisoCuentaSinJugadores: enviado={}", enviado);
        return enviado;
    }

    static String construirAvisoCuentaSinJugadores(String nombrePersona, LocalDate fechaDesactivacion,
            String idioma) {

        return PlantillasEmail.texto(idioma, "cuentaSinJugadores.cuerpo",
                "saludo", saludo(idioma, nombrePersona, false),
                "fecha", FECHA_AVISO.format(fechaDesactivacion));
    }

    /** Confirma que la contraseña de la cuenta se acaba de cambiar, en el idioma de la cuenta. */
    public boolean enviarContrasenaCambiada(String email, String nombrePersona, String idioma) {
        logger.debug("Inicio enviarContrasenaCambiada");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "contrasenaCambiada.asunto"),
                construirContrasenaCambiada(nombrePersona, idioma));

        logger.debug("Fin enviarContrasenaCambiada: enviado={}", enviado);
        return enviado;
    }

    static String construirContrasenaCambiada(String nombrePersona, String idioma) {

        return PlantillasEmail.texto(idioma, "contrasenaCambiada.cuerpo",
                "saludo", saludo(idioma, nombrePersona, false));
    }

    /**
     * El código para confirmar el email de una solicitud de familia, en el idioma de la petición. No es una invitación del
     * club: el texto dice que es para confirmar el email de la solicitud de acceso.
     */
    public boolean enviarCodigoSolicitudFamilia(String email, String nombrePersona, String codigo, String idioma) {
        logger.debug("Inicio enviarCodigoSolicitudFamilia");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "solicitudFamilia.asunto"),
                construirCodigoSolicitudFamilia(nombrePersona, codigo, idioma));

        logger.debug("Fin enviarCodigoSolicitudFamilia: enviado={}", enviado);
        return enviado;
    }

    static String construirCodigoSolicitudFamilia(String nombrePersona, String codigo, String idioma) {

        return PlantillasEmail.texto(idioma, "solicitudFamilia.cuerpo",
                "saludo", saludo(idioma, nombrePersona, false),
                "codigo", codigo,
                "dias", String.valueOf(UsuarioAppService.DIAS_VALIDEZ_ACTIVACION));
    }

    /**
     * Avisa a la familia de que su solicitud ha caducado SIN haberse revisado y se han borrado los datos de la solicitud.
     * Dos variantes: si el registro creó la cuenta y se ha borrado ({@code cuentaBorrada}), puede volver a pedirlo en
     * «Soy familiar de un jugador»; si la cuenta se conserva (ya tenía roles), en «Pedir acceso a otro jugador».
     */
    public boolean enviarSolicitudFamiliaCaducada(String email, String nombrePersona, String idioma,
            boolean cuentaBorrada) {
        logger.debug("Inicio enviarSolicitudFamiliaCaducada");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "solicitudFamiliaCaducada.asunto"),
                construirSolicitudFamiliaCaducada(nombrePersona, idioma, cuentaBorrada));

        logger.debug("Fin enviarSolicitudFamiliaCaducada: enviado={}", enviado);
        return enviado;
    }

    static String construirSolicitudFamiliaCaducada(String nombrePersona, String idioma, boolean cuentaBorrada) {

        return PlantillasEmail.texto(idioma,
                cuentaBorrada ? "solicitudFamiliaCaducada.cuerpo.cuentaBorrada"
                        : "solicitudFamiliaCaducada.cuerpo.cuentaConservada",
                "saludo", saludo(idioma, nombrePersona, false));
    }

    /** "Ya tienes acceso" (primer hijo aprobado), con los jugadores aprobados y el bloque de lo que puede hacer una familia. */
    @Override
    public boolean enviarSolicitudFamiliaAprobada(String email, String nombrePersona, List<String> jugadores,
            String idioma) {
        logger.debug("Inicio enviarSolicitudFamiliaAprobada");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "solicitudFamiliaAprobada.asunto"),
                construirSolicitudFamiliaAprobada(nombrePersona, jugadores, idioma));

        logger.debug("Fin enviarSolicitudFamiliaAprobada: enviado={}", enviado);
        return enviado;
    }

    static String construirSolicitudFamiliaAprobada(String nombrePersona, List<String> jugadores, String idioma) {

        return PlantillasEmail.texto(idioma, "solicitudFamiliaAprobada.cuerpo",
                "saludo", saludo(idioma, nombrePersona, false),
                "jugadores", unirNombres(jugadores, idioma),
                "roles", PlantillasEmail.texto(idioma, "rol.FAMILIAR"));
    }

    /** "Pepe García", "Pepe García y Ana García" o "A, B y C", con la palabra de cada idioma. */
    static String unirNombres(List<String> nombres, String idioma) {

        if (nombres == null || nombres.isEmpty()) {
            return "";
        }

        if (nombres.size() == 1) {
            return nombres.get(0);
        }

        String y = PlantillasEmail.texto(idioma, "solicitudFamilia.y");

        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " " + y + " "
                + nombres.get(nombres.size() - 1);
    }

    /** Se aprueba otro hijo de la misma solicitud: email corto. */
    @Override
    public boolean enviarSolicitudFamiliaOtroAprobado(String email, String nombrePersona, String jugador,
            String idioma) {
        logger.debug("Inicio enviarSolicitudFamiliaOtroAprobado");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "solicitudFamiliaOtroAprobado.asunto", "jugador", jugador),
                PlantillasEmail.texto(idioma, "solicitudFamiliaOtroAprobado.cuerpo",
                        "saludo", saludo(idioma, nombrePersona, false),
                        "jugador", jugador));

        logger.debug("Fin enviarSolicitudFamiliaOtroAprobado: enviado={}", enviado);
        return enviado;
    }

    /** Se rechaza un hijo; el motivo solo va si el revisor escribió uno. */
    @Override
    public boolean enviarSolicitudFamiliaRechazada(String email, String nombrePersona, String jugador, String motivo,
            String idioma) {
        logger.debug("Inicio enviarSolicitudFamiliaRechazada");

        boolean enviado = enviarEmail(REMITENTE, email, null,
                PlantillasEmail.texto(idioma, "solicitudFamiliaRechazada.asunto"),
                construirSolicitudFamiliaRechazada(nombrePersona, jugador, motivo, idioma));

        logger.debug("Fin enviarSolicitudFamiliaRechazada: enviado={}", enviado);
        return enviado;
    }

    static String construirSolicitudFamiliaRechazada(String nombrePersona, String jugador, String motivo,
            String idioma) {

        String limpio = motivo == null ? "" : motivo.trim();

        return PlantillasEmail.texto(idioma, "solicitudFamiliaRechazada.cuerpo",
                "saludo", saludo(idioma, nombrePersona, false),
                "jugador", jugador,
                "motivo", limpio.isEmpty() ? ""
                        : PlantillasEmail.texto(idioma, "solicitudFamiliaRechazada.motivo", "motivo", limpio));
    }

    /** "Hola Ana:" (o "Hola Ana," en la invitación); sin nombre, "Hola:" / "Hola,". */
    private static String saludo(String idioma, String nombrePersona, boolean invitacion) {

        String prefijo = invitacion ? "saludo.invitacion." : "saludo.";

        return nombrePersona == null || nombrePersona.isBlank()
                ? PlantillasEmail.texto(idioma, prefijo + "sinNombre")
                : PlantillasEmail.texto(idioma, prefijo + "conNombre", "nombre", nombrePersona.trim());
    }

    private static String asuntoEnLosTresIdiomas(String clave) {

        return IdiomaApp.TODOS.stream()
                .map(idioma -> PlantillasEmail.texto(idioma, clave))
                .collect(Collectors.joining(" / "));
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
