package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.FamiliarDao;
import com.mikedev.mutxamelcf.dao.FamiliarJugadorDao;
import com.mikedev.mutxamelcf.dao.SolicitudFamiliaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.FamiliarJugador;
import com.mikedev.mutxamelcf.model.JugadorCandidato;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.SolicitudFamilia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaJugador;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaRequest;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Caducada;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Mia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Revision;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;
import com.mikedev.mutxamelcf.service.CuentaAppService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Registro;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Resultado;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Revisor;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/** Las reglas de las solicitudes de familia, con un DAO en memoria y el resto de colaboradores simulados. */
class SolicitudFamiliaServiceImplTest {

    private static final Instant INSTANTE = Instant.parse("2026-11-18T10:00:00Z");
    private static final LocalDateTime AHORA = LocalDateTime.ofInstant(INSTANTE, ZoneOffset.UTC);

    // Revisores: 1 coordinación, 2 administrador, 3 entrenador de Alevín B (equipo 8), 4 entrenador de otro equipo,
    // 5 sin ningún permiso.
    private static final long COORDINADOR = 1;
    private static final long ADMIN = 2;
    private static final long ENTRENADOR_ALEVIN = 3;
    private static final long ENTRENADOR_OTRO = 4;
    private static final long NADIE = 5;

    private MemoriaDao dao;
    private UsuarioAppService usuarioAppService;
    private UsuarioAppVinculoDao vinculoDao;
    private FamiliarDao familiarDao;
    private FamiliarJugadorDao familiarJugadorDao;
    private EquipoGestionDao equipoGestionDao;
    private CuentaAppService cuentaAppService;
    private com.mikedev.mutxamelcf.dao.PerfilAppDao perfilAppDao;
    private AvisosSolicitudFamilia avisos;
    private SolicitudFamiliaServiceImpl service;

    private final Map<String, UsuarioApp> cuentas = new HashMap<>();
    private int siguienteCuenta = 100;

    // ---- el DAO en memoria ---------------------------------------------------------------------------------------------

    private static final class MemoriaDao implements SolicitudFamiliaDao {

        final Map<Long, SolicitudFamilia> solicitudes = new HashMap<>();
        final Map<Long, List<SolicitudFamiliaJugador>> hijos = new HashMap<>();
        List<JugadorCandidato> activos = new ArrayList<>();
        long siguiente = 1;
        long siguienteHijo = 1;

        @Override
        public long insertar(SolicitudFamilia s, List<SolicitudFamiliaJugador> lista) {
            long id = siguiente++;
            solicitudes.put(id, conId(s, id));
            anadirHijos(id, lista);
            return id;
        }

        private static SolicitudFamilia conId(SolicitudFamilia s, long id) {
            return new SolicitudFamilia(id, s.usuarioAppId(), s.nombre(), s.apellidos(), s.email(), s.telefono(),
                    s.parentesco(), s.estado(), s.motivoRechazo(), s.fechaSolicitud(), s.fechaRevision(),
                    s.revisadaPorId(), s.revisadaPorPanel());
        }

        private void anadirHijos(long solicitudId, List<SolicitudFamiliaJugador> lista) {
            List<SolicitudFamiliaJugador> nuevos = new ArrayList<>();
            for (SolicitudFamiliaJugador h : lista) {
                nuevos.add(new SolicitudFamiliaJugador(siguienteHijo++, solicitudId, h.nombre(), h.apellidos(),
                        h.equipoTexto(), null, "PENDIENTE"));
            }
            hijos.put(solicitudId, nuevos);
        }

        @Override
        public void reemplazarDatos(long id, SolicitudFamilia d, List<SolicitudFamiliaJugador> lista) {
            SolicitudFamilia a = solicitudes.get(id);
            solicitudes.put(id, new SolicitudFamilia(id, a.usuarioAppId(), d.nombre(), d.apellidos(), a.email(),
                    d.telefono(), d.parentesco(), a.estado(), null, d.fechaSolicitud(), null, null, null));
            anadirHijos(id, lista);
        }

        private void actualizar(long id, java.util.function.UnaryOperator<SolicitudFamilia> cambio) {
            solicitudes.put(id, cambio.apply(solicitudes.get(id)));
        }

        private static SolicitudFamilia conEstado(SolicitudFamilia a, String estado) {
            return new SolicitudFamilia(a.id(), a.usuarioAppId(), a.nombre(), a.apellidos(), a.email(), a.telefono(),
                    a.parentesco(), estado, a.motivoRechazo(), a.fechaSolicitud(), a.fechaRevision(),
                    a.revisadaPorId(), a.revisadaPorPanel());
        }

        @Override
        public SolicitudFamilia obtenerPorId(long id) {
            return solicitudes.get(id);
        }

        @Override
        public List<SolicitudFamilia> obtenerPorUsuario(long usuarioAppId) {
            return solicitudes.values().stream().filter(s -> s.usuarioAppId() == usuarioAppId)
                    .sorted((a, b) -> Long.compare(b.id(), a.id())).toList();
        }

        @Override
        public SolicitudFamilia obtenerAbiertaDeUsuario(long usuarioAppId) {
            return obtenerPorUsuario(usuarioAppId).stream()
                    .filter(s -> Set.of("PENDIENTE_EMAIL", "PENDIENTE").contains(s.estado())).findFirst()
                    .orElse(null);
        }

        @Override
        public List<SolicitudFamiliaJugador> obtenerHijos(long solicitudId) {
            return hijos.getOrDefault(solicitudId, List.of());
        }

        @Override
        public SolicitudFamiliaJugador obtenerHijo(long hijoId) {
            return hijos.values().stream().flatMap(List::stream).filter(h -> h.id() == hijoId).findFirst()
                    .orElse(null);
        }

        @Override
        public int confirmarEmail(long id) {
            if (!"PENDIENTE_EMAIL".equals(solicitudes.get(id).estado())) {
                return 0;
            }
            actualizar(id, a -> conEstado(a, "PENDIENTE"));
            return 1;
        }

        @Override
        public List<SolicitudFamilia> listarPorEstado(String estado) {
            return solicitudes.values().stream().filter(s -> s.estado().equals(estado))
                    .sorted((a, b) -> Long.compare(a.id(), b.id())).toList();
        }

        @Override
        public int contarPorEstado(String estado) {
            return listarPorEstado(estado).size();
        }

        @Override
        public List<SolicitudFamilia> listarRevisadasDesde(LocalDateTime desde) {
            return solicitudes.values().stream().filter(s -> Set.of("APROBADA", "RECHAZADA").contains(s.estado()))
                    .filter(s -> !s.fechaRevision().isBefore(desde)).toList();
        }

        private void cambiarHijo(long hijoId, String estado, Long jugadorId) {
            hijos.replaceAll((sol, lista) -> lista.stream().map(h -> h.id() == hijoId
                    ? new SolicitudFamiliaJugador(h.id(), h.solicitudId(), h.nombre(), h.apellidos(), h.equipoTexto(),
                            jugadorId, estado)
                    : h).toList());
        }

        @Override
        public void aprobarHijo(long hijoId, long jugadorId) {
            cambiarHijo(hijoId, "APROBADO", jugadorId);
        }

        @Override
        public void rechazarHijo(long hijoId) {
            cambiarHijo(hijoId, "RECHAZADO", null);
        }

        @Override
        public void actualizarMotivo(long id, String motivo) {
            actualizar(id, a -> new SolicitudFamilia(a.id(), a.usuarioAppId(), a.nombre(), a.apellidos(), a.email(),
                    a.telefono(), a.parentesco(), a.estado(), motivo, a.fechaSolicitud(), a.fechaRevision(),
                    a.revisadaPorId(), a.revisadaPorPanel()));
        }

        @Override
        public void cerrar(long id, String estado, String motivo, LocalDateTime fecha, Long porId, String panel) {
            actualizar(id, a -> new SolicitudFamilia(a.id(), a.usuarioAppId(), a.nombre(), a.apellidos(), a.email(),
                    a.telefono(), a.parentesco(), estado, motivo, a.fechaSolicitud(), fecha, porId, panel));
        }

        @Override
        public List<SolicitudFamilia> listarCaducables(LocalDateTime limite) {
            return solicitudes.values().stream()
                    .filter(s -> (Set.of("PENDIENTE_EMAIL", "PENDIENTE").contains(s.estado())
                            && s.fechaSolicitud().isBefore(limite))
                            || ("RECHAZADA".equals(s.estado()) && s.fechaRevision().isBefore(limite)))
                    .sorted((a, b) -> Long.compare(a.id(), b.id())).toList();
        }

        @Override
        public List<SolicitudFamilia> listarPendientesAnterioresA(LocalDateTime limite) {
            return listarPorEstado("PENDIENTE").stream().filter(s -> s.fechaSolicitud().isBefore(limite)).toList();
        }

        @Override
        public List<JugadorCandidato> listarJugadoresActivos() {
            return activos;
        }

        @Override
        public void caducar(long id) {
            solicitudes.remove(id);
            hijos.remove(id);
        }
    }

    // ---- preparación ---------------------------------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        dao = new MemoriaDao();
        usuarioAppService = mock(UsuarioAppService.class);
        vinculoDao = mock(UsuarioAppVinculoDao.class);
        familiarDao = mock(FamiliarDao.class);
        familiarJugadorDao = mock(FamiliarJugadorDao.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        cuentaAppService = mock(CuentaAppService.class);
        avisos = mock(AvisosSolicitudFamilia.class);
        perfilAppDao = mock(com.mikedev.mutxamelcf.dao.PerfilAppDao.class);

        service = new SolicitudFamiliaServiceImpl(dao, usuarioAppService, vinculoDao, familiarDao,
                familiarJugadorDao, equipoGestionDao, cuentaAppService, avisos, Clock.fixed(INSTANTE, ZoneOffset.UTC),
                new PerfilAppServiceImpl(perfilAppDao, usuarioAppService));

        // Cuentas: crear devuelve un id nuevo y deja la cuenta consultable por email.
        when(usuarioAppService.obtenerPorEmail(any())).thenAnswer(i -> cuentas.get(i.getArgument(0)));
        when(usuarioAppService.crearUsuario(any())).thenAnswer(i -> {
            UsuarioApp u = i.getArgument(0);
            u.setId(siguienteCuenta++);
            u.setActivo(false);
            cuentas.put(u.getEmail(), u);
            return u.getId();
        });
        when(usuarioAppService.obtenerPorId(anyInt()))
                .thenAnswer(i -> cuentas.values().stream().filter(u -> u.getId() == (int) i.getArgument(0))
                        .findFirst().orElse(null));
        when(usuarioAppService.generarTokenActivacion(anyInt())).thenReturn("123456");
        when(usuarioAppService.obtenerIdioma(anyInt())).thenReturn("es");
        when(usuarioAppService.obtenerRoles(anyInt())).thenReturn(List.of());
        when(vinculoDao.obtenerVinculos(anyInt())).thenReturn(List.of());

        // Revisores.
        when(equipoGestionDao.esCoordinadorOAdmin(COORDINADOR)).thenReturn(true);
        when(equipoGestionDao.esCoordinadorOAdmin(ADMIN)).thenReturn(true);
        when(equipoGestionDao.gestionaAlgunEquipo(ENTRENADOR_ALEVIN)).thenReturn(true);
        when(equipoGestionDao.gestionaAlgunEquipo(ENTRENADOR_OTRO)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(ENTRENADOR_ALEVIN, 8L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(ENTRENADOR_OTRO, 9L)).thenReturn(true);

        // Jugadores: Luis Pérez Gómez (Alevín B, equipo 8), Luis Pérez Ruiz (Benjamín A, equipo 9), Eva García (equipo 8).
        dao.activos = List.of(
                new JugadorCandidato(55L, "Luis", "Pérez Gómez", 8L, "Alevín B", "Alevín"),
                new JugadorCandidato(56L, "Luis", "Pérez Ruiz", 9L, "Benjamín A", "Benjamín"),
                new JugadorCandidato(57L, "Eva", "García", 8L, "Alevín B", "Alevín"));

        // El rol FAMILIAR existe.
        RolApp familiar = new RolApp();
        familiar.setId(4);
        familiar.setCodigo("FAMILIAR");
        when(usuarioAppService.obtenerRolPorCodigo("FAMILIAR")).thenReturn(familiar);
    }

    private static SolicitudFamiliaRequest.Hijo hijo(String nombre, String apellidos, String equipo) {
        return new SolicitudFamiliaRequest.Hijo(nombre, apellidos, equipo);
    }

    private static SolicitudFamiliaRequest peticion(String email, SolicitudFamiliaRequest.Hijo... hijos) {
        return new SolicitudFamiliaRequest("Ana", "Pérez Gil", email, "623 17 68 18", "madre", List.of(hijos), true,
                true, "ca");
    }

    private static SolicitudFamiliaRequest conCasillas(SolicitudFamiliaRequest r, Boolean privacidad,
            Boolean representante) {
        return new SolicitudFamiliaRequest(r.nombre(), r.apellidos(), r.email(), r.telefono(), r.parentesco(),
                r.hijos(), privacidad, representante, r.idioma());
    }

    private UsuarioApp cuentaActiva(int id, String email) {
        UsuarioApp u = new UsuarioApp();
        u.setId(id);
        u.setEmail(email);
        u.setActivo(true);
        u.setNombre("Marta");
        u.setApellidos("Soler");
        cuentas.put(email, u);
        return u;
    }

    /** Registra una solicitud nueva y la deja PENDIENTE (email confirmado), como la dejaría la verificación. */
    private long solicitudPendiente(String email, SolicitudFamiliaRequest.Hijo... hijos) {
        service.registrarPublica(peticion(email, hijos));
        SolicitudFamilia s = dao.solicitudes.values().stream().filter(x -> x.email().equals(email)).findFirst()
                .orElseThrow();
        dao.confirmarEmail(s.id());
        return s.id();
    }

    private long hijoId(long solicitudId, int indice) {
        return dao.obtenerHijos(solicitudId).get(indice).id();
    }

    // ==================================================================================================================
    // Registro sin sesión
    // ==================================================================================================================

    @Test
    void unEmailNuevoCreaLaCuentaSinRolesYLaSolicitudPendienteDeEmail() {

        Registro r = service.registrarPublica(
                peticion("  Ana@Example.COM ", hijo(" Luis ", "Pérez", "Alevín B"), hijo("Eva", "Pérez", null)));

        assertThat(r.resultado()).isEqualTo(Resultado.CODIGO_ENVIADO);
        assertThat(r.email()).isEqualTo("ana@example.com");
        assertThat(r.codigo()).isEqualTo("123456");
        assertThat(r.idioma()).isEqualTo("ca");

        // La cuenta: nombre y apellidos, inactiva, idioma, y NINGÚN rol ni vínculo.
        UsuarioApp cuenta = cuentas.get("ana@example.com");
        assertThat(cuenta.getNombre()).isEqualTo("Ana");
        assertThat(cuenta.getApellidos()).isEqualTo("Pérez Gil");
        verify(usuarioAppService).actualizarIdioma(cuenta.getId(), "ca");
        verify(usuarioAppService, never()).asignarRol(anyInt(), anyInt());
        verify(usuarioAppService, never()).agregarVinculosAUsuarioExistente(anyInt(), any());

        SolicitudFamilia s = dao.obtenerAbiertaDeUsuario(cuenta.getId());
        assertThat(s.estado()).isEqualTo("PENDIENTE_EMAIL");
        assertThat(s.parentesco()).isEqualTo("MADRE");
        assertThat(s.fechaSolicitud()).isEqualTo(AHORA);
        assertThat(dao.obtenerHijos(s.id())).extracting(SolicitudFamiliaJugador::nombre).containsExactly("Luis", "Eva");

        // Todavía no se avisa a nadie: falta confirmar el email.
        verify(avisos, never()).avisarNuevaSolicitud(any(), any());
    }

    @Test
    void unEmailConCuentaActivaNoCreaOtraNiRevelaNadaMas() {

        cuentaActiva(7, "ana@example.com");

        Registro r = service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));

        assertThat(r.resultado()).isEqualTo(Resultado.CUENTA_EXISTENTE);
        assertThat(r.email()).isNull();
        assertThat(r.codigo()).isNull();
        assertThat(r.nombre()).isNull();
        verify(usuarioAppService, never()).crearUsuario(any());
        verify(usuarioAppService, never()).generarTokenActivacion(anyInt());
        assertThat(dao.solicitudes).isEmpty();
    }

    @Test
    void unEmailConUnaInvitacionDelClubPendienteDiceQueUseActivarCuenta() {

        UsuarioApp invitada = new UsuarioApp();
        invitada.setId(9);
        invitada.setEmail("ana@example.com");
        invitada.setActivo(false);
        cuentas.put("ana@example.com", invitada);

        Registro r = service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));

        assertThat(r.resultado()).isEqualTo(Resultado.INVITACION_PENDIENTE);
        assertThat(r.codigo()).isNull();
        verify(usuarioAppService, never()).crearUsuario(any());
        verify(usuarioAppService, never()).generarTokenActivacion(anyInt());
        assertThat(dao.solicitudes).isEmpty();
    }

    @Test
    void unaCuentaQueElClubDesactivoSeTrataComoCuentaExistente() {

        UsuarioApp desactivada = new UsuarioApp();
        desactivada.setId(9);
        desactivada.setEmail("ana@example.com");
        desactivada.setActivo(false);
        desactivada.setFechaActivacion(Timestamp.from(INSTANTE.minusSeconds(86400L * 30)));
        cuentas.put("ana@example.com", desactivada);

        assertThat(service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null))).resultado())
                .isEqualTo(Resultado.CUENTA_EXISTENTE);
    }

    @Test
    void repetirLaSolicitudAntesDeConfirmarSustituyeLosDatosYMandaUnCodigoNuevo() {

        service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));

        SolicitudFamiliaRequest otra = new SolicitudFamiliaRequest("Anita", "Gil", "ana@example.com", null, "tutor",
                List.of(hijo("Eva", "Gil", "Cadete")), true, true, "es");

        Registro r = service.registrarPublica(otra);

        assertThat(r.resultado()).isEqualTo(Resultado.CODIGO_ENVIADO);
        assertThat(r.codigo()).isEqualTo("123456");
        assertThat(dao.solicitudes).hasSize(1);
        verify(usuarioAppService, times(1)).crearUsuario(any());
        verify(usuarioAppService, times(2)).generarTokenActivacion(anyInt());

        SolicitudFamilia s = dao.solicitudes.values().iterator().next();
        assertThat(s.nombre()).isEqualTo("Anita");
        assertThat(s.parentesco()).isEqualTo("TUTOR");
        assertThat(dao.obtenerHijos(s.id())).extracting(SolicitudFamiliaJugador::nombre).containsExactly("Eva");
    }

    @Test
    void validaLaPeticionConMensajesClaros() {

        SolicitudFamiliaRequest base = peticion("ana@example.com", hijo("Luis", "Pérez", null));

        assertThatThrownBy(() -> service.registrarPublica(null)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.registrarPublica(new SolicitudFamiliaRequest(" ", "Gil", "a@b.es", null,
                "MADRE", base.hijos(), true, true, null))).hasMessage("El nombre es obligatorio");

        assertThatThrownBy(() -> service.registrarPublica(new SolicitudFamiliaRequest("Ana", "Gil", "no-es-email",
                null, "MADRE", base.hijos(), true, true, null))).hasMessage("El email no tiene un formato válido");

        assertThatThrownBy(() -> service.registrarPublica(new SolicitudFamiliaRequest("Ana", "Gil", "a@b.es", null,
                "ABUELA", base.hijos(), true, true, null)))
                .hasMessage("El parentesco tiene que ser MADRE, PADRE, TUTOR u OTRO");

        assertThatThrownBy(() -> service.registrarPublica(new SolicitudFamiliaRequest("Ana", "Gil", "a@b.es", "abc",
                "MADRE", base.hijos(), true, true, null))).hasMessage("El teléfono no tiene un formato válido");

        assertThatThrownBy(() -> service.registrarPublica(new SolicitudFamiliaRequest("Ana", "Gil", "a@b.es",
                "6".repeat(21), "MADRE", base.hijos(), true, true, null)))
                .hasMessage("El teléfono no tiene un formato válido");

        assertThatThrownBy(() -> service.registrarPublica(conCasillas(base, false, true)))
                .hasMessage("Tienes que aceptar la política de privacidad");
        assertThatThrownBy(() -> service.registrarPublica(conCasillas(base, null, true)))
                .hasMessage("Tienes que aceptar la política de privacidad");
        assertThatThrownBy(() -> service.registrarPublica(conCasillas(base, true, false)))
                .hasMessageContaining("representante legal");

        verify(usuarioAppService, never()).crearUsuario(any());
    }

    @Test
    void hacenFaltaDeUnoACincoHijosConNombreYApellidos() {

        assertThatThrownBy(() -> service.registrarPublica(peticion("a@b.es")))
                .hasMessage("Indica al menos un hijo o hija");

        SolicitudFamiliaRequest.Hijo h = hijo("Luis", "Pérez", null);

        assertThatThrownBy(() -> service.registrarPublica(peticion("a@b.es", h, h, h, h, h, h)))
                .hasMessage("Puedes indicar como máximo 5 hijos por solicitud");

        assertThatThrownBy(() -> service.registrarPublica(peticion("a@b.es", hijo("", "Pérez", null))))
                .hasMessage("El nombre del jugador es obligatorio");
        assertThatThrownBy(() -> service.registrarPublica(peticion("a@b.es", hijo("Luis", null, null))))
                .hasMessage("Los apellidos del jugador son obligatorios");

        // Cinco sí.
        assertThat(service.registrarPublica(peticion("cinco@b.es", h, h, h, h, h)).resultado())
                .isEqualTo(Resultado.CODIGO_ENVIADO);
    }

    @Test
    void laRespuestaNuncaDevuelveDatosDeJugadores() {

        // Aunque el hijo coincida con un jugador real, el registro público no lo comprueba ni lo cuenta.
        Registro r = service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez Gómez", "Alevín B")));

        assertThat(r.toString()).doesNotContain("55").doesNotContain("Alevín").doesNotContain("Gómez");
        verify(familiarDao, never()).obtenerPorEmailOTelefono(any(), any());
    }

    // ==================================================================================================================
    // Verificar el email
    // ==================================================================================================================

    @Test
    void verificarElEmailPasaLaSolicitudAPendienteYAvisaAQuienRevisa() {

        service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez Gómez", "Alevín B")));
        UsuarioApp cuenta = cuentas.get("ana@example.com");
        when(usuarioAppService.activarCuenta("ana@example.com", "123456", "Secreta123")).thenReturn(cuenta);
        when(avisos.destinatariosDeRevision(any(), any())).thenReturn(Set.of(COORDINADOR));

        UsuarioApp activada = service.verificarEmail(" ANA@example.com ", "123456", "Secreta123");

        assertThat(activada).isSameAs(cuenta);
        assertThat(dao.obtenerAbiertaDeUsuario(cuenta.getId()).estado()).isEqualTo("PENDIENTE");

        // Los equipos de las coincidencias (Alevín B = 8) llegan al aviso, que se manda sin el solicitante.
        verify(avisos).destinatariosDeRevision(Set.of(8L), (long) cuenta.getId());
        verify(avisos).avisarNuevaSolicitud(eq(Set.of(COORDINADOR)), any());
        verify(usuarioAppService, never()).asignarRol(anyInt(), anyInt());
    }

    @Test
    void conUnCodigoMaloLaSolicitudSigueSinConfirmarYNoSeAvisa() {

        service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));
        when(usuarioAppService.activarCuenta(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("El código no es válido o ha caducado."));

        assertThatThrownBy(() -> service.verificarEmail("ana@example.com", "000000", "Secreta123"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El código no es válido o ha caducado.");

        assertThat(dao.solicitudes.values().iterator().next().estado()).isEqualTo("PENDIENTE_EMAIL");
        verify(avisos, never()).avisarNuevaSolicitud(any(), any());
    }

    @Test
    void verificarNoSirveParaActivarUnaInvitacionDelClubNiUnEmailSinSolicitud() {

        UsuarioApp invitada = new UsuarioApp();
        invitada.setId(9);
        invitada.setEmail("invitada@example.com");
        invitada.setActivo(false);
        cuentas.put("invitada@example.com", invitada);

        assertThatThrownBy(() -> service.verificarEmail("invitada@example.com", "123456", "Secreta123"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El código no es válido o ha caducado.");
        assertThatThrownBy(() -> service.verificarEmail("nadie@example.com", "123456", "Secreta123"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El código no es válido o ha caducado.");
        assertThatThrownBy(() -> service.verificarEmail(null, "123456", "Secreta123"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioAppService, never()).activarCuenta(any(), any(), any());
    }

    @Test
    void reenviarElCodigoSoloSirveParaUnaSolicitudSinConfirmar() {

        service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));

        Registro r = service.reenviarCodigo("Ana@Example.com");

        assertThat(r.codigo()).isEqualTo("123456");
        assertThat(r.email()).isEqualTo("ana@example.com");
        assertThat(r.idioma()).isEqualTo("es");

        // Sin cuenta, con cuenta activa, o con una invitación del club: nada (y sin decir cuál de los casos).
        assertThat(service.reenviarCodigo("nadie@example.com")).isNull();
        assertThat(service.reenviarCodigo(null)).isNull();
        cuentaActiva(7, "activa@example.com");
        assertThat(service.reenviarCodigo("activa@example.com")).isNull();

        UsuarioApp invitada = new UsuarioApp();
        invitada.setId(9);
        invitada.setEmail("invitada@example.com");
        cuentas.put("invitada@example.com", invitada);
        assertThat(service.reenviarCodigo("invitada@example.com")).isNull();
    }

    @Test
    void unaCuentaActivadaPorActivarCuentaConfirmaLaSolicitudAlConsultarlaYAvisaUnaVez() {

        service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));
        UsuarioApp cuenta = cuentas.get("ana@example.com");
        cuenta.setActivo(true);

        assertThat(service.misSolicitudes(cuenta.getId())).extracting(Mia::estado).containsExactly("PENDIENTE");
        service.misSolicitudes(cuenta.getId());

        verify(avisos, times(1)).avisarNuevaSolicitud(any(), any());
    }

    // ==================================================================================================================
    // Con sesión
    // ==================================================================================================================

    /** Una cuenta invitada por el club: sin nombre propio (el nombre sale de su ficha). */
    private UsuarioApp cuentaSinNombrePropio(int id, String email, String... roles) {
        UsuarioApp u = cuentaActiva(id, email);
        u.setNombre(null);
        u.setApellidos(null);
        when(usuarioAppService.obtenerRoles(id)).thenReturn(java.util.Arrays.stream(roles).map(c -> {
            RolApp r = new RolApp();
            r.setCodigo(c);
            return r;
        }).toList());
        return u;
    }

    private static SolicitudFamiliaRequest peticionSinNombre() {
        return new SolicitudFamiliaRequest(null, null, null, null, "MADRE",
                List.of(hijo("Luis", "Pérez", "Alevín B")), true, true, null);
    }

    @Test
    void conSesionUnaCuentaSinNombrePropioUsaElNombreDeSuFichaDeFamiliar() {

        cuentaSinNombrePropio(7, "marta@example.com", "FAMILIAR");
        Familiar ficha = new Familiar();
        ficha.setNombre("Marta");
        ficha.setApellidos("Soler Gil");
        when(perfilAppDao.obtenerFamiliarPorUsuario(7)).thenReturn(ficha);

        service.solicitarConSesion(7, peticionSinNombre());

        SolicitudFamilia s = dao.solicitudes.values().iterator().next();
        assertThat(s.nombre()).isEqualTo("Marta");
        assertThat(s.apellidos()).isEqualTo("Soler Gil");
    }

    @Test
    void conSesionUnaCuentaSinNombrePropioUsaElNombreDeSuFichaDeCuerpoTecnico() {

        cuentaSinNombrePropio(7, "luis@example.com", "ENTRENADOR");
        com.mikedev.mutxamelcf.model.CuerpoTecnico ficha = new com.mikedev.mutxamelcf.model.CuerpoTecnico();
        ficha.setNombre("Luis");
        ficha.setApellidos("Mas Ruiz");
        when(perfilAppDao.obtenerCuerpoTecnicoPorUsuario(7)).thenReturn(List.of(ficha));

        service.solicitarConSesion(7, peticionSinNombre());

        SolicitudFamilia s = dao.solicitudes.values().iterator().next();
        assertThat(s.nombre()).isEqualTo("Luis");
        assertThat(s.apellidos()).isEqualTo("Mas Ruiz");
    }

    @Test
    void conSesionUnaCuentaSinFichaNiNombreUsaElEmailYSinApellidosNoFalla() {

        cuentaSinNombrePropio(7, "sinficha@example.com");

        service.solicitarConSesion(7, peticionSinNombre());

        SolicitudFamilia s = dao.solicitudes.values().iterator().next();
        assertThat(s.nombre()).isEqualTo("sinficha@example.com");
        // APELLIDOS es NOT NULL (y en Oracle la cadena vacia es NULL): sin apellidos se guarda un espacio.
        assertThat(s.apellidos().trim()).isEmpty();
    }

    @Test
    void conSesionUnaCuentaConNombrePropioLoUsa() {

        cuentaActiva(7, "marta@example.com");

        service.solicitarConSesion(7, peticionSinNombre());

        SolicitudFamilia s = dao.solicitudes.values().iterator().next();
        assertThat(s.nombre()).isEqualTo("Marta");
        assertThat(s.apellidos()).isEqualTo("Soler");
        verify(perfilAppDao, never()).obtenerFamiliarPorUsuario(anyInt());
    }

    @Test
    void elRegistroPublicoSinNombreOSinApellidosSigueDandoError() {

        SolicitudFamiliaRequest sinNombre = new SolicitudFamiliaRequest(null, null, "ana@example.com", null, "MADRE",
                List.of(hijo("Luis", "Pérez", null)), true, true, null);
        assertThatThrownBy(() -> service.registrarPublica(sinNombre)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre es obligatorio");

        SolicitudFamiliaRequest sinApellidos = new SolicitudFamiliaRequest("Ana", " ", "ana@example.com", null, "MADRE",
                List.of(hijo("Luis", "Pérez", null)), true, true, null);
        assertThatThrownBy(() -> service.registrarPublica(sinApellidos)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los apellidos son obligatorios");
    }

    @Test
    void conSesionLaSolicitudNaceYaPendienteSinCrearCuentaNiVerificarEmail() {

        UsuarioApp cuenta = cuentaActiva(7, "marta@example.com");
        SolicitudFamiliaRequest peticion = new SolicitudFamiliaRequest(null, null, "otro@example.com", null, "PADRE",
                List.of(hijo("Luis", "Pérez", "Alevín B")), true, true, null);

        Mia mia = service.solicitarConSesion(7, peticion);

        assertThat(mia.estado()).isEqualTo("PENDIENTE");
        assertThat(mia.hijos()).hasSize(1);
        verify(usuarioAppService, never()).crearUsuario(any());
        verify(usuarioAppService, never()).generarTokenActivacion(anyInt());

        // El email y el nombre son los de la cuenta, no los de la petición.
        SolicitudFamilia s = dao.solicitudes.values().iterator().next();
        assertThat(s.email()).isEqualTo("marta@example.com");
        assertThat(s.nombre()).isEqualTo("Marta");
        assertThat(s.apellidos()).isEqualTo("Soler");
        assertThat(s.usuarioAppId()).isEqualTo(cuenta.getId());

        verify(avisos).avisarNuevaSolicitud(any(), any());
    }

    @Test
    void conSesionNoSePuedeTenerOtraSolicitudAbierta() {

        cuentaActiva(7, "marta@example.com");
        SolicitudFamiliaRequest peticion = peticion("marta@example.com", hijo("Luis", "Pérez", null));

        service.solicitarConSesion(7, peticion);

        assertThatThrownBy(() -> service.solicitarConSesion(7, peticion)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Ya tienes una solicitud pendiente de revisar");
    }

    @Test
    void conSesionTambienExigeLasCasillasYLosHijos() {

        cuentaActiva(7, "marta@example.com");

        assertThatThrownBy(() -> service.solicitarConSesion(7,
                conCasillas(peticion("m@x.es", hijo("Luis", "Pérez", null)), true, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.solicitarConSesion(7, peticion("m@x.es")))
                .hasMessage("Indica al menos un hijo o hija");
        assertThatThrownBy(() -> service.solicitarConSesion(99, peticion("m@x.es", hijo("L", "P", null))))
                .hasMessage("El usuario no existe");
    }

    @Test
    void misSolicitudesNuncaLlevanJugadoresNiCoincidencias() {

        cuentaActiva(7, "marta@example.com");
        service.solicitarConSesion(7, peticion("marta@example.com", hijo("Luis", "Pérez Gómez", "Alevín B")));

        List<Mia> mias = service.misSolicitudes(7);

        // La vista del propio familiar no tiene ni campo para jugadores: solo lo que él escribió y el estado.
        assertThat(mias.get(0).hijos().get(0).nombre()).isEqualTo("Luis");
        assertThat(mias.get(0).toString()).doesNotContain("55").doesNotContain("Gómez Alev");
        assertThat(java.util.Arrays.stream(Mia.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("coincidencias", "jugador", "jugadorId");
        assertThat(java.util.Arrays
                .stream(com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.HijoMio.class.getRecordComponents())
                .map(c -> c.getName())).doesNotContain("coincidencias", "jugador", "jugadorId");
    }

    // ==================================================================================================================
    // Coincidencias y permisos de revisión
    // ==================================================================================================================

    @Test
    void coordinacionYAdministracionVenTodasLasSolicitudesConSusCoincidencias() {

        long id = solicitudPendiente("ana@example.com", hijo("LUIS", "pérez", "benjamin a"),
                hijo("Desconocido", "Nadie", null));

        for (long revisor : new long[] { COORDINADOR, ADMIN }) {

            List<Revision> lista = service.pendientes(Revisor.app(revisor));

            assertThat(lista).hasSize(1);

            Revision r = lista.get(0);

            assertThat(r.id()).isEqualTo(id);
            assertThat(r.hijos()).hasSize(2);
            // Coinciden las dos "Luis Pérez" (segundo apellido ausente) y la del equipo escrito va primero.
            assertThat(r.hijos().get(0).coincidencias()).extracting(c -> c.jugadorId()).containsExactly(56L, 55L);
            assertThat(r.hijos().get(0).coincidencias().get(0).coincideEquipo()).isTrue();
            assertThat(r.hijos().get(1).coincidencias()).isEmpty();
            assertThat(service.contarPendientes(Revisor.app(revisor))).isEqualTo(1);
        }
    }

    @Test
    void unEntrenadorSoloVeLosHijosConCoincidenciasEnSusEquiposYSoloEsasCoincidencias() {

        solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null), hijo("Eva", "García", null),
                hijo("Desconocido", "Nadie", null));

        // El de Alevín B (equipo 8): Luis (55) y Eva (57) están en su equipo; Luis Pérez Ruiz (56) no se le enseña.
        List<Revision> deAlevin = service.pendientes(Revisor.app(ENTRENADOR_ALEVIN));

        assertThat(deAlevin).hasSize(1);
        assertThat(deAlevin.get(0).hijos()).extracting(h -> h.nombre()).containsExactly("Luis", "Eva");
        assertThat(deAlevin.get(0).hijos().get(0).coincidencias()).extracting(c -> c.jugadorId()).containsExactly(55L);

        // El de Benjamín A (equipo 9) solo ve a Luis, con su Luis Pérez Ruiz (56).
        List<Revision> deBenjamin = service.pendientes(Revisor.app(ENTRENADOR_OTRO));

        assertThat(deBenjamin.get(0).hijos()).extracting(h -> h.nombre()).containsExactly("Luis");
        assertThat(deBenjamin.get(0).hijos().get(0).coincidencias()).extracting(c -> c.jugadorId())
                .containsExactly(56L);
    }

    @Test
    void unEntrenadorSinCoincidenciasEnSusEquiposNoVeLaSolicitud() {

        solicitudPendiente("ana@example.com", hijo("Eva", "García", null));

        assertThat(service.pendientes(Revisor.app(ENTRENADOR_OTRO))).isEmpty();
        assertThat(service.contarPendientes(Revisor.app(ENTRENADOR_OTRO))).isZero();
    }

    @Test
    void quienNoGestionaNingunEquipoNoRevisaNada() {

        solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));

        assertThatThrownBy(() -> service.pendientes(Revisor.app(NADIE))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.contarPendientes(Revisor.app(NADIE))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.buscarJugadores(Revisor.app(NADIE), "luis"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.aprobarHijo(Revisor.app(NADIE), 1, 55)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.rechazarHijo(Revisor.app(NADIE), 1, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void elPanelVeTodoYElHistoricoSoloEsDelPanel() {

        solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));

        assertThat(service.pendientes(Revisor.panel("admin"))).hasSize(1);
        assertThat(service.historico(Revisor.panel("admin"))).isEmpty();
        assertThatThrownBy(() -> service.historico(Revisor.app(COORDINADOR))).isInstanceOf(SecurityException.class);
    }

    @Test
    void laBusquedaManualSoloEnsenaLosJugadoresQueQuienRevisaPuedeVer() {

        assertThat(service.buscarJugadores(Revisor.app(COORDINADOR), "perez")).extracting(c -> c.jugadorId())
                .containsExactly(55L, 56L);
        assertThat(service.buscarJugadores(Revisor.app(ENTRENADOR_ALEVIN), "perez")).extracting(c -> c.jugadorId())
                .containsExactly(55L);
        assertThat(service.buscarJugadores(Revisor.panel("admin"), "garcia")).extracting(c -> c.jugadorId())
                .containsExactly(57L);
        assertThat(service.buscarJugadores(Revisor.app(COORDINADOR), "pe")).isEmpty();
    }

    @Test
    void unEntrenadorNoPuedeAprobarNiRechazarUnHijoSinCoincidenciaEnSusEquipos() {

        long id = solicitudPendiente("ana@example.com", hijo("Eva", "García", null));

        assertThatThrownBy(() -> service.aprobarHijo(Revisor.app(ENTRENADOR_OTRO), hijoId(id, 0), 57))
                .isInstanceOf(SecurityException.class).hasMessage("No puedes revisar esta solicitud");
        assertThatThrownBy(() -> service.rechazarHijo(Revisor.app(ENTRENADOR_OTRO), hijoId(id, 0), "no"))
                .isInstanceOf(SecurityException.class);
        assertThat(dao.obtenerHijos(id).get(0).estado()).isEqualTo("PENDIENTE");
    }

    @Test
    void unEntrenadorSoloPuedeElegirJugadoresDeSusEquipos() {

        // Luis Pérez coincide con el 55 (equipo 8) y el 56 (equipo 9): el entrenador de Alevín B ve al primero...
        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));

        // ...y aunque conozca el id del segundo, no puede aprobar con él.
        assertThatThrownBy(() -> service.aprobarHijo(Revisor.app(ENTRENADOR_ALEVIN), hijoId(id, 0), 56))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Ese jugador no es de un equipo que puedas gestionar");

        verify(familiarDao, never()).guardarFamiliar(any());
        assertThat(dao.obtenerHijos(id).get(0).estado()).isEqualTo("PENDIENTE");
    }

    // ==================================================================================================================
    // Aprobar y rechazar
    // ==================================================================================================================

    @Test
    void aprobarCreaLaFichaLaVinculaAlJugadorYALaCuentaYDaElRolFamiliar() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez Gómez", "Alevín B"));
        int cuenta = cuentas.get("ana@example.com").getId();

        when(familiarDao.obtenerPorEmailOTelefono("ana@example.com", "623 17 68 18")).thenReturn(null);
        when(familiarDao.guardarFamiliar(any())).thenAnswer(i -> {
            ((Familiar) i.getArgument(0)).setId(300L);
            return true;
        });
        when(familiarJugadorDao.existeRelacion(300L, 55L)).thenReturn(false);

        Revision r = service.aprobarHijo(Revisor.app(ENTRENADOR_ALEVIN), hijoId(id, 0), 55);

        // La ficha nueva con los datos de la familia.
        ArgumentCaptor<Familiar> ficha = ArgumentCaptor.forClass(Familiar.class);
        verify(familiarDao).guardarFamiliar(ficha.capture());
        assertThat(ficha.getValue().getNombre()).isEqualTo("Ana");
        assertThat(ficha.getValue().getApellidos()).isEqualTo("Pérez Gil");
        assertThat(ficha.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(ficha.getValue().getTelefono()).isEqualTo("623 17 68 18");

        // El vínculo con el jugador, con el parentesco.
        ArgumentCaptor<FamiliarJugador> relacion = ArgumentCaptor.forClass(FamiliarJugador.class);
        verify(familiarJugadorDao).guardarFamiliarJugador(relacion.capture());
        assertThat(relacion.getValue().getFamiliarId()).isEqualTo(300L);
        assertThat(relacion.getValue().getJugadorId()).isEqualTo(55L);
        assertThat(relacion.getValue().getParentesco()).isEqualTo("Madre");

        // El vínculo de la cuenta y el rol, por la misma vía que las invitaciones del club.
        ArgumentCaptor<AnadirVinculosRequest> vinculo = ArgumentCaptor.forClass(AnadirVinculosRequest.class);
        verify(usuarioAppService).agregarVinculosAUsuarioExistente(eq(cuenta), vinculo.capture());
        assertThat(vinculo.getValue().getVinculos()).singleElement().satisfies(v -> {
            assertThat(v.getTipo()).isEqualTo("FAMILIAR");
            assertThat(v.getPersonaId()).isEqualTo(300L);
        });

        // El hijo, la solicitud y el aviso (al familiar, con el nombre del jugador real).
        assertThat(r.estado()).isEqualTo("APROBADA");
        assertThat(r.hijos().get(0).estado()).isEqualTo("APROBADO");
        assertThat(r.hijos().get(0).jugador().jugadorId()).isEqualTo(55L);
        assertThat(dao.obtenerPorId(id).revisadaPorId()).isEqualTo(ENTRENADOR_ALEVIN);
        assertThat(dao.obtenerPorId(id).fechaRevision()).isEqualTo(AHORA);
        verify(avisos).avisarAprobada((long) cuenta, "Luis Pérez Gómez", id);
        // Primer hijo aprobado de la solicitud: email completo, al email que escribió la familia.
        verify(avisos).emailAprobada((long) cuenta, "ana@example.com", "Ana", "Luis Pérez Gómez", true);
    }

    @Test
    void aprobarReutilizaLaFichaQueYaExisteConEseEmailOTelefono() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez Gómez", null));

        Familiar existente = new Familiar();
        existente.setId(77L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(existente);
        when(familiarJugadorDao.existeRelacion(77L, 55L)).thenReturn(false);

        service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 55);

        verify(familiarDao, never()).guardarFamiliar(any());
        verify(familiarJugadorDao).guardarFamiliarJugador(any());

        ArgumentCaptor<AnadirVinculosRequest> vinculo = ArgumentCaptor.forClass(AnadirVinculosRequest.class);
        verify(usuarioAppService).agregarVinculosAUsuarioExistente(anyInt(), vinculo.capture());
        assertThat(vinculo.getValue().getVinculos().get(0).getPersonaId()).isEqualTo(77L);
    }

    @Test
    void siLaFichaYaEstaVinculadaAlJugadorNoSeDuplicaLaRelacion() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez Gómez", null));

        Familiar existente = new Familiar();
        existente.setId(77L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(existente);
        when(familiarJugadorDao.existeRelacion(77L, 55L)).thenReturn(true);

        service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 55);

        verify(familiarJugadorDao, never()).guardarFamiliarJugador(any());
    }

    @Test
    void elSegundoHijoReutilizaLaFichaYLaCuentaYaVinculadas() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez Gómez", null), hijo("Eva", "García", null));
        int cuenta = cuentas.get("ana@example.com").getId();

        Familiar ficha = new Familiar();
        ficha.setId(300L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(null, ficha);
        when(familiarDao.guardarFamiliar(any())).thenAnswer(i -> {
            ((Familiar) i.getArgument(0)).setId(300L);
            return true;
        });

        service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 55);

        // Ahora la cuenta ya está vinculada a la ficha 300 y tiene el rol.
        VinculoUsuarioApp vinculado = new VinculoUsuarioApp();
        vinculado.setTipo("FAMILIAR");
        vinculado.setPersonaId(300L);
        when(vinculoDao.obtenerVinculos(cuenta)).thenReturn(List.of(vinculado));
        when(usuarioAppService.tieneRol(cuenta, "FAMILIAR")).thenReturn(true);

        Revision r = service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 1), 57);

        // Es el segundo hijo aprobado de la misma solicitud: email corto, no el completo.
        verify(avisos).emailAprobada((long) cuenta, "ana@example.com", "Ana", "Luis Pérez Gómez", true);
        verify(avisos).emailAprobada((long) cuenta, "ana@example.com", "Ana", "Eva García", false);

        verify(usuarioAppService, times(1)).agregarVinculosAUsuarioExistente(anyInt(), any());
        verify(familiarDao, times(1)).guardarFamiliar(any());
        verify(familiarJugadorDao, times(2)).guardarFamiliarJugador(any());
        assertThat(r.estado()).isEqualTo("APROBADA");
    }

    @Test
    void unaFichaVinculadaAOtraCuentaNoSeUsaYSeExplicaElProblema() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez Gómez", null));

        Familiar existente = new Familiar();
        existente.setId(77L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(existente);
        when(vinculoDao.familiarTieneCuenta(77L)).thenReturn(true);

        assertThatThrownBy(() -> service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 55))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("vinculada a otra cuenta");

        verify(usuarioAppService, never()).agregarVinculosAUsuarioExistente(anyInt(), any());
        verify(avisos, never()).avisarAprobada(any(), any(), any());
    }

    @Test
    void aprobarConUnJugadorInexistenteOYaAprobadoFalla() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez Gómez", null));

        assertThatThrownBy(() -> service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 999))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El jugador no existe o no está activo");
        assertThatThrownBy(() -> service.aprobarHijo(Revisor.app(COORDINADOR), 12345, 55))
                .isInstanceOf(IllegalArgumentException.class);

        Familiar f = new Familiar();
        f.setId(1L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(f);
        service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 55);

        assertThatThrownBy(() -> service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 55))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La solicitud ya no está pendiente de revisar");
    }

    @Test
    void rechazarGuardaElMotivoYAvisaAlFamiliarSinDarLeRolNiVinculo() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));
        int cuenta = cuentas.get("ana@example.com").getId();

        Revision r = service.rechazarHijo(Revisor.panel("admin"), hijoId(id, 0), "  No lo encontramos  ");

        assertThat(r.estado()).isEqualTo("RECHAZADA");
        assertThat(r.motivoRechazo()).isEqualTo("No lo encontramos");
        assertThat(r.hijos().get(0).estado()).isEqualTo("RECHAZADO");
        assertThat(dao.obtenerPorId(id).revisadaPorPanel()).isEqualTo("admin");
        assertThat(dao.obtenerPorId(id).revisadaPorId()).isNull();

        verify(avisos).avisarRechazada((long) cuenta, "Luis Pérez", "No lo encontramos", id);
        verify(avisos).emailRechazada((long) cuenta, "ana@example.com", "Ana", "Luis Pérez", "No lo encontramos");
        verify(usuarioAppService, never()).asignarRol(anyInt(), anyInt());
        verify(usuarioAppService, never()).agregarVinculosAUsuarioExistente(anyInt(), any());
        verify(familiarJugadorDao, never()).guardarFamiliarJugador(any());
    }

    @Test
    void rechazarSinMotivoEsValidoYUnMotivoLargoNo() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));

        assertThatThrownBy(() -> service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);

        Revision r = service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), "   ");

        assertThat(r.estado()).isEqualTo("RECHAZADA");
        assertThat(r.motivoRechazo()).isNull();
        verify(avisos).avisarRechazada(any(), any(), eq(null), any());
    }

    // ---- varios hijos --------------------------------------------------------------------------------------------------

    @Test
    void conVariosHijosLaSolicitudSigueAbiertaHastaRevisarlosTodos() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez Gómez", null), hijo("Eva", "García", null),
                hijo("Pau", "Nadie", null));

        Familiar f = new Familiar();
        f.setId(1L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(f);

        service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), 55);
        assertThat(dao.obtenerPorId(id).estado()).isEqualTo("PENDIENTE");
        assertThat(dao.obtenerPorId(id).fechaRevision()).isNull();

        service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 1), "Es de otro club");
        assertThat(dao.obtenerPorId(id).estado()).isEqualTo("PENDIENTE");

        // Cuando se revisa el último, alguno aprobado: APROBADA (con el motivo del rechazado guardado en la solicitud).
        Revision r = service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 2), "No existe");

        assertThat(r.estado()).isEqualTo("APROBADA");
        assertThat(r.motivoRechazo()).isEqualTo("Es de otro club / No existe");
        assertThat(r.hijos()).extracting(h -> h.estado()).containsExactly("APROBADO", "RECHAZADO", "RECHAZADO");
        assertThat(service.pendientes(Revisor.app(COORDINADOR))).isEmpty();
    }

    @Test
    void siSeRechazanTodosLosHijosLaSolicitudEsRechazada() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null), hijo("Eva", "García", null));

        service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), "Mismo motivo");
        Revision r = service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 1), "Mismo motivo");

        assertThat(r.estado()).isEqualTo("RECHAZADA");
        assertThat(r.motivoRechazo()).isEqualTo("Mismo motivo");
        assertThat(dao.obtenerPorId(id).fechaRevision()).isEqualTo(AHORA);
    }

    @Test
    void dosEntrenadoresDeEquiposDistintosRevisanCadaUnoSuHijo() {

        long id = solicitudPendiente("ana@example.com", hijo("Eva", "García", null), hijo("Luis", "Pérez Ruiz", null));
        Familiar f = new Familiar();
        f.setId(1L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(f);

        service.aprobarHijo(Revisor.app(ENTRENADOR_ALEVIN), hijoId(id, 0), 57);

        assertThat(dao.obtenerPorId(id).estado()).isEqualTo("PENDIENTE");
        // El entrenador de Alevín B ya no tiene nada pendiente; al de Benjamín A le queda su hijo.
        assertThat(service.pendientes(Revisor.app(ENTRENADOR_ALEVIN))).isEmpty();
        assertThat(service.pendientes(Revisor.app(ENTRENADOR_OTRO))).hasSize(1);

        service.aprobarHijo(Revisor.app(ENTRENADOR_OTRO), hijoId(id, 1), 56);

        assertThat(dao.obtenerPorId(id).estado()).isEqualTo("APROBADA");
    }

    @Test
    void elHistoricoDelPanelSoloTieneLasRevisadasDeLosUltimosSieteDias() {

        long reciente = solicitudPendiente("a@example.com", hijo("Luis", "Pérez", null));
        service.rechazarHijo(Revisor.panel("admin"), hijoId(reciente, 0), null);

        long antigua = solicitudPendiente("b@example.com", hijo("Eva", "García", null));
        service.rechazarHijo(Revisor.panel("admin"), hijoId(antigua, 0), null);
        dao.cerrar(antigua, "RECHAZADA", null, AHORA.minusDays(8), null, "admin");

        assertThat(service.historico(Revisor.panel("admin"))).extracting(Revision::id).containsExactly(reciente);
    }

    // ==================================================================================================================
    // Caducidad y recordatorio
    // ==================================================================================================================

    @Test
    void caducaLoQueLlevaMasDeSieteDiasSinRevisarYBorraLosDatosYLaCuentaSinRoles() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));
        int cuenta = cuentas.get("ana@example.com").getId();
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(8)));

        List<Caducada> avisar = service.caducarVencidas();

        assertThat(dao.obtenerPorId(id)).isNull();
        assertThat(dao.obtenerHijos(id)).isEmpty();
        verify(cuentaAppService).eliminarCuentaPorSistema(cuenta);
        assertThat(avisar).singleElement().satisfies(c -> {
            assertThat(c.email()).isEqualTo("ana@example.com");
            assertThat(c.nombre()).isEqualTo("Ana");
            assertThat(c.idioma()).isEqualTo("es");
        });
    }

    @Test
    void unaSolicitudSinConfirmarElEmailTambienCaduca() {

        service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));
        long id = dao.solicitudes.keySet().iterator().next();
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(8)));

        List<Caducada> avisar = service.caducarVencidas();

        // Caducó sin revisarse y el registro creó la cuenta: se avisa y la cuenta se ha borrado (variante 1).
        assertThat(avisar).singleElement().extracting(Caducada::cuentaEliminada).isEqualTo(true);
        assertThat(dao.solicitudes).isEmpty();
        verify(cuentaAppService).eliminarCuentaPorSistema(anyInt());
    }

    @Test
    void unaRechazadaCaducaSieteDiasDespuesDeLaRevisionNoDeLaSolicitud() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(30)));
        service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), null);

        // Revisada ahora: aunque se pidió hace 30 días, todavía no caduca.
        assertThat(service.caducarVencidas()).isEmpty();
        assertThat(dao.obtenerPorId(id)).isNotNull();

        dao.cerrar(id, "RECHAZADA", null, AHORA.minusDays(8), (long) COORDINADOR, null);
        // Al borrar una rechazada (la cuenta sin roles también) no se avisa por email: ya recibió el del rechazo.
        assertThat(service.caducarVencidas()).isEmpty();
        verify(cuentaAppService).eliminarCuentaPorSistema(anyInt());
        assertThat(dao.obtenerPorId(id)).isNull();
    }

    @Test
    void laSolicitudAprobadaNoCaducaNuncaYLaRecienteTampoco() {

        long aprobada = solicitudPendiente("a@example.com", hijo("Luis", "Pérez Gómez", null));
        Familiar f = new Familiar();
        f.setId(1L);
        when(familiarDao.obtenerPorEmailOTelefono(any(), any())).thenReturn(f);
        service.aprobarHijo(Revisor.app(COORDINADOR), hijoId(aprobada, 0), 55);
        dao.cerrar(aprobada, "APROBADA", null, AHORA.minusDays(40), (long) COORDINADOR, null);

        long reciente = solicitudPendiente("b@example.com", hijo("Eva", "García", null));

        assertThat(service.caducarVencidas()).isEmpty();
        assertThat(dao.obtenerPorId(aprobada)).isNotNull();
        assertThat(dao.obtenerPorId(reciente)).isNotNull();
        verify(cuentaAppService, never()).eliminarCuentaPorSistema(anyInt());
    }

    @Test
    void unaCuentaConRolesNuncaSeBorraAunqueCaduqueLaSolicitud() {

        UsuarioApp cuenta = cuentaActiva(7, "marta@example.com");
        RolApp entrenador = new RolApp();
        entrenador.setCodigo("ENTRENADOR");
        when(usuarioAppService.obtenerRoles(7)).thenReturn(List.of(entrenador));

        service.solicitarConSesion(7, peticion("marta@example.com", hijo("Luis", "Pérez", null)));
        long id = dao.solicitudes.keySet().iterator().next();
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(9)));

        List<Caducada> avisar = service.caducarVencidas();

        // Los datos de la solicitud se borran, pero la cuenta se queda como está.
        assertThat(dao.obtenerPorId(id)).isNull();
        verify(cuentaAppService, never()).eliminarCuentaPorSistema(anyInt());
        assertThat(avisar).hasSize(1);
        assertThat(cuenta.isActivo()).isTrue();
        // Se avisa igual (caducó sin revisarse) pero con la variante de la cuenta que se conserva.
        assertThat(avisar).singleElement().extracting(Caducada::cuentaEliminada).isEqualTo(false);
    }

    @Test
    void unaCuentaSinRolesPeroConVinculosNoSeBorra() {

        service.registrarPublica(peticion("ana@example.com", hijo("Luis", "Pérez", null)));
        int cuenta = cuentas.get("ana@example.com").getId();
        when(vinculoDao.obtenerVinculos(cuenta)).thenReturn(List.of(new VinculoUsuarioApp()));
        long id = dao.solicitudes.keySet().iterator().next();
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(9)));

        service.caducarVencidas();

        verify(cuentaAppService, never()).eliminarCuentaPorSistema(anyInt());
    }

    @Test
    void unaSolicitudQueFallaAlCaducarNoImpideLimpiarLasDemas() {

        long primera = solicitudPendiente("a@example.com", hijo("Luis", "Pérez", null));
        long segunda = solicitudPendiente("b@example.com", hijo("Eva", "García", null));
        dao.solicitudes.put(primera, SolicitudFamiliaConFecha(dao.solicitudes.get(primera), AHORA.minusDays(9)));
        dao.solicitudes.put(segunda, SolicitudFamiliaConFecha(dao.solicitudes.get(segunda), AHORA.minusDays(9)));
        int cuentaA = cuentas.get("a@example.com").getId();
        org.mockito.Mockito.doThrow(new RuntimeException("fallo")).when(cuentaAppService)
                .eliminarCuentaPorSistema(cuentaA);

        List<Caducada> avisar = service.caducarVencidas();

        assertThat(dao.obtenerPorId(segunda)).isNull();
        assertThat(avisar).extracting(Caducada::email).contains("b@example.com");
    }

    @Test
    void elRecordatorioSoloSaleSiHaySolicitudesPendientesDeMasDeDosDias() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));
        when(avisos.recordarACoordinacion()).thenReturn(3);

        // Hace 1 día: nada.
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(1)));
        assertThat(service.recordarPendientes()).isZero();
        verify(avisos, never()).recordarACoordinacion();

        // Hace 3 días: recordatorio.
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(3)));
        assertThat(service.recordarPendientes()).isEqualTo(3);
        verify(avisos).recordarACoordinacion();
    }

    @Test
    void siSoloHayYaRevisadasNoHayRecordatorio() {

        long id = solicitudPendiente("ana@example.com", hijo("Luis", "Pérez", null));
        service.rechazarHijo(Revisor.app(COORDINADOR), hijoId(id, 0), null);
        dao.solicitudes.put(id, SolicitudFamiliaConFecha(dao.solicitudes.get(id), AHORA.minusDays(5)));

        assertThat(service.recordarPendientes()).isZero();
        verify(avisos, never()).recordarACoordinacion();
        verify(familiarDao, never()).guardarFamiliar(any());
        verify(usuarioAppService, never()).eliminarRol(anyInt(), anyInt());
        verify(equipoGestionDao, never()).obtenerCoordinadores();
        verify(familiarJugadorDao, never()).eliminar(anyLong());
    }

    private static SolicitudFamilia SolicitudFamiliaConFecha(SolicitudFamilia s, LocalDateTime fecha) {
        return new SolicitudFamilia(s.id(), s.usuarioAppId(), s.nombre(), s.apellidos(), s.email(), s.telefono(),
                s.parentesco(), s.estado(), s.motivoRechazo(), fecha, s.fechaRevision(), s.revisadaPorId(),
                s.revisadaPorPanel());
    }
}
