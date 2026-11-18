package com.mikedev.mutxamelcf.serviceimpl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

/**
 * Los avisos (bandeja + push, en el idioma de cada cuenta) de las solicitudes de acceso de las familias. Los textos
 * están en {@code i18n/avisos_*.properties} (claves {@code solicitudFamilia.*}).
 *
 * <p>
 * Privacidad: el aviso a quien revisa NO lleva ningún dato del menor ni de la familia, ni en el push ni en la bandeja;
 * quien revisa los ve al abrir la pantalla de solicitudes. Los avisos a la familia llevan el nombre que ella misma
 * escribió. El motivo de un rechazo va solo en la bandeja, como el resto de motivos (docs/avisos.md).
 */
@Component
public class AvisosSolicitudFamilia {

    private static final Logger logger = LoggerFactory.getLogger(AvisosSolicitudFamilia.class);

    /** Valor de data["tipo"] del push a quien revisa y de su fila en la bandeja. */
    public static final String TIPO_NUEVA = "SOLICITUD_FAMILIA";

    /** Valor de data["tipo"] del push a la familia (aprobada o rechazada). */
    public static final String TIPO_RESPUESTA = "SOLICITUD_FAMILIA_RESPUESTA";

    /** La pantalla que abre el push a quien revisa. */
    public static final String PANTALLA_SOLICITUDES = "SOLICITUDES_FAMILIA";

    /** La pantalla que abre el push a la familia: el estado de su solicitud. */
    public static final String PANTALLA_MI_SOLICITUD = "MI_SOLICITUD_FAMILIA";

    private final NotificacionAppService notificacionAppService;
    private final FcmPushService fcmPushService;
    private final IdiomasUsuarios idiomas;
    private final TextosAvisos textos;
    private final EquipoGestionDao equipoGestionDao;

    public AvisosSolicitudFamilia(NotificacionAppService notificacionAppService, FcmPushService fcmPushService,
            IdiomasUsuarios idiomas, TextosAvisos textos, EquipoGestionDao equipoGestionDao) {

        this.notificacionAppService = notificacionAppService;
        this.fcmPushService = fcmPushService;
        this.idiomas = idiomas;
        this.textos = textos;
        this.equipoGestionDao = equipoGestionDao;
    }

    /** Coordinación, administradores de la app y los entrenadores de esos equipos (sin repetidos, sin {@code excluir}). */
    public Set<Long> destinatariosDeRevision(Set<Long> equipoIds, Long excluir) {

        Set<Long> destinos = new LinkedHashSet<>(equipoGestionDao.obtenerCoordinadores());
        destinos.addAll(equipoGestionDao.obtenerAdministradoresApp());

        for (Long equipoId : equipoIds) {
            destinos.addAll(equipoGestionDao.obtenerEntrenadoresPorEquipo(equipoId));
        }

        if (excluir != null) {
            destinos.remove(excluir);
        }

        return destinos;
    }

    /** "Nueva solicitud de acceso de una familia", sin datos del menor. */
    public int avisarNuevaSolicitud(Set<Long> destinatarios, Long solicitudId) {

        int avisados = 0;

        for (Long cuenta : destinatarios) {

            String idioma = idiomas.idiomaDe(cuenta);

            boolean enviado = enviar(cuenta, TIPO_NUEVA, textos.texto(idioma, "solicitudFamilia.nueva.titulo"),
                    textos.texto(idioma, "solicitudFamilia.nueva.texto"),
                    textos.texto(idioma, "solicitudFamilia.nueva.push"), solicitudId,
                    datos(TIPO_NUEVA, PANTALLA_SOLICITUDES, solicitudId));

            avisados += enviado ? 1 : 0;
        }

        return avisados;
    }

    /** "Ya puedes ver la información de {jugador}". Solo a la cuenta que pidió el acceso. */
    public void avisarAprobada(Long cuenta, String jugador, Long solicitudId) {

        String idioma = idiomas.idiomaDe(cuenta);
        String texto = textos.texto(idioma, "solicitudFamilia.aprobada.texto", "jugador", jugador);

        enviar(cuenta, TIPO_RESPUESTA, textos.texto(idioma, "solicitudFamilia.aprobada.titulo"), texto, texto,
                solicitudId, datos(TIPO_RESPUESTA, PANTALLA_MI_SOLICITUD, solicitudId));
    }

    /** Rechazada, con el motivo si lo hay (solo en la bandeja). */
    public void avisarRechazada(Long cuenta, String jugador, String motivo, Long solicitudId) {

        String idioma = idiomas.idiomaDe(cuenta);
        String texto = textos.texto(idioma, "solicitudFamilia.rechazada.texto", "jugador", jugador);
        String limpio = motivo == null ? "" : motivo.trim();
        String bandeja = limpio.isEmpty() ? texto
                : texto + "\n\n" + textos.texto(idioma, "motivo", "motivo", limpio);

        enviar(cuenta, TIPO_RESPUESTA, textos.texto(idioma, "solicitudFamilia.rechazada.titulo"), bandeja, texto,
                solicitudId, datos(TIPO_RESPUESTA, PANTALLA_MI_SOLICITUD, solicitudId));
    }

    /** El recordatorio diario a coordinación y administración: hay solicitudes pendientes de más de 2 días. */
    public int recordarACoordinacion() {

        Set<Long> destinos = new LinkedHashSet<>(equipoGestionDao.obtenerCoordinadores());
        destinos.addAll(equipoGestionDao.obtenerAdministradoresApp());

        int avisados = 0;

        for (Long cuenta : destinos) {

            String idioma = idiomas.idiomaDe(cuenta);
            String texto = textos.texto(idioma, "solicitudFamilia.recordatorio.texto");

            boolean enviado = enviar(cuenta, TIPO_NUEVA, textos.texto(idioma, "solicitudFamilia.recordatorio.titulo"),
                    texto, texto, null, datos(TIPO_NUEVA, PANTALLA_SOLICITUDES, null));

            avisados += enviado ? 1 : 0;
        }

        return avisados;
    }

    private static Map<String, String> datos(String tipo, String pantalla, Long solicitudId) {

        Map<String, String> datos = new LinkedHashMap<>();

        datos.put("tipo", tipo);
        datos.put("pantalla", pantalla);

        if (solicitudId != null) {
            datos.put("solicitudId", String.valueOf(solicitudId));
        }

        return datos;
    }

    /** Bandeja y push; un fallo de envío no rompe la operación: se registra y se sigue con el resto. */
    private boolean enviar(Long cuenta, String tipo, String titulo, String mensajeBandeja, String mensajePush,
            Long referenciaId, Map<String, String> datos) {

        try {
            if (!notificacionAppService.puedeRecibir(cuenta, "COMUNICACION")) {
                return false;
            }

            notificacionAppService.crear(cuenta, tipo, titulo, mensajeBandeja, referenciaId);
            fcmPushService.enviarNotificacionAUsuario(cuenta, tipo, titulo, mensajePush, referenciaId, datos);

            return true;

        } catch (RuntimeException e) {
            logger.error("No se ha podido avisar a la cuenta {} ({}): {}", cuenta, tipo, e.getMessage(), e);

            return false;
        }
    }
}
