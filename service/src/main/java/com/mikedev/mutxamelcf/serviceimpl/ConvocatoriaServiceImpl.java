package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipacionDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.ConvocatoriaGuardarRequest;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugadorResponse;
import com.mikedev.mutxamelcf.model.ConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.Equipacion;
import com.mikedev.mutxamelcf.model.EquipacionResponse;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.ConvocatoriaService;
import com.mikedev.mutxamelcf.service.InvitadosService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ConvocatoriaServiceImpl implements ConvocatoriaService {

        /** Valor de "tipo" en el data del push de una convocatoria (nueva o actualizada). */
        static final String TIPO_PUSH_CONVOCATORIA = "CONVOCATORIA";

        private final ConvocatoriaDao convocatoriaDao;
        private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
        private final EquipoGestionDao equipoGestionDao;
        private final PartidoDao partidoDao;
        private final ComunicacionService comunicacionService;
        private final JustificacionFaltaPartidoDao justificacionFaltaPartidoDao;
        private final com.mikedev.mutxamelcf.dao.TemporadaDao temporadaDao;
        private final InvitadosService invitadosService;
        private final EquipacionDao equipacionDao;
        private final java.time.Clock clock;
        private final TextosAvisos textos;
        private final IdiomasUsuarios idiomas;

        public ConvocatoriaServiceImpl(
                        ConvocatoriaDao convocatoriaDao,
                        ConvocatoriaJugadorDao convocatoriaJugadorDao,
                        EquipoGestionDao equipoGestionDao,
                        PartidoDao partidoDao,
                        ComunicacionService comunicacionService,
                        JustificacionFaltaPartidoDao justificacionFaltaPartidoDao,
                        com.mikedev.mutxamelcf.dao.TemporadaDao temporadaDao,
                        InvitadosService invitadosService,
                        EquipacionDao equipacionDao,
                        java.time.Clock clock,
                        TextosAvisos textos,
                        IdiomasUsuarios idiomas) {

                this.textos = textos;
                this.idiomas = idiomas;

                this.equipacionDao = equipacionDao;
                this.invitadosService = invitadosService;
                this.clock = clock;
                this.temporadaDao = temporadaDao;
                this.justificacionFaltaPartidoDao = justificacionFaltaPartidoDao;
                this.convocatoriaDao = convocatoriaDao;
                this.convocatoriaJugadorDao = convocatoriaJugadorDao;
                this.equipoGestionDao = equipoGestionDao;
                this.partidoDao = partidoDao;
                this.comunicacionService = comunicacionService;
        }

        @Override
        @Transactional
        public ConvocatoriaResponse crear(
                        Long usuarioAppId,
                        ConvocatoriaGuardarRequest request) {

                validarUsuario(usuarioAppId);
                validarRequest(request);

                Partido partido = partidoDao.obtenerPorId(request.getPartidoId());

                if (partido == null) {
                        throw new IllegalArgumentException(
                                        "El partido no existe");
                }

                validarPartidoConvocable(partido);

                Long equipoId = partido.getEquipoId();

                if (!equipoGestionDao.existeEquipo(equipoId)) {
                        throw new IllegalArgumentException(
                                        "El equipo no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                equipoId)) {

                        throw new SecurityException(
                                        "No tienes permiso para gestionar este equipo");
                }

                List<Long> jugadoresEquipo = equipoGestionDao.obtenerJugadoresPorEquipo(equipoId);

                if (jugadoresEquipo.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "El equipo no tiene jugadores");
                }

                // Se bloquea el partido ANTES de comprobar si ya tiene convocatoria:
                // así dos altas simultáneas no pasan las dos la comprobación, y se
                // serializa también con quien justifica su ausencia a este partido.
                partidoDao.bloquearPorId(partido.getId());

                if (convocatoriaDao.existePorPartido(
                                partido.getId(),
                                null)) {

                        throw new IllegalArgumentException(
                                        "Ya existe una convocatoria para este partido");
                }

                validarJugadoresSeleccionados(
                                request.getJugadoresIds(),
                                jugadoresEquipo);

                rechazarJustificados(
                                partido.getId(),
                                request.getJugadoresIds(),
                                Set.of());

                // Un jugador ya autorizado como invitado con otro equipo ese día no se puede convocar.
                invitadosService.comprobarNoInvitadoEseDia(
                                request.getJugadoresIds(),
                                partido);

                validarEquipaje(
                                request.getEquipacionId(),
                                request.getEquipacionMochilaId(),
                                null);

                Convocatoria convocatoria = new Convocatoria();

                convocatoria.setEquipacionId(request.getEquipacionId());
                convocatoria.setEquipacionMochilaId(request.getEquipacionMochilaId());
                convocatoria.setEquipoId(equipoId);
                convocatoria.setPartidoId(partido.getId());
                convocatoria.setHoraConvocatoria(
                                limpiar(request.getHoraConvocatoria()));
                convocatoria.setLugarConvocatoria(
                                limpiar(request.getLugarConvocatoria()));
                convocatoria.setUsuarioEntrenadorId(
                                usuarioAppId);

                Convocatoria guardada = convocatoriaDao.guardar(convocatoria);

                /*
                 * El rival/campo/fecha/hora no se persisten en la
                 * convocatoria: se copian aquí, en memoria, desde el
                 * Partido ya cargado, únicamente para poder construir la
                 * respuesta y las notificaciones sin tener que volver a
                 * leer de base de datos.
                 */
                aplicarDatosPartido(guardada, partido);

                for (Long jugadorId : request.getJugadoresIds()) {

                        ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();

                        convocatoriaJugador.setConvocatoriaId(
                                        guardada.getId());

                        convocatoriaJugador.setJugadorId(
                                        jugadorId);

                        convocatoriaJugadorDao.guardar(
                                        convocatoriaJugador);
                }

                generarNotificaciones(
                                guardada,
                                request.getJugadoresIds());

                // Los jugadores de otros equipos quedan pendientes de coordinación (o autorizados si convoca ella).
                invitadosService.sincronizarInvitados(
                                usuarioAppId,
                                guardada,
                                partido,
                                request.getInvitadosIds());

                return construirResponse(guardada, null, usuarioAppId);
        }

        private void aplicarDatosPartido(
                        Convocatoria convocatoria,
                        Partido partido) {

                convocatoria.setRival(partido.getRival());
                convocatoria.setCampo(partido.getCampo());

                convocatoria.setFechaPartido(
                                partido.getDia() != null
                                                ? new java.sql.Date(partido.getDia().getTime()).toLocalDate()
                                                : null);

                convocatoria.setHoraPartido(partido.getHora());
        }

        /*
         * Los avisos se crean aquí, dentro de la transacción del cambio (si se deshace, no
         * queda ninguno), pero el push a los móviles no sale hasta que esta confirma:
         * ComunicacionService lo difiere al commit (ComunicacionPushListener).
         */
        private void generarNotificaciones(
                        Convocatoria convocatoria,
                        Collection<Long> jugadoresIds) {

                String equipo = equipoGestionDao.obtenerNombreEquipo(
                                convocatoria.getEquipoId());

                Equipacion principal = equipacionPorId(convocatoria.getEquipacionId());
                Equipacion mochila = equipacionPorId(convocatoria.getEquipacionMochilaId());

                avisar(
                                convocatoria,
                                jugadoresIds,
                                TIPO_PUSH_CONVOCATORIA,
                                true,
                                null,
                                null,
                                (jugador, esJugador, idioma) -> new TextoAviso(
                                                textos.texto(idioma, "convocatoria.nueva.titulo"),
                                                TextosConvocatoria.componer(textos, idioma,
                                                                textos.texto(idioma,
                                                                                esJugador ? "convocatoria.nueva.jugador"
                                                                                                : "convocatoria.nueva.familia",
                                                                                "jugador", jugador,
                                                                                "equipo", equipo,
                                                                                "rival", textoOVacio(convocatoria.getRival()),
                                                                                "fecha", textos.fecha(idioma, convocatoria.getFechaPartido()),
                                                                                "hora", TextosConvocatoria.hora(textos, convocatoria.getHoraPartido()),
                                                                                "horaCitacion", TextosConvocatoria.hora(textos, convocatoria.getHoraConvocatoria()),
                                                                                "lugar", textoOVacio(convocatoria.getLugarConvocatoria())),
                                                                TextosConvocatoria.parrafoEquipaje(textos, idioma, principal, mochila))));
        }

        private Equipacion equipacionPorId(Long id) {

                return id == null ? null : equipacionDao.obtenerPorId(id);
        }

        /*
         * El equipaje es opcional. La mochila solo se indica si hay equipaje principal y distinto de ella. Un
         * equipaje tiene que existir y estar activo, salvo que sea uno de los que la convocatoria ya tenía (un
         * equipaje desactivado después de elegirlo no impide editar el resto de la convocatoria).
         */
        private void validarEquipaje(
                        Long equipacionId,
                        Long mochilaId,
                        Convocatoria actual) {

                if (mochilaId != null && equipacionId == null) {
                        throw new IllegalArgumentException(
                                        "No se puede indicar el equipaje de la mochila sin indicar el equipaje con el que van vestidos");
                }

                if (mochilaId != null && mochilaId.equals(equipacionId)) {
                        throw new IllegalArgumentException(
                                        "El equipaje de la mochila tiene que ser distinto del equipaje con el que van vestidos");
                }

                for (Long id : new Long[] { equipacionId, mochilaId }) {

                        if (id == null) {
                                continue;
                        }

                        Equipacion equipacion = id <= 0 ? null : equipacionDao.obtenerPorId(id);

                        if (equipacion == null) {
                                throw new IllegalArgumentException(
                                                "El equipaje indicado no existe");
                        }

                        boolean yaLoTenia = actual != null
                                        && (id.equals(actual.getEquipacionId()) || id.equals(actual.getEquipacionMochilaId()));

                        if (!equipacion.isActiva() && !yaLoTenia) {
                                throw new IllegalArgumentException(
                                                "El equipaje " + equipacion.getNombre()
                                                                + " está desactivado y no se puede elegir");
                        }
                }
        }

        /*
         * Un aviso por usuario (el jugador y sus familiares) y jugador, sin avisar a quien
         * ha justificado su ausencia a este partido. La app navega según data["tipo"]
         * (no según el título): con convocatoriaId abre el detalle de la convocatoria, y
         * fechaPartido (yyyy-MM-dd) lleva al calendario en ese día.
         */
        /** Genera el aviso para un jugador y un destinatario, en el idioma de este. */
        @FunctionalInterface
        private interface GeneradorAviso {
                TextoAviso generar(String jugador, boolean esJugador, String idioma);
        }

        private void avisar(
                        Convocatoria convocatoria,
                        Collection<Long> jugadoresIds,
                        String tipoPush,
                        boolean conConvocatoriaId,
                        Long partidoAnteriorId,
                        java.time.LocalDate fechaPartidoPush,
                        GeneradorAviso mensaje) {

                if (jugadoresIds.isEmpty()) {
                        return;
                }

                Set<Long> ausentes = new HashSet<>();

                if (convocatoria.getPartidoId() != null) {

                        for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                        .obtenerPorPartido(convocatoria.getPartidoId())) {

                                ausentes.add(justificacion.getJugadorId());
                        }
                }

                // Al cambiar de partido, también quien justificó su ausencia al anterior.
                if (partidoAnteriorId != null) {

                        for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                        .obtenerPorPartido(partidoAnteriorId)) {

                                ausentes.add(justificacion.getJugadorId());
                        }
                }

                for (Long jugadorId : jugadoresIds) {

                        if (ausentes.contains(jugadorId)) {
                                continue;
                        }

                        String jugador = equipoGestionDao.obtenerNombreJugador(
                                        jugadorId);

                        List<Long> usuariosDirectos = equipoGestionDao.obtenerUsuariosPorJugador(
                                        jugadorId);

                        List<Long> usuariosFamiliares = equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(
                                        jugadorId);

                        Set<Long> destinatariosUnicos = new HashSet<>();

                        destinatariosUnicos.addAll(usuariosDirectos);
                        destinatariosUnicos.addAll(usuariosFamiliares);

                        for (Long usuarioId : destinatariosUnicos) {

                                Comunicacion comunicacion = new Comunicacion();

                                TextoAviso aviso = mensaje.generar(
                                                jugador,
                                                usuariosDirectos.contains(usuarioId),
                                                idiomas.idiomaDe(usuarioId));

                                comunicacion.setTitulo(aviso.titulo());
                                comunicacion.setContenido(aviso.contenido());

                                Map<String, String> datosPush = new HashMap<>();

                                datosPush.put("tipo", tipoPush);

                                if (conConvocatoriaId) {
                                        datosPush.put("convocatoriaId", String.valueOf(convocatoria.getId()));

                                        if (convocatoria.getPartidoId() != null) {
                                                datosPush.put("partidoId", String.valueOf(convocatoria.getPartidoId()));
                                        }
                                }

                                java.time.LocalDate fechaData = fechaPartidoPush != null ? fechaPartidoPush : convocatoria.getFechaPartido();

                                if (fechaData != null) {
                                        datosPush.put("fechaPartido", fechaData.toString());
                                }

                                comunicacionService.crearPrivada(
                                                comunicacion,
                                                List.of(usuarioId),
                                                convocatoria.getUsuarioEntrenadorId() != null
                                                                ? convocatoria.getUsuarioEntrenadorId()
                                                                : usuarioId,
                                                new OpcionesPushComunicacion(null, datosPush));
                        }
                }
        }

        private static String textoFecha(Convocatoria convocatoria) {
                return convocatoria.getFechaPartido() != null
                                ? convocatoria.getFechaPartido().toString()
                                : "";
        }

        private static String textoOVacio(String valor) {
                return valor != null ? valor : "";
        }

        /*
         * Avisos al editar, comparando la lista de convocados y los datos de antes y de
         * después (nunca a quien tiene la ausencia justificada, ver avisar):
         *  - añadidos: el aviso normal de convocatoria;
         *  - quitados: "Ya no estás convocado...";
         *  - los que siguen: solo si ha cambiado la hora de citación, el lugar o el partido.
         * Sin ningún cambio relevante no se avisa a nadie.
         */
        private void avisarCambios(
                        Convocatoria convocatoria,
                        DatosConvocatoria antes,
                        Set<Long> idsAntes,
                        List<Long> idsDespues,
                        List<Long> invitadosQueSiguen) {

                List<Long> anadidos = new ArrayList<>();
                List<Long> quitados = new ArrayList<>();
                List<Long> siguen = new ArrayList<>();
                List<Long> avisarCambio = new ArrayList<>();

                for (Long id : idsDespues) {
                        (idsAntes.contains(id) ? siguen : anadidos).add(id);
                }

                for (Long id : idsAntes) {

                        if (!idsDespues.contains(id)) {
                                quitados.add(id);
                        }
                }

                generarNotificaciones(convocatoria, anadidos);

                avisarCambio.addAll(siguen);
                avisarCambio.addAll(invitadosQueSiguen);

                avisar(
                                convocatoria,
                                quitados,
                                TIPO_PUSH_CONVOCATORIA,
                                false,
                                !Objects.equals(antes.partidoId(), convocatoria.getPartidoId()) ? antes.partidoId() : null,
                                antes.fechaPartido(),
                                (jugador, esJugador, idioma) -> new TextoAviso(
                                                textos.texto(idioma, "convocatoria.quitado.titulo"),
                                                textos.texto(idioma,
                                                                esJugador ? "convocatoria.quitado.jugador"
                                                                                : "convocatoria.quitado.familia",
                                                                "jugador", jugador,
                                                                "rival", textoOVacio(antes.rival()),
                                                                "fecha", textos.fecha(idioma, antes.fechaPartido()))));

                boolean cambiaHora = !Objects.equals(antes.horaConvocatoria(), convocatoria.getHoraConvocatoria());
                boolean cambiaLugar = !Objects.equals(antes.lugarConvocatoria(), convocatoria.getLugarConvocatoria());
                boolean cambiaPartido = !Objects.equals(antes.partidoId(), convocatoria.getPartidoId());
                boolean cambiaElEquipaje = !Objects.equals(antes.equipacionId(), convocatoria.getEquipacionId())
                                || !Objects.equals(antes.equipacionMochilaId(), convocatoria.getEquipacionMochilaId());

                if (!cambiaHora && !cambiaLugar && !cambiaPartido && !cambiaElEquipaje) {
                        return;
                }

                Equipacion principal = equipacionPorId(convocatoria.getEquipacionId());
                Equipacion mochila = equipacionPorId(convocatoria.getEquipacionMochilaId());

                // Con hora, lugar o partido distintos en la misma edición, un solo aviso que lo diga todo.
                String claveTitulo = cambiaHora || cambiaLugar || cambiaPartido
                                ? "convocatoria.cambio.titulo"
                                : "convocatoria.cambioEquipaje.titulo";

                avisar(
                                convocatoria,
                                avisarCambio,
                                TIPO_PUSH_CONVOCATORIA,
                                true,
                                null,
                                null,
                                (jugador, esJugador, idioma) -> {

                                        List<String> cambios = new ArrayList<>();

                                        if (cambiaHora) {
                                                cambios.add(textos.texto(idioma, "cambio.hora",
                                                                "hora", TextosConvocatoria.hora(textos, convocatoria.getHoraConvocatoria())));
                                        }

                                        if (cambiaLugar) {
                                                cambios.add(textos.texto(idioma, "cambio.lugar",
                                                                "lugar", textoOVacio(convocatoria.getLugarConvocatoria())));
                                        }

                                        if (cambiaPartido) {
                                                cambios.add(textos.texto(idioma, "cambio.partido",
                                                                "fecha", textos.fecha(idioma, convocatoria.getFechaPartido()),
                                                                "hora", TextosConvocatoria.hora(textos, convocatoria.getHoraPartido())));
                                        }

                                        if (cambiaElEquipaje) {
                                                cambios.add(textos.texto(idioma, "cambio.equipaje"));
                                        }

                                        String equipaje = "";

                                        if (cambiaElEquipaje) {
                                                equipaje = TextosConvocatoria.parrafoEquipaje(textos, idioma, principal, mochila);

                                                if (equipaje.isEmpty()) {
                                                        equipaje = textos.texto(idioma, "equipaje.ninguno");
                                                }
                                        }

                                        String base = textos.texto(idioma,
                                                        esJugador ? "convocatoria.cambio.jugador" : "convocatoria.cambio.familia",
                                                        "jugador", jugador,
                                                        "rival", textoOVacio(convocatoria.getRival()),
                                                        "cambios", String.join("; ", cambios));

                                        return new TextoAviso(
                                                        textos.texto(idioma, claveTitulo),
                                                        TextosConvocatoria.componer(textos, idioma, base, equipaje));
                                });
        }

        /* Lo que se compara al editar: la convocatoria tal como estaba antes del PUT. */
        private record DatosConvocatoria(
                        Long partidoId,
                        String rival,
                        java.time.LocalDate fechaPartido,
                        String horaConvocatoria,
                        String lugarConvocatoria,
                        Long equipacionId,
                        Long equipacionMochilaId) {
        }

        @Override
        @Transactional
        public ConvocatoriaResponse actualizar(
                        Long usuarioAppId,
                        Long convocatoriaId,
                        ConvocatoriaGuardarRequest request) {

                validarUsuario(usuarioAppId);
                validarRequestActualizacion(request);

                if (convocatoriaId == null || convocatoriaId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID de la convocatoria no es válido");
                }

                Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocatoriaId);

                if (convocatoria == null) {
                        throw new IllegalArgumentException(
                                        "La convocatoria no existe");
                }

                // La convocatoria tal como estaba, para avisar solo de lo que cambie.
                DatosConvocatoria antes = new DatosConvocatoria(
                                convocatoria.getPartidoId(),
                                convocatoria.getRival(),
                                convocatoria.getFechaPartido(),
                                limpiar(convocatoria.getHoraConvocatoria()),
                                limpiar(convocatoria.getLugarConvocatoria()),
                                convocatoria.getEquipacionId(),
                                convocatoria.getEquipacionMochilaId());

                /*
                 * Comprobamos que el usuario puede gestionar el equipo
                 * ACTUAL de la convocatoria (antes de aplicar ningún
                 * cambio del request).
                 */
                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                convocatoria.getEquipoId())) {

                        throw new SecurityException(
                                        "No tienes permiso para actualizar esta convocatoria");
                }

                Partido partido = partidoDao.obtenerPorId(request.getPartidoId());

                if (partido == null) {
                        throw new IllegalArgumentException(
                                        "El partido no existe");
                }

                /*
                 * No permitimos cambiar el equipo de la convocatoria: el
                 * partido elegido al re-apuntar la convocatoria tiene que
                 * seguir siendo del mismo equipo.
                 */
                if (!convocatoria.getEquipoId().equals(partido.getEquipoId())) {
                        throw new IllegalArgumentException(
                                        "No se puede cambiar el equipo de la convocatoria");
                }

                // Se bloquea el partido ANTES de comprobar si ya tiene convocatoria
                // (como en crear): serializa con otras altas o ediciones sobre el mismo
                // partido y con quien justifica su ausencia a él.
                partidoDao.bloquearPorId(partido.getId());

                if (convocatoriaDao.existePorPartido(
                                partido.getId(),
                                convocatoriaId)) {

                        throw new IllegalArgumentException(
                                        "Ya existe una convocatoria para este partido");
                }

                boolean cambiaElPartido = !partido.getId().equals(convocatoria.getPartidoId());

                // Re-apuntar la convocatoria a otro partido: el nuevo tiene que poder convocarse.
                if (cambiaElPartido) {
                        validarPartidoConvocable(partido);

                        // Los invitados dependen del día del partido: hay que quitarlos antes de cambiarlo.
                        invitadosService.comprobarSinInvitadosAlCambiarPartido(convocatoriaId);
                }

                /*
                 * Si el request trae la lista de jugadores, la convocatoria
                 * pasa a tener esos: salen (solo su fila) los que ya no
                 * están, entran los nuevos y los demás se conservan. Sin
                 * lista (null), los convocados no se tocan. Los invitados de
                 * otros equipos no entran aquí: van aparte (invitadosIds).
                 */
                // Los invitados autorizados antes de esta edición: los que sigan así reciben los avisos de cambio.
                Set<Long> invitadosAutorizadosAntes = convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoriaId).stream()
                                .filter(ConvocatoriaJugador::isInvitado)
                                .filter(convocado -> convocado.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO)
                                .map(ConvocatoriaJugador::getJugadorId)
                                .collect(java.util.stream.Collectors.toSet());

                List<ConvocatoriaJugador> actuales = convocatoriaJugadorDao.obtenerPorConvocatoria(
                                convocatoriaId).stream()
                                .filter(actual -> !actual.isInvitado())
                                .toList();

                Set<Long> idsActuales = new HashSet<>();

                for (ConvocatoriaJugador actual : actuales) {
                        idsActuales.add(actual.getJugadorId());
                }

                List<Long> nuevosIds = request.getJugadoresIds();

                if (nuevosIds != null) {

                        if (nuevosIds.isEmpty()) {
                                throw new IllegalArgumentException(
                                                "Debes seleccionar al menos un jugador");
                        }

                        validarJugadoresSeleccionados(
                                        nuevosIds,
                                        equipoGestionDao.obtenerJugadoresPorEquipo(
                                                        convocatoria.getEquipoId()));

                        // Mantener a uno que ya estaba se permite aunque haya justificado su
                        // ausencia, pero solo si el partido no cambia: sus justificaciones eran
                        // de otro partido, y con el nuevo se rechaza a quien esté justificado.
                        rechazarJustificados(
                                        partido.getId(),
                                        nuevosIds,
                                        cambiaElPartido ? Set.<Long>of() : idsActuales);

                        // A quien entra ahora no se le puede convocar si ya está autorizado como invitado ese día.
                        invitadosService.comprobarNoInvitadoEseDia(
                                        nuevosIds.stream()
                                                        .filter(id -> !idsActuales.contains(id))
                                                        .toList(),
                                        partido);
                }

                /*
                 * Actualizamos el partido vinculado y los datos propios
                 * de la convocatoria.
                 */
                convocatoria.setPartidoId(partido.getId());

                convocatoria.setHoraConvocatoria(
                                limpiar(request.getHoraConvocatoria()));

                convocatoria.setLugarConvocatoria(
                                limpiar(request.getLugarConvocatoria()));

                /*
                 * El equipaje solo se modifica si el cuerpo trae equipajeIndicado = true (la app nueva lo envía
                 * siempre): una versión que no lo conoce no trae los campos y no puede borrarlo al editar.
                 */
                if (request.isEquipajeIndicado()) {

                        validarEquipaje(
                                        request.getEquipacionId(),
                                        request.getEquipacionMochilaId(),
                                        convocatoria);

                        convocatoria.setEquipacionId(request.getEquipacionId());
                        convocatoria.setEquipacionMochilaId(request.getEquipacionMochilaId());
                }

                convocatoriaDao.actualizar(convocatoria);

                /*
                 * Sin lista de jugadores y con otro partido, los convocados actuales
                 * se revisan contra las justificaciones del partido NUEVO: quien esté
                 * justificado se quita de la convocatoria (haya o no disponibles) y se
                 * avisa al cuerpo técnico.
                 */
                if (nuevosIds == null && cambiaElPartido) {
                        quitarJustificadosDelPartidoNuevo(convocatoria, partido, actuales, usuarioAppId);
                }

                if (nuevosIds != null) {

                        for (Long actualId : idsActuales) {

                                if (!nuevosIds.contains(actualId)) {
                                        convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(
                                                        convocatoriaId,
                                                        actualId);
                                }
                        }

                        for (Long nuevoId : nuevosIds) {

                                if (!idsActuales.contains(nuevoId)) {

                                        ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();

                                        convocatoriaJugador.setConvocatoriaId(convocatoriaId);
                                        convocatoriaJugador.setJugadorId(nuevoId);

                                        convocatoriaJugadorDao.guardar(convocatoriaJugador);
                                }
                        }
                }

                aplicarDatosPartido(convocatoria, partido);

                /*
                 * Obtenemos los jugadores que ya están asociados
                 * a la convocatoria.
                 */
                List<ConvocatoriaJugador> jugadoresConvocados = convocatoriaJugadorDao.obtenerPorConvocatoria(
                                convocatoriaId);

                // Solo los del propio equipo: a los invitados los avisa InvitadosService cuando se autorizan.
                List<Long> jugadoresIds = jugadoresConvocados.stream()
                                .filter(convocado -> !convocado.isInvitado())
                                .map(ConvocatoriaJugador::getJugadorId)
                                .toList();

                /*
                 * Los jugadores que este cuerpo técnico acaba de quitar quedan libres: si estaban pidiéndoselos a
                 * este equipo para otro partido el mismo día, esas invitaciones pasan a autorizadas.
                 */
                if (nuevosIds != null) {

                        List<Long> quitados = idsActuales.stream()
                                        .filter(id -> !nuevosIds.contains(id))
                                        .toList();

                        invitadosService.alQuitarJugadoresPropios(
                                        usuarioAppId,
                                        convocatoria.getEquipoId(),
                                        antes.fechaPartido() != null ? antes.fechaPartido() : convocatoria.getFechaPartido(),
                                        quitados);
                }

                invitadosService.sincronizarInvitados(
                                usuarioAppId,
                                convocatoria,
                                partido,
                                request.getInvitadosIds());

                /*
                 * Avisamos solo de lo que ha cambiado: a los añadidos, a los
                 * quitados y, si ha cambiado la citación o el partido, a los que
                 * siguen.
                 */
                // Solo quien ya estaba autorizado y lo sigue estando: al que se autoriza o se quita en esta misma edición
                // ya le avisa InvitadosService con su propio texto. Los pendientes y rechazados no salen de aquí, y a quien
                // la familia ha marcado "no puede ir" lo descarta avisar().
                List<Long> invitadosQueSiguen = convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoriaId).stream()
                                .filter(ConvocatoriaJugador::isInvitado)
                                .filter(convocado -> convocado.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO)
                                .map(ConvocatoriaJugador::getJugadorId)
                                .filter(invitadosAutorizadosAntes::contains)
                                .toList();

                avisarCambios(
                                convocatoria,
                                antes,
                                idsActuales,
                                jugadoresIds,
                                invitadosQueSiguen);

                return construirResponse(convocatoria, null, usuarioAppId);
        }

        @Override
        public ConvocatoriaResponse obtenerPorId(
                        Long usuarioAppId,
                        Long convocatoriaId) {

                validarUsuario(usuarioAppId);

                if (convocatoriaId == null || convocatoriaId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID de la convocatoria no es válido");
                }

                Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocatoriaId);

                if (convocatoria == null) {
                        throw new IllegalArgumentException(
                                        "La convocatoria no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                convocatoria.getEquipoId())) {

                        throw new SecurityException(
                                        "No tienes permiso para consultar esta convocatoria");
                }

                return construirResponse(convocatoria, null, usuarioAppId);
        }

        @Override
        public List<ConvocatoriaResponse> obtenerPorEquipo(
                        Long usuarioAppId,
                        Long equipoId) {

                validarUsuario(usuarioAppId);

                if (equipoId == null || equipoId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID del equipo no es válido");
                }

                if (!equipoGestionDao.existeEquipo(equipoId)) {
                        throw new IllegalArgumentException(
                                        "El equipo no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                equipoId)) {

                        throw new SecurityException(
                                        "No tienes permiso para consultar este equipo");
                }

                List<Convocatoria> convocatorias = convocatoriaDao.obtenerPorEquipo(equipoId);

                List<ConvocatoriaResponse> respuesta = new ArrayList<>();

                // Los partidos de una vez, no uno por convocatoria.
                Map<Long, Partido> partidos = new HashMap<>();

                if (!convocatorias.isEmpty()) {
                        for (Partido p : partidoDao.obtenerPorEquipo(equipoId)) {
                                partidos.put(p.getId(), p);
                        }
                }

                // Solo las de partidos de la temporada activa (sin temporada activa, todas)
                RangoTemporada rango = RangoTemporada.de(temporadaDao.obtenerTemporadaActiva());

                for (Convocatoria convocatoria : convocatorias) {
                        Partido partido = partidos.get(convocatoria.getPartidoId());

                        if (partido != null && !rango.contiene(partido.getDia())) {
                                continue;
                        }

                        respuesta.add(
                                        construirResponse(convocatoria, partidos, usuarioAppId));
                }

                return respuesta;
        }

        @Override
        @Transactional
        public void eliminar(
                        Long usuarioAppId,
                        Long convocatoriaId) {

                validarUsuario(usuarioAppId);

                if (convocatoriaId == null || convocatoriaId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID de la convocatoria no es válido");
                }

                Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocatoriaId);

                if (convocatoria == null) {
                        throw new IllegalArgumentException(
                                        "La convocatoria no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                convocatoria.getEquipoId())) {

                        throw new SecurityException(
                                        "No tienes permiso para eliminar esta convocatoria");
                }

                // Serializa con quien justifica su ausencia a este partido y con su edición.
                if (convocatoria.getPartidoId() != null) {

                        partidoDao.bloquearPorId(convocatoria.getPartidoId());

                        validarEliminable(partidoDao.obtenerPorId(convocatoria.getPartidoId()));
                }

                // Solo los del propio equipo: a los invitados los avisa InvitadosService, con su propio texto.
                List<Long> convocados = convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoriaId).stream()
                                .filter(convocado -> !convocado.isInvitado())
                                .map(ConvocatoriaJugador::getJugadorId)
                                .toList();

                // Antes de borrar las filas: es donde está quién era invitado y en qué estado.
                if (convocatoria.getPartidoId() != null) {
                        invitadosService.alEliminarConvocatoria(
                                        convocatoria,
                                        partidoDao.obtenerPorId(convocatoria.getPartidoId()));
                }

                // Las justificaciones de ausencia al partido NO se borran: son del partido, no de la convocatoria.
                convocatoriaJugadorDao.eliminarPorConvocatoria(
                                convocatoriaId);

                convocatoriaDao.eliminar(convocatoriaId);

                // Los jugadores de esta convocatoria quedan libres para quien los pedía el mismo día.
                invitadosService.alQuitarJugadoresPropios(
                                usuarioAppId,
                                convocatoria.getEquipoId(),
                                convocatoria.getFechaPartido(),
                                convocados);

                avisar(
                                convocatoria,
                                convocados,
                                TIPO_PUSH_CONVOCATORIA,
                                false,
                                null,
                                null,
                                (jugador, esJugador, idioma) -> new TextoAviso(
                                                textos.texto(idioma, "convocatoria.anulada.titulo"),
                                                textos.texto(idioma,
                                                                esJugador ? "convocatoria.anulada.jugador"
                                                                                : "convocatoria.anulada.familia",
                                                                "jugador", jugador,
                                                                "rival", textoOVacio(convocatoria.getRival()),
                                                                "fecha", textos.fecha(idioma, convocatoria.getFechaPartido()))));
        }

        /*
         * Solo se anula la convocatoria de un partido que aún no tiene resultado y cuya
         * fecha es hoy (hora de Madrid, por el Clock) o posterior. Se lanza
         * IllegalStateException (400 en la API): IllegalArgumentException es "no existe" (404).
         */
        private void validarEliminable(Partido partido) {

                if (partido == null) {
                        return;
                }

                if (CriterioResultadoPartido.tieneResultado(partido)) {

                        throw new IllegalStateException(
                                        "No se puede eliminar la convocatoria: el partido ya tiene resultado");
                }

                java.time.LocalDate dia = PlazoJustificacionPartido.dia(partido);

                if (dia != null && dia.isBefore(java.time.LocalDate.now(clock))) {

                        throw new IllegalStateException(
                                        "No se puede eliminar la convocatoria: el partido ya se jugó"
                                                        + " (su fecha es anterior a hoy)");
                }
        }

        // partidos: ya cargados (listados); null = se consulta el partido de esta convocatoria.
        private ConvocatoriaResponse construirResponse(
                        Convocatoria convocatoria,
                        Map<Long, Partido> partidos,
                        Long usuarioAppId) {

                ConvocatoriaResponse response = new ConvocatoriaResponse();

                response.setId(convocatoria.getId());
                response.setEquipoId(convocatoria.getEquipoId());
                response.setEquipo(
                                equipoGestionDao.obtenerNombreEquipo(
                                                convocatoria.getEquipoId()));
                response.setPartidoId(convocatoria.getPartidoId());
                response.setRival(convocatoria.getRival());
                response.setCampo(convocatoria.getCampo());
                response.setFechaPartido(
                                convocatoria.getFechaPartido());
                response.setHoraPartido(
                                convocatoria.getHoraPartido());
                response.setHoraConvocatoria(
                                convocatoria.getHoraConvocatoria());
                response.setLugarConvocatoria(
                                convocatoria.getLugarConvocatoria());
                response.setUsuarioEntrenadorId(
                                convocatoria.getUsuarioEntrenadorId());
                response.setEquipacion(
                                EquipacionResponse.de(equipacionPorId(convocatoria.getEquipacionId())));
                response.setEquipacionMochila(
                                EquipacionResponse.de(equipacionPorId(convocatoria.getEquipacionMochilaId())));

                Partido partidoVinculado = convocatoria.getPartidoId() == null
                                ? null
                                : partidos != null
                                                ? partidos.get(convocatoria.getPartidoId())
                                                : partidoDao.obtenerPorId(convocatoria.getPartidoId());

                response.setPartidoConResultado(
                                partidoVinculado != null && CriterioResultadoPartido.tieneResultado(partidoVinculado));

                List<ConvocatoriaJugador> jugadores = convocatoriaJugadorDao.obtenerPorConvocatoria(
                                convocatoria.getId());

                List<ConvocatoriaJugadorResponse> jugadoresResponse = new ArrayList<>();

                Map<Long, JustificacionFaltaPartido> justificadas = new HashMap<>();

                if (convocatoria.getPartidoId() != null) {

                        for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                        .obtenerPorPartido(convocatoria.getPartidoId())) {

                                justificadas.put(justificacion.getJugadorId(), justificacion);
                        }
                }

                for (ConvocatoriaJugador jugador : jugadores) {

                        String nombre = equipoGestionDao.obtenerNombreJugador(
                                        jugador.getJugadorId());

                        JustificacionFaltaPartido ausencia = justificadas.get(jugador.getJugadorId());

                        ConvocatoriaJugadorResponse item = new ConvocatoriaJugadorResponse(
                                        jugador.getJugadorId(),
                                        nombre,
                                        ausencia != null,
                                        ausencia != null ? ausencia.getMotivo() : null);

                        if (jugador.isInvitado()) {

                                item.setInvitado(true);
                                item.setEquipoOrigenId(jugador.getEquipoOrigenId());
                                item.setEquipoOrigen(equipoGestionDao.obtenerNombreEquipo(jugador.getEquipoOrigenId()));
                                item.setEstadoInvitacion(jugador.getEstadoInvitacion().name());

                                // El motivo de la familia solo lo ve quien convocó al invitado.
                                Long invitador = jugador.getUsuarioInvitaId() != null
                                                ? jugador.getUsuarioInvitaId()
                                                : convocatoria.getUsuarioEntrenadorId();

                                if (!Objects.equals(invitador, usuarioAppId)) {
                                        item.setMotivoAusencia(null);
                                }
                        }

                        jugadoresResponse.add(item);
                }

                // Los invitados pendientes o rechazados salen aparte de los que ya juegan.
                jugadoresResponse.addAll(invitadosService.invitadosSinAutorizar(convocatoria.getId()));

                response.setJugadores(jugadoresResponse);

                return response;
        }

        private void quitarJustificadosDelPartidoNuevo(
                        Convocatoria convocatoria,
                        Partido partidoNuevo,
                        List<ConvocatoriaJugador> actuales,
                        Long usuarioAppId) {

                Set<Long> justificados = new HashSet<>();

                for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                .obtenerPorPartido(partidoNuevo.getId())) {

                        justificados.add(justificacion.getJugadorId());
                }

                List<String> nombres = new ArrayList<>();

                for (ConvocatoriaJugador actual : actuales) {

                        if (justificados.contains(actual.getJugadorId())) {

                                convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(
                                                convocatoria.getId(),
                                                actual.getJugadorId());

                                nombres.add(equipoGestionDao.obtenerNombreJugador(actual.getJugadorId()));
                        }
                }

                if (nombres.isEmpty()) {
                        return;
                }

                List<Long> destinatarios = new ArrayList<>(
                                equipoGestionDao.obtenerEntrenadoresPorEquipo(convocatoria.getEquipoId()));

                if (destinatarios.isEmpty()) {
                        destinatarios.addAll(equipoGestionDao.obtenerCoordinadores());
                }

                // Quien edita ya lo sabe: no se avisa a sí mismo.
                destinatarios.remove(usuarioAppId);

                if (destinatarios.isEmpty()) {
                        return;
                }

                String rival = textoOVacio(partidoNuevo.getRival());

                idiomas.porIdioma(destinatarios, (idioma, ids) -> {

                        Comunicacion comunicacion = new Comunicacion();

                        comunicacion.setTitulo(textos.texto(idioma, "convocatoria.quitadosAusencia.titulo"));
                        comunicacion.setContenido(
                                        textos.texto(idioma, "convocatoria.quitadosAusencia.texto",
                                                        "rival", rival,
                                                        "nombres", textos.lista(idioma, nombres)));

                        comunicacionService.crearPrivada(comunicacion, ids, usuarioAppId);
                });
        }

        /*
         * Un jugador con ausencia justificada a este partido no se puede
         * añadir a la convocatoria (sí mantener, si ya estaba en ella).
         */
        private void rechazarJustificados(
                        Long partidoId,
                        List<Long> jugadoresIds,
                        Set<Long> yaConvocados) {

                for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                .obtenerPorPartido(partidoId)) {

                        Long jugadorId = justificacion.getJugadorId();

                        if (jugadoresIds.contains(jugadorId) && !yaConvocados.contains(jugadorId)) {

                                throw new IllegalArgumentException(
                                                equipoGestionDao.obtenerNombreJugador(jugadorId)
                                                                + " ha justificado su ausencia a este partido"
                                                                + " y no se puede convocar");
                        }
                }
        }

        private void validarRequest(
                        ConvocatoriaGuardarRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "La petición no puede ser nula");
                }

                if (request.getPartidoId() == null
                                || request.getPartidoId() <= 0) {

                        throw new IllegalArgumentException(
                                        "El partido es obligatorio");
                }

                if (request.getHoraConvocatoria() == null
                                || request.getHoraConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "La hora de convocatoria es obligatoria");
                }

                if (request.getLugarConvocatoria() == null
                                || request.getLugarConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El lugar de convocatoria es obligatorio");
                }

                if (request.getJugadoresIds() == null
                                || request.getJugadoresIds().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Debes seleccionar al menos un jugador");
                }
        }

        private void validarRequestActualizacion(
                        ConvocatoriaGuardarRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "La petición no puede ser nula");
                }

                if (request.getPartidoId() == null
                                || request.getPartidoId() <= 0) {

                        throw new IllegalArgumentException(
                                        "El partido es obligatorio");
                }

                if (request.getHoraConvocatoria() == null
                                || request.getHoraConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "La hora de convocatoria es obligatoria");
                }

                if (request.getLugarConvocatoria() == null
                                || request.getLugarConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El lugar de convocatoria es obligatorio");
                }
        }

        private void validarJugadoresSeleccionados(
                        List<Long> jugadoresSeleccionados,
                        List<Long> jugadoresEquipo) {

                Set<Long> jugadoresEquipoSet = new HashSet<>(jugadoresEquipo);

                Set<Long> jugadoresSeleccionadosSet = new HashSet<>(jugadoresSeleccionados);

                if (jugadoresSeleccionados.size() != jugadoresSeleccionadosSet.size()) {

                        throw new IllegalArgumentException(
                                        "No se puede repetir un jugador");
                }

                for (Long jugadorId : jugadoresSeleccionados) {

                        if (jugadorId == null
                                        || jugadorId <= 0) {

                                throw new IllegalArgumentException(
                                                "Hay un jugador no válido");
                        }

                        if (!jugadoresEquipoSet.contains(jugadorId)) {

                                throw new SecurityException(
                                                "Uno de los jugadores no pertenece al equipo");
                        }
                }
        }

        /*
         * Solo se convoca para un partido que se va a jugar: no cancelado y con fecha de
         * hoy (hora de Madrid, por el Clock) o posterior.
         */
        private void validarPartidoConvocable(Partido partido) {

                if (partido.isCancelado()) {
                        throw new IllegalArgumentException(
                                        "El partido está cancelado: no se puede crear una convocatoria para él");
                }

                if (partido.getDia() != null) {

                        java.time.LocalDate dia = partido.getDia() instanceof java.sql.Date sqlDate
                                        ? sqlDate.toLocalDate()
                                        : partido.getDia().toInstant().atZone(clock.getZone()).toLocalDate();

                        if (dia.isBefore(java.time.LocalDate.now(clock))) {
                                throw new IllegalArgumentException(
                                                "El partido ya se jugó (su fecha es anterior a hoy): "
                                                                + "no se puede crear una convocatoria para él");
                        }
                }
        }

        private void validarUsuario(Long usuarioAppId) {

                if (usuarioAppId == null || usuarioAppId <= 0) {
                        throw new SecurityException(
                                        "Usuario no válido");
                }
        }

        private String limpiar(String valor) {

                if (valor == null) {
                        return null;
                }

                String resultado = valor.trim();

                return resultado.isEmpty()
                                ? null
                                : resultado;
        }
}
