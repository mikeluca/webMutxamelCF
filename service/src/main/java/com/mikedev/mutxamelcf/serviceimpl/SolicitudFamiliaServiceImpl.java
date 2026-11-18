package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.FamiliarDao;
import com.mikedev.mutxamelcf.dao.FamiliarJugadorDao;
import com.mikedev.mutxamelcf.dao.SolicitudFamiliaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.FamiliarJugador;
import com.mikedev.mutxamelcf.model.JugadorCandidato;
import com.mikedev.mutxamelcf.model.SolicitudFamilia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaJugador;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaRequest;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Caducada;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Coincidencia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.HijoMio;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.HijoRevision;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Mia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Revision;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;
import com.mikedev.mutxamelcf.service.CuentaAppService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@Service
public class SolicitudFamiliaServiceImpl implements SolicitudFamiliaService {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudFamiliaServiceImpl.class);

    /** El mismo mensaje que la activación para cualquier motivo por el que el código no sirve (SEC-02). */
    static final String MENSAJE_CODIGO_INVALIDO = "El código no es válido o ha caducado.";

    private static final Set<String> PARENTESCOS = Set.of(SolicitudFamilia.MADRE, SolicitudFamilia.PADRE,
            SolicitudFamilia.TUTOR, SolicitudFamilia.OTRO);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Pattern TELEFONO = Pattern.compile("^[0-9+()\\-. ]{6,20}$");

    // Tamaños de las columnas.
    private static final int MAX_NOMBRE = 100;
    private static final int MAX_APELLIDOS = 150;
    private static final int MAX_EMAIL = 150;
    private static final int MAX_EQUIPO = 100;
    private static final int MAX_MOTIVO = 500;
    private static final int MAX_BUSQUEDA = 20;

    private final SolicitudFamiliaDao dao;
    private final UsuarioAppService usuarioAppService;
    private final UsuarioAppVinculoDao usuarioAppVinculoDao;
    private final FamiliarDao familiarDao;
    private final FamiliarJugadorDao familiarJugadorDao;
    private final EquipoGestionDao equipoGestionDao;
    private final CuentaAppService cuentaAppService;
    private final AvisosSolicitudFamilia avisos;
    private final Clock clock;

    public SolicitudFamiliaServiceImpl(
            SolicitudFamiliaDao dao,
            UsuarioAppService usuarioAppService,
            UsuarioAppVinculoDao usuarioAppVinculoDao,
            FamiliarDao familiarDao,
            FamiliarJugadorDao familiarJugadorDao,
            EquipoGestionDao equipoGestionDao,
            CuentaAppService cuentaAppService,
            AvisosSolicitudFamilia avisos,
            Clock clock) {

        this.dao = dao;
        this.usuarioAppService = usuarioAppService;
        this.usuarioAppVinculoDao = usuarioAppVinculoDao;
        this.familiarDao = familiarDao;
        this.familiarJugadorDao = familiarJugadorDao;
        this.equipoGestionDao = equipoGestionDao;
        this.cuentaAppService = cuentaAppService;
        this.avisos = avisos;
        this.clock = clock;
    }

    // ==================================================================
    // Familiar, sin sesión
    // ==================================================================

    @Override
    @Transactional
    public Registro registrarPublica(SolicitudFamiliaRequest request) {

        Datos d = validar(request, true);
        String idioma = d.idioma;

        UsuarioApp existente = usuarioAppService.obtenerPorEmail(d.email);

        if (existente != null && !existente.isEliminada()) {

            // Una cuenta activa, o una que el club desactivó: que inicie sesión (o hable con el club). Nada más.
            if (existente.isActivo() || existente.getFechaActivacion() != null) {
                return new Registro(Resultado.CUENTA_EXISTENTE, null, null, null, null);
            }

            SolicitudFamilia abierta = dao.obtenerAbiertaDeUsuario(existente.getId());

            // La misma persona repite la solicitud antes de confirmar el email: se sustituyen los datos y va un código nuevo.
            if (abierta != null && SolicitudFamilia.PENDIENTE_EMAIL.equals(abierta.estado())) {

                dao.reemplazarDatos(abierta.id(), solicitudNueva(d, existente.getId(), SolicitudFamilia.PENDIENTE_EMAIL),
                        hijos(d));

                guardarIdioma(existente.getId(), idioma);

                String codigo = usuarioAppService.generarTokenActivacion(existente.getId());

                return new Registro(Resultado.CODIGO_ENVIADO, d.email, d.nombre, codigo, idioma);
            }

            // Una invitación del club sin canjear: que use "Activar cuenta".
            return new Registro(Resultado.INVITACION_PENDIENTE, null, null, null, null);
        }

        UsuarioApp usuario = new UsuarioApp();
        usuario.setEmail(d.email);
        usuario.setNombre(d.nombre);
        usuario.setApellidos(d.apellidos);

        int usuarioId = usuarioAppService.crearUsuario(usuario);

        guardarIdioma(usuarioId, idioma);

        String codigo = usuarioAppService.generarTokenActivacion(usuarioId);

        dao.insertar(solicitudNueva(d, usuarioId, SolicitudFamilia.PENDIENTE_EMAIL), hijos(d));

        logger.info("Solicitud de familia registrada (pendiente de confirmar el email): usuarioAppId={}", usuarioId);

        return new Registro(Resultado.CODIGO_ENVIADO, d.email, d.nombre, codigo, idioma);
    }

    @Override
    @Transactional
    public Registro reenviarCodigo(String email) {

        UsuarioApp usuario = usuarioAppService.obtenerPorEmail(normalizarEmail(email));

        if (usuario == null || usuario.isEliminada() || usuario.isActivo()) {
            return null;
        }

        SolicitudFamilia abierta = dao.obtenerAbiertaDeUsuario(usuario.getId());

        if (abierta == null || !SolicitudFamilia.PENDIENTE_EMAIL.equals(abierta.estado())) {
            return null;
        }

        String codigo = usuarioAppService.generarTokenActivacion(usuario.getId());

        return new Registro(Resultado.CODIGO_ENVIADO, usuario.getEmail(), abierta.nombre(), codigo,
                usuarioAppService.obtenerIdioma(usuario.getId()));
    }

    // Sin @Transactional: activarCuenta conserva el intento gastado aunque falle (ver UsuarioAppServiceImpl).
    @Override
    public UsuarioApp verificarEmail(String email, String codigo, String password) {

        String normalizado = normalizarEmail(email);
        UsuarioApp usuario = normalizado.isEmpty() ? null : usuarioAppService.obtenerPorEmail(normalizado);
        SolicitudFamilia abierta = usuario == null ? null : dao.obtenerAbiertaDeUsuario(usuario.getId());

        // Solo sirve para una solicitud sin confirmar: no es otra puerta para activar invitaciones del club.
        if (abierta == null || !SolicitudFamilia.PENDIENTE_EMAIL.equals(abierta.estado())) {
            throw new IllegalArgumentException(MENSAJE_CODIGO_INVALIDO);
        }

        UsuarioApp activada = usuarioAppService.activarCuenta(normalizado, codigo, password);

        confirmar(abierta);

        return activada;
    }

    @Override
    public void confirmarSiProcede(int usuarioAppId) {

        SolicitudFamilia abierta = dao.obtenerAbiertaDeUsuario(usuarioAppId);

        if (abierta != null && SolicitudFamilia.PENDIENTE_EMAIL.equals(abierta.estado())) {
            confirmar(abierta);
        }
    }

    private void confirmar(SolicitudFamilia abierta) {

        if (dao.confirmarEmail(abierta.id()) == 1) {

            logger.info("Solicitud de familia confirmada, pendiente de revisar: solicitudId={}", abierta.id());

            avisarNuevaSolicitud(abierta.id(), abierta.usuarioAppId());
        }
    }

    // ==================================================================
    // Familiar, con sesión
    // ==================================================================

    @Override
    @Transactional
    public Mia solicitarConSesion(int usuarioAppId, SolicitudFamiliaRequest request) {

        UsuarioApp usuario = usuarioAppService.obtenerPorId(usuarioAppId);

        if (usuario == null || !usuario.isActivo() || usuario.isEliminada()) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        if (dao.obtenerAbiertaDeUsuario(usuarioAppId) != null) {
            throw new IllegalStateException("Ya tienes una solicitud pendiente de revisar");
        }

        Datos d = validar(conDatosDeLaCuenta(request, usuario), false);

        long id = dao.insertar(solicitudNueva(d, usuarioAppId, SolicitudFamilia.PENDIENTE), hijos(d));

        logger.info("Solicitud de familia con sesión: usuarioAppId={}, solicitudId={}", usuarioAppId, id);

        avisarNuevaSolicitud(id, (long) usuarioAppId);

        return aMia(dao.obtenerPorId(id));
    }

    /** Con sesión no se pide el email (es el de la cuenta) y el nombre sale de la cuenta si no se escribe. */
    private static SolicitudFamiliaRequest conDatosDeLaCuenta(SolicitudFamiliaRequest r, UsuarioApp usuario) {

        if (r == null) {
            throw new IllegalArgumentException("La petición no puede ser nula");
        }

        String nombre = hayTexto(r.nombre()) ? r.nombre() : usuario.getNombre();
        String apellidos = hayTexto(r.apellidos()) ? r.apellidos() : usuario.getApellidos();

        return new SolicitudFamiliaRequest(nombre, apellidos, usuario.getEmail(), r.telefono(), r.parentesco(),
                r.hijos(), r.aceptaPrivacidad(), r.declaraRepresentante(), r.idioma());
    }

    @Override
    public List<Mia> misSolicitudes(int usuarioAppId) {

        // La cuenta ya está activa: si se activó con "Activar cuenta" en vez de con la verificación de la solicitud,
        // la solicitud sigue en PENDIENTE_EMAIL y se confirma aquí (el código era el suyo).
        confirmarSiProcede(usuarioAppId);

        List<Mia> lista = new ArrayList<>();

        for (SolicitudFamilia s : dao.obtenerPorUsuario(usuarioAppId)) {
            if (!SolicitudFamilia.CADUCADA.equals(s.estado())) {
                lista.add(aMia(s));
            }
        }

        return lista;
    }

    private Mia aMia(SolicitudFamilia s) {

        List<HijoMio> hijos = new ArrayList<>();

        for (SolicitudFamiliaJugador h : dao.obtenerHijos(s.id())) {
            hijos.add(new HijoMio(h.id(), h.nombre(), h.apellidos(), h.equipoTexto(), h.estado()));
        }

        return new Mia(s.id(), s.estado(), s.parentesco(), s.fechaSolicitud(), s.fechaRevision(), s.motivoRechazo(),
                hijos);
    }

    // ==================================================================
    // Quien revisa
    // ==================================================================

    @Override
    public List<Revision> pendientes(Revisor revisor) {

        List<JugadorCandidato> activos = dao.listarJugadoresActivos();
        Permisos permisos = new Permisos(revisor);
        List<Revision> lista = new ArrayList<>();

        for (SolicitudFamilia s : dao.listarPorEstado(SolicitudFamilia.PENDIENTE)) {

            Revision revision = aRevision(s, activos, permisos, !permisos.veTodo());

            if (revision != null) {
                lista.add(revision);
            }
        }

        return lista;
    }

    @Override
    public int contarPendientes(Revisor revisor) {
        return pendientes(revisor).size();
    }

    @Override
    public List<Revision> historico(Revisor revisor) {

        if (!revisor.esPanel()) {
            throw new SecurityException("El histórico solo está en el panel web");
        }

        LocalDateTime desde = LocalDateTime.now(clock).minusDays(DIAS_HASTA_CADUCAR);
        List<JugadorCandidato> activos = dao.listarJugadoresActivos();
        Permisos permisos = new Permisos(revisor);
        List<Revision> lista = new ArrayList<>();

        for (SolicitudFamilia s : dao.listarRevisadasDesde(desde)) {

            Revision revision = aRevision(s, activos, permisos, false);

            if (revision != null) {
                lista.add(revision);
            }
        }

        return lista;
    }

    @Override
    public List<Coincidencia> buscarJugadores(Revisor revisor, String texto) {

        Permisos permisos = new Permisos(revisor);
        List<JugadorCandidato> visibles = new ArrayList<>();

        for (JugadorCandidato j : dao.listarJugadoresActivos()) {
            if (permisos.puedeVerJugador(j)) {
                visibles.add(j);
            }
        }

        List<Coincidencia> resultado = new ArrayList<>();

        for (JugadorCandidato j : CoincidenciasJugador.buscarPorTexto(texto, visibles, MAX_BUSQUEDA)) {
            resultado.add(aCoincidencia(j, false));
        }

        return resultado;
    }

    @Override
    @Transactional
    public Revision aprobarHijo(Revisor revisor, long hijoId, long jugadorId) {

        Contexto c = cargar(revisor, hijoId);

        JugadorCandidato jugador = c.activos.stream().filter(j -> j.id() == jugadorId).findFirst().orElse(null);

        if (jugador == null) {
            throw new IllegalArgumentException("El jugador no existe o no está activo");
        }

        if (!c.permisos.puedeVerJugador(jugador)) {
            throw new SecurityException("Ese jugador no es de un equipo que puedas gestionar");
        }

        SolicitudFamilia s = c.solicitud;

        Familiar ficha = fichaDeFamiliar(s);

        vincularCuenta(s.usuarioAppId().intValue(), ficha);

        if (!familiarJugadorDao.existeRelacion(ficha.getId(), jugadorId)) {
            familiarJugadorDao.guardarFamiliarJugador(
                    new FamiliarJugador(null, ficha.getId(), jugadorId, textoParentesco(s.parentesco()), 0));
        }

        dao.aprobarHijo(hijoId, jugadorId);

        cerrarSiTodoRevisado(s, revisor);

        String nombreJugador = (jugador.nombre() + " " + jugador.apellidos()).trim();

        despuesDelCommit(() -> avisos.avisarAprobada(s.usuarioAppId(), nombreJugador, s.id()));

        logger.info("Hijo de solicitud de familia aprobado: solicitudId={}, hijoId={}, jugadorId={}", s.id(), hijoId,
                jugadorId);

        return aRevision(dao.obtenerPorId(s.id()), c.activos, c.permisos, false);
    }

    @Override
    @Transactional
    public Revision rechazarHijo(Revisor revisor, long hijoId, String motivo) {

        Contexto c = cargar(revisor, hijoId);

        String limpio = motivo == null ? "" : motivo.trim();

        if (limpio.length() > MAX_MOTIVO) {
            throw new IllegalArgumentException("El motivo no puede pasar de " + MAX_MOTIVO + " caracteres");
        }

        SolicitudFamilia s = c.solicitud;
        String guardado = limpio.isEmpty() ? null : limpio;

        dao.rechazarHijo(hijoId);

        if (guardado != null) {
            dao.actualizarMotivo(s.id(), unirMotivos(dao.obtenerPorId(s.id()).motivoRechazo(), guardado));
        }

        cerrarSiTodoRevisado(s, revisor);

        String nombreHijo = (c.hijo.nombre() + " " + c.hijo.apellidos()).trim();

        despuesDelCommit(() -> avisos.avisarRechazada(s.usuarioAppId(), nombreHijo, guardado, s.id()));

        logger.info("Hijo de solicitud de familia rechazado: solicitudId={}, hijoId={}", s.id(), hijoId);

        return aRevision(dao.obtenerPorId(s.id()), c.activos, c.permisos, false);
    }

    /** El hijo, su solicitud y los permisos de quien revisa, ya comprobados. */
    private record Contexto(SolicitudFamiliaJugador hijo, SolicitudFamilia solicitud, List<JugadorCandidato> activos,
            Permisos permisos) {
    }

    private Contexto cargar(Revisor revisor, long hijoId) {

        SolicitudFamiliaJugador hijo = dao.obtenerHijo(hijoId);

        if (hijo == null) {
            throw new IllegalArgumentException("La solicitud no existe");
        }

        SolicitudFamilia solicitud = dao.obtenerPorId(hijo.solicitudId());

        if (solicitud == null || !SolicitudFamilia.PENDIENTE.equals(solicitud.estado())) {
            throw new IllegalStateException("La solicitud ya no está pendiente de revisar");
        }

        if (!SolicitudFamiliaJugador.PENDIENTE.equals(hijo.estado())) {
            throw new IllegalStateException("Este hijo ya está revisado");
        }

        List<JugadorCandidato> activos = dao.listarJugadoresActivos();
        Permisos permisos = new Permisos(revisor);

        if (!permisos.veTodo() && visibles(hijo, activos, permisos).isEmpty()) {
            throw new SecurityException("No puedes revisar esta solicitud");
        }

        return new Contexto(hijo, solicitud, activos, permisos);
    }

    /** Tras revisar un hijo: si ya no queda ninguno pendiente, la solicitud pasa a APROBADA (alguno aprobado) o RECHAZADA. */
    private void cerrarSiTodoRevisado(SolicitudFamilia s, Revisor revisor) {

        List<SolicitudFamiliaJugador> hijos = dao.obtenerHijos(s.id());

        if (hijos.stream().anyMatch(h -> SolicitudFamiliaJugador.PENDIENTE.equals(h.estado()))) {
            return;
        }

        boolean algunoAprobado = hijos.stream().anyMatch(h -> SolicitudFamiliaJugador.APROBADO.equals(h.estado()));

        String estado = algunoAprobado ? SolicitudFamilia.APROBADA : SolicitudFamilia.RECHAZADA;

        // El motivo (si lo hubo al rechazar algún hijo) ya está en la solicitud: los hijos no tienen el suyo.
        SolicitudFamilia actual = dao.obtenerPorId(s.id());

        dao.cerrar(s.id(), estado, actual.motivoRechazo(), LocalDateTime.now(clock), revisor.usuarioAppId(), revisor.usuarioPanel());
    }


    /** Los motivos de los hijos rechazados de una misma solicitud, sin repetir y como mucho 500 caracteres. */
    static String unirMotivos(String anterior, String nuevo) {

        Set<String> motivos = new LinkedHashSet<>();

        for (String m : new String[] { anterior, nuevo }) {
            if (m != null && !m.isBlank()) {
                for (String parte : m.split(" / ")) {
                    motivos.add(parte.trim());
                }
            }
        }

        String unido = String.join(" / ", motivos);

        return unido.substring(0, Math.min(unido.length(), MAX_MOTIVO));
    }
    // ---- la ficha de familiar y el vínculo de la cuenta --------------------------------------------------------------

    /** La ficha con el mismo email o teléfono (si ya existe se usa esa) o una nueva. */
    private Familiar fichaDeFamiliar(SolicitudFamilia s) {

        Familiar existente = familiarDao.obtenerPorEmailOTelefono(s.email(), s.telefono());

        if (existente != null) {
            return existente;
        }

        Familiar nueva = new Familiar();
        nueva.setNombre(s.nombre());
        nueva.setApellidos(s.apellidos());
        nueva.setEmail(s.email());
        nueva.setTelefono(s.telefono());
        nueva.setRecibeInfoClub(0);
        nueva.setWhatsappActivo(0);

        if (!familiarDao.guardarFamiliar(nueva) || nueva.getId() == null) {
            throw new IllegalStateException("No se ha podido crear la ficha del familiar");
        }

        return nueva;
    }

    /**
     * Vincula la cuenta con la ficha y le da el rol FAMILIAR con la misma lógica que las invitaciones del club
     * ({@code UsuarioAppService.agregarVinculosAUsuarioExistente}). Si ya estaba vinculada con esa ficha (otro hijo de la
     * misma solicitud, o una aprobación anterior) no hace nada más que asegurar el rol.
     */
    private void vincularCuenta(int usuarioAppId, Familiar ficha) {

        boolean yaVinculada = usuarioAppVinculoDao.obtenerVinculos(usuarioAppId).stream()
                .anyMatch(v -> "FAMILIAR".equals(v.getTipo()) && ficha.getId().equals(v.getPersonaId()));

        if (!yaVinculada) {

            if (usuarioAppVinculoDao.familiarTieneCuenta(ficha.getId())) {
                throw new IllegalStateException("La ficha de familiar con ese email o teléfono ya está vinculada a otra "
                        + "cuenta de la app. Revísala en Familiares antes de aprobar");
            }

            AnadirVinculosRequest vinculo = new AnadirVinculosRequest();
            vinculo.setVinculos(List.of(new VinculoSolicitado("FAMILIAR", ficha.getId())));

            usuarioAppService.agregarVinculosAUsuarioExistente(usuarioAppId, vinculo);

        } else if (!usuarioAppService.tieneRol(usuarioAppId, "FAMILIAR")) {

            usuarioAppService.asignarRol(usuarioAppId, usuarioAppService.obtenerRolPorCodigo("FAMILIAR").getId());
        }
    }

    private static String textoParentesco(String parentesco) {

        return switch (parentesco) {
            case SolicitudFamilia.MADRE -> "Madre";
            case SolicitudFamilia.PADRE -> "Padre";
            case SolicitudFamilia.TUTOR -> "Tutor Legal";
            default -> "Otro";
        };
    }

    // ---- quién ve qué ------------------------------------------------------------------------------------------------

    /**
     * Los permisos de quien revisa: el panel web, coordinación y los administradores de la app lo ven todo; un
     * entrenador solo lo de los equipos que gestiona (los mismos que
     * {@code EquipoGestionDao.puedeGestionarEquipo}); cualquier otro, nada.
     */
    private final class Permisos {

        private final Revisor revisor;
        private final boolean todo;
        private final Map<Long, Boolean> equipos = new HashMap<>();

        Permisos(Revisor revisor) {
            this.revisor = revisor;
            this.todo = revisor.esPanel() || equipoGestionDao.esCoordinadorOAdmin(revisor.usuarioAppId());

            // Ni coordinación, ni administración, ni entrenador de ningún equipo: no revisa nada.
            if (!todo && !equipoGestionDao.gestionaAlgunEquipo(revisor.usuarioAppId())) {
                throw new SecurityException("No puedes revisar solicitudes de familia");
            }
        }

        boolean veTodo() {
            return todo;
        }

        boolean puedeVerEquipo(Long equipoId) {

            if (todo) {
                return true;
            }

            return equipoId != null && equipos.computeIfAbsent(equipoId,
                    id -> equipoGestionDao.puedeGestionarEquipo(revisor.usuarioAppId(), id));
        }

        boolean puedeVerJugador(JugadorCandidato jugador) {
            return todo || puedeVerEquipo(jugador.equipoId());
        }
    }

    /** Las coincidencias del hijo que puede ver quien revisa. */
    private List<CoincidenciasJugador.Coincidencia> visibles(SolicitudFamiliaJugador hijo,
            List<JugadorCandidato> activos, Permisos permisos) {

        List<CoincidenciasJugador.Coincidencia> lista = new ArrayList<>();

        for (CoincidenciasJugador.Coincidencia c : CoincidenciasJugador.buscar(hijo.nombre(), hijo.apellidos(),
                hijo.equipoTexto(), activos)) {

            if (permisos.puedeVerJugador(c.jugador())) {
                lista.add(c);
            }
        }

        return lista;
    }

    /** La solicitud como la ve quien revisa; null si no ve ningún hijo (un entrenador sin coincidencias en sus equipos). */
    private Revision aRevision(SolicitudFamilia s, List<JugadorCandidato> activos, Permisos permisos,
            boolean soloPendientes) {

        List<HijoRevision> hijos = new ArrayList<>();

        for (SolicitudFamiliaJugador h : dao.obtenerHijos(s.id())) {

            List<CoincidenciasJugador.Coincidencia> coincidencias = visibles(h, activos, permisos);

            if (soloPendientes && !SolicitudFamiliaJugador.PENDIENTE.equals(h.estado())) {
                continue;
            }

            if (!permisos.veTodo() && coincidencias.isEmpty()) {
                continue;
            }

            Coincidencia elegido = null;

            if (h.jugadorId() != null) {
                elegido = activos.stream().filter(j -> j.id().equals(h.jugadorId())).findFirst()
                        .map(j -> aCoincidencia(j, false)).orElse(null);
            }

            hijos.add(new HijoRevision(h.id(), h.nombre(), h.apellidos(), h.equipoTexto(), h.estado(),
                    elegido,
                    coincidencias.stream().map(c -> aCoincidencia(c.jugador(), c.coincideEquipo())).toList()));
        }

        if (hijos.isEmpty()) {
            return null;
        }

        return new Revision(s.id(), s.nombre(), s.apellidos(), s.email(), s.telefono(), s.parentesco(), s.estado(),
                s.motivoRechazo(), s.fechaSolicitud(), s.fechaRevision(), hijos);
    }

    private static Coincidencia aCoincidencia(JugadorCandidato j, boolean coincideEquipo) {
        return new Coincidencia(j.id(), (j.nombre() + " " + j.apellidos()).trim(), j.equipoId(), j.equipo(),
                coincideEquipo);
    }

    // ==================================================================
    // Avisos
    // ==================================================================

    /** Avisa a coordinación, administración y a los entrenadores de los equipos de las coincidencias. */
    private void avisarNuevaSolicitud(long solicitudId, Long solicitanteId) {

        try {
            List<JugadorCandidato> activos = dao.listarJugadoresActivos();
            Set<Long> equipos = new LinkedHashSet<>();

            for (SolicitudFamiliaJugador h : dao.obtenerHijos(solicitudId)) {
                for (CoincidenciasJugador.Coincidencia c : CoincidenciasJugador.buscar(h.nombre(), h.apellidos(),
                        h.equipoTexto(), activos)) {
                    if (c.jugador().equipoId() != null) {
                        equipos.add(c.jugador().equipoId());
                    }
                }
            }

            avisos.avisarNuevaSolicitud(avisos.destinatariosDeRevision(equipos, solicitanteId), solicitudId);

        } catch (RuntimeException e) {
            logger.error("No se ha podido avisar de la solicitud de familia {}: {}", solicitudId, e.getMessage(), e);
        }
    }

    // ==================================================================
    // Tareas diarias
    // ==================================================================

    // Sin @Transactional: cada solicitud se limpia por separado y una que falle no deshace las demás.
    @Override
    public List<Caducada> caducarVencidas() {

        LocalDateTime limite = LocalDateTime.now(clock).minusDays(DIAS_HASTA_CADUCAR);
        List<Caducada> caducadas = new ArrayList<>();

        for (SolicitudFamilia s : dao.listarCaducables(limite)) {

            try {
                // Antes de borrar: después ya no hay a quién avisar.
                String email = s.email();
                String nombre = s.nombre();
                String idioma = usuarioAppService.obtenerIdioma(s.usuarioAppId().intValue());

                dao.caducar(s.id());

                // Solo la cuenta que creó el propio registro y sin roles ni vínculos; las demás no se tocan nunca.
                if (sinRolesNiVinculos(s.usuarioAppId().intValue())) {
                    cuentaAppService.eliminarCuentaPorSistema(s.usuarioAppId().intValue());
                }

                if (email != null && !email.isBlank()) {
                    caducadas.add(new Caducada(email, nombre, idioma));
                }

                logger.info("Solicitud de familia caducada y borrada: solicitudId={}", s.id());

            } catch (RuntimeException e) {
                logger.error("No se ha podido caducar la solicitud de familia {}: {}", s.id(), e.getMessage(), e);
            }
        }

        return caducadas;
    }

    private boolean sinRolesNiVinculos(int usuarioAppId) {
        return usuarioAppService.obtenerRoles(usuarioAppId).isEmpty()
                && usuarioAppVinculoDao.obtenerVinculos(usuarioAppId).isEmpty();
    }

    @Override
    public int recordarPendientes() {

        LocalDateTime limite = LocalDateTime.now(clock).minusDays(DIAS_HASTA_RECORDATORIO);

        if (dao.listarPendientesAnterioresA(limite).isEmpty()) {
            return 0;
        }

        return avisos.recordarACoordinacion();
    }

    // ==================================================================
    // Validación
    // ==================================================================

    /** La petición ya comprobada y normalizada. */
    private record Datos(String nombre, String apellidos, String email, String telefono, String parentesco,
            List<SolicitudFamiliaRequest.Hijo> hijos, String idioma) {
    }

    private Datos validar(SolicitudFamiliaRequest r, boolean pideEmail) {

        if (r == null) {
            throw new IllegalArgumentException("La petición no puede ser nula");
        }

        String nombre = obligatorio(r.nombre(), "El nombre es obligatorio", MAX_NOMBRE, "El nombre es demasiado largo");
        String apellidos = obligatorio(r.apellidos(), "Los apellidos son obligatorios", MAX_APELLIDOS,
                "Los apellidos son demasiado largos");

        String email = normalizarEmail(r.email());

        if (email.isEmpty()) {
            throw new IllegalArgumentException(pideEmail ? "El email es obligatorio" : "La cuenta no tiene email");
        }

        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("El email no tiene un formato válido");
        }

        String telefono = null;

        if (hayTexto(r.telefono())) {
            telefono = r.telefono().trim();

            if (!TELEFONO.matcher(telefono).matches()) {
                throw new IllegalArgumentException("El teléfono no tiene un formato válido");
            }
        }

        String parentesco = r.parentesco() == null ? "" : r.parentesco().trim().toUpperCase(Locale.ROOT);

        if (!PARENTESCOS.contains(parentesco)) {
            throw new IllegalArgumentException("El parentesco tiene que ser MADRE, PADRE, TUTOR u OTRO");
        }

        if (r.hijos() == null || r.hijos().isEmpty()) {
            throw new IllegalArgumentException("Indica al menos un hijo o hija");
        }

        if (r.hijos().size() > MAX_HIJOS) {
            throw new IllegalArgumentException("Puedes indicar como máximo " + MAX_HIJOS + " hijos por solicitud");
        }

        List<SolicitudFamiliaRequest.Hijo> hijos = new ArrayList<>();

        for (SolicitudFamiliaRequest.Hijo h : r.hijos()) {

            if (h == null) {
                throw new IllegalArgumentException("Faltan los datos de un hijo");
            }

            hijos.add(new SolicitudFamiliaRequest.Hijo(
                    obligatorio(h.nombre(), "El nombre del jugador es obligatorio", MAX_NOMBRE,
                            "El nombre del jugador es demasiado largo"),
                    obligatorio(h.apellidos(), "Los apellidos del jugador son obligatorios", MAX_APELLIDOS,
                            "Los apellidos del jugador son demasiado largos"),
                    opcional(h.equipo(), MAX_EQUIPO, "El equipo o categoría es demasiado largo")));
        }

        if (!Boolean.TRUE.equals(r.aceptaPrivacidad())) {
            throw new IllegalArgumentException("Tienes que aceptar la política de privacidad");
        }

        if (!Boolean.TRUE.equals(r.declaraRepresentante())) {
            throw new IllegalArgumentException(
                    "Tienes que declarar que eres el representante legal del jugador o que estás autorizado por él");
        }

        return new Datos(nombre, apellidos, email, telefono, parentesco, hijos, idiomaNormalizado(r.idioma()));
    }

    private static String obligatorio(String valor, String mensajeVacio, int maximo, String mensajeLargo) {

        if (!hayTexto(valor)) {
            throw new IllegalArgumentException(mensajeVacio);
        }

        String limpio = valor.trim().replaceAll("\\s+", " ");

        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(mensajeLargo);
        }

        return limpio;
    }

    private static String opcional(String valor, int maximo, String mensajeLargo) {

        if (!hayTexto(valor)) {
            return null;
        }

        String limpio = valor.trim().replaceAll("\\s+", " ");

        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(mensajeLargo);
        }

        return limpio;
    }

    private static boolean hayTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String idiomaNormalizado(String idioma) {
        return hayTexto(idioma) ? com.mikedev.mutxamelcf.model.IdiomaApp.normalizar(idioma) : null;
    }

    private void guardarIdioma(int usuarioId, String idioma) {

        if (idioma != null) {
            usuarioAppService.actualizarIdioma(usuarioId, idioma);
        }
    }

    private SolicitudFamilia solicitudNueva(Datos d, long usuarioId, String estado) {

        return new SolicitudFamilia(null, usuarioId, d.nombre, d.apellidos, d.email, d.telefono,
                d.parentesco, estado, null, LocalDateTime.now(clock), null, null, null);
    }

    private static List<SolicitudFamiliaJugador> hijos(Datos d) {

        List<SolicitudFamiliaJugador> hijos = new ArrayList<>();

        for (SolicitudFamiliaRequest.Hijo h : d.hijos) {
            hijos.add(new SolicitudFamiliaJugador(null, null, h.nombre(), h.apellidos(), h.equipo(), null,
                    SolicitudFamiliaJugador.PENDIENTE));
        }

        return hijos;
    }

    /** Tras el commit si hay transacción; si no, al momento (el aviso no debe salir si la operación se deshace). */
    private static void despuesDelCommit(Runnable tarea) {

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarea.run();
                }
            });
            return;
        }

        tarea.run();
    }
}
