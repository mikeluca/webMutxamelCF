package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveDao;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoLiveEstado;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.PartidoEnVivoService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@Service
public class PartidoEnVivoServiceImpl implements PartidoEnVivoService {

    /*
     * Los avisos del partido en directo caen dentro de la categoría de
     * preferencias "Resultados" (ver PreferenciasNotificacionService):
     * quien la tenga desactivada no debe recibirlos.
     */
    private static final String TIPO_PUSH = "RESULTADO";

    /*
     * Topic de FCM para quien sigue la retransmisión sin tener cuenta
     * en la app (suscripción anónima gestionada por la propia app). La
     * app se encarga de desuscribir a los usuarios con cuenta al
     * iniciar sesión, para que no reciban el aviso duplicado.
     */
    private static final String TOPIC_RESULTADOS = "resultados";

    /*
     * En EQUIPO, la fila del primer equipo tiene NOMBRE = "Primer Equipo"
     * (no "Mutxamel CF", que es el nombre del club, no de esa fila) y
     * CATEGORIA = "Primer Equipo" - confirmado en /admin/equipos
     * (id=21, orden='I').
     */
    private static final String EQUIPO_PRIMER_EQUIPO = "Primer Equipo";
    private static final String CATEGORIA_PRIMER_EQUIPO = "Primer Equipo";

    /*
     * BE-02: si enviarGolFavor() tarda en responder (el push a todos los
     * usuarios podía superar el timeout de la app antes de que las
     * notificaciones fuesen @Async), el retransmisor podía reintentar la
     * misma acción y sumar el mismo gol dos veces. Como no hay un
     * identificador de acción generado por la app, se descarta como
     * reintento cualquier gol del mismo autor que llegue dentro de esta
     * ventana desde el anterior -- una coincidencia real (el mismo
     * jugador marcando dos veces en menos de esto) es prácticamente
     * imposible en fútbol.
     */
    private static final Duration VENTANA_DEDUPLICACION_GOL = Duration.ofSeconds(15);

    private volatile String ultimoAutorGolFavor;
    private volatile Instant ultimoGolFavorEn;

    private final PartidoLiveDao partidoLiveDao;
    private final PartidoDao partidoDao;
    private final NotificacionAppService notificacionAppService;
    private final UsuarioAppService usuarioAppService;
    private final FcmPushService fcmPushService;

    public PartidoEnVivoServiceImpl(
            PartidoLiveDao partidoLiveDao,
            PartidoDao partidoDao,
            NotificacionAppService notificacionAppService,
            UsuarioAppService usuarioAppService,
            FcmPushService fcmPushService) {

        this.partidoLiveDao = partidoLiveDao;
        this.partidoDao = partidoDao;
        this.notificacionAppService = notificacionAppService;
        this.usuarioAppService = usuarioAppService;
        this.fcmPushService = fcmPushService;
    }

    @Override
    public void enviarAlineacion(Long usuarioId, String onceInicial, String suplentes) {

        validarRolRetransmision(usuarioId);

        String mensaje = "Once inicial: " + onceInicial + "\n\nSuplentes: " + suplentes;

        difundir("📋 Alineación del Mutxamel CF", mensaje);
    }

    @Override
    public void enviarInicioPartido(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        partidoLiveDao.reiniciar();

        ultimoAutorGolFavor = null;
        ultimoGolFavorEn = null;

        String mensaje = "Mutxamel CF - " + nombreRival();

        difundir("⚽ ¡Comienza el partido!", mensaje);
    }

    @Override
    public void enviarGolFavor(Long usuarioId, String autor) {

        validarRolRetransmision(usuarioId);

        if (esReintentoDuplicadoDeGolFavor(autor)) {
            return;
        }

        partidoLiveDao.sumarGolFavor(autor);

        String mensaje = autor + "\n\n" + textoMarcador();

        difundir("⚽ ¡GOOOL del Mutxamel CF!", mensaje);
    }

    private boolean esReintentoDuplicadoDeGolFavor(String autor) {

        String autorNormalizado = autor == null ? "" : autor.trim().toLowerCase(Locale.ROOT);
        Instant ahora = Instant.now();

        boolean esDuplicado = autorNormalizado.equals(ultimoAutorGolFavor)
                && ultimoGolFavorEn != null
                && ahora.isBefore(ultimoGolFavorEn.plus(VENTANA_DEDUPLICACION_GOL));

        ultimoAutorGolFavor = autorNormalizado;
        ultimoGolFavorEn = ahora;

        return esDuplicado;
    }

    @Override
    public void enviarGolContra(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        partidoLiveDao.sumarGolContra();

        difundir("Gol en contra", textoMarcador());
    }

    @Override
    public void enviarDescanso(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        difundir("⏸️ Descanso", textoMarcador());
    }

    @Override
    public void enviarSegundaParte(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        String mensaje = "Mutxamel CF - " + nombreRival();

        difundir("▶️ ¡Comienza la segunda parte!", mensaje);
    }

    @Override
    public void enviarFinalPartido(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        PartidoLiveEstado estado = partidoLiveDao.obtenerEstado();

        String goleadores = estado.getGoleadores() == null || estado.getGoleadores().isEmpty()
                ? "Sin goleadores"
                : String.join(", ", estado.getGoleadores());

        String mensaje = textoMarcador(estado)
                + "\n\nGoleadores: " + goleadores
                + "\n\nHa finalizado el partido.";

        difundir("🏁 Final del partido", mensaje);
    }

    /**
     * Envía el aviso por los dos canales: a los usuarios con cuenta
     * (por dispositivo, vía NotificacionAppService) y al topic de FCM
     * "resultados" (suscripción anónima sin cuenta). La app se encarga
     * de que un usuario con cuenta no reciba el aviso dos veces.
     */
    private void difundir(String titulo, String mensaje) {

        notificacionAppService.difundirATodos(TIPO_PUSH, titulo, mensaje, null);
        fcmPushService.enviarATopic(TOPIC_RESULTADOS, titulo, mensaje);
    }

    private void validarRolRetransmision(Long usuarioId) {

        if (usuarioId == null || !usuarioAppService.tieneRol(usuarioId.intValue(), "RETRANSMISION")) {

            throw new SecurityException(
                    "No tienes permiso para enviar avisos del partido en directo");
        }
    }

    private String nombreRival() {

        Partido partido = partidoDao.obtenerMasRelevantePorEquipoNombre(
                EQUIPO_PRIMER_EQUIPO,
                CATEGORIA_PRIMER_EQUIPO);

        return partido != null && partido.getRival() != null
                ? partido.getRival()
                : "el rival";
    }

    private String textoMarcador() {
        return textoMarcador(partidoLiveDao.obtenerEstado());
    }

    private String textoMarcador(PartidoLiveEstado estado) {

        return "Mutxamel CF " + estado.getGolesFavor()
                + " - " + estado.getGolesContra()
                + " " + nombreRival();
    }
}
