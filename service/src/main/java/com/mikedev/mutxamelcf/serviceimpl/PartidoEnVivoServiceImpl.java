package com.mikedev.mutxamelcf.serviceimpl;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveDao;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoLiveEstado;
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
     * Mismo criterio literal de equipo/categoria que usaba antes
     * ResultadoDaoImpl.obtenerResultadoPrimerEquipo().
     */
    private static final String EQUIPO_PRIMER_EQUIPO = "Mutxamel CF";
    private static final String CATEGORIA_PRIMER_EQUIPO = "Primer Equipo";

    private final PartidoLiveDao partidoLiveDao;
    private final PartidoDao partidoDao;
    private final NotificacionAppService notificacionAppService;
    private final UsuarioAppService usuarioAppService;

    public PartidoEnVivoServiceImpl(
            PartidoLiveDao partidoLiveDao,
            PartidoDao partidoDao,
            NotificacionAppService notificacionAppService,
            UsuarioAppService usuarioAppService) {

        this.partidoLiveDao = partidoLiveDao;
        this.partidoDao = partidoDao;
        this.notificacionAppService = notificacionAppService;
        this.usuarioAppService = usuarioAppService;
    }

    @Override
    public void enviarAlineacion(Long usuarioId, String onceInicial, String suplentes) {

        validarRolRetransmision(usuarioId);

        String mensaje = "Once inicial: " + onceInicial + "\n\nSuplentes: " + suplentes;

        notificacionAppService.difundirATodos(TIPO_PUSH, "📋 Alineación del Mutxamel CF", mensaje, null);
    }

    @Override
    public void enviarInicioPartido(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        partidoLiveDao.reiniciar();

        String mensaje = "Mutxamel CF - " + nombreRival();

        notificacionAppService.difundirATodos(TIPO_PUSH, "⚽ ¡Comienza el partido!", mensaje, null);
    }

    @Override
    public void enviarGolFavor(Long usuarioId, String autor) {

        validarRolRetransmision(usuarioId);

        partidoLiveDao.sumarGolFavor(autor);

        String mensaje = autor + "\n\n" + textoMarcador();

        notificacionAppService.difundirATodos(TIPO_PUSH, "⚽ ¡GOOOL del Mutxamel CF!", mensaje, null);
    }

    @Override
    public void enviarGolContra(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        partidoLiveDao.sumarGolContra();

        notificacionAppService.difundirATodos(TIPO_PUSH, "Gol en contra", textoMarcador(), null);
    }

    @Override
    public void enviarDescanso(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        notificacionAppService.difundirATodos(TIPO_PUSH, "⏸️ Descanso", textoMarcador(), null);
    }

    @Override
    public void enviarSegundaParte(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        String mensaje = "Mutxamel CF - " + nombreRival();

        notificacionAppService.difundirATodos(TIPO_PUSH, "▶️ ¡Comienza la segunda parte!", mensaje, null);
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

        notificacionAppService.difundirATodos(TIPO_PUSH, "🏁 Final del partido", mensaje, null);
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
