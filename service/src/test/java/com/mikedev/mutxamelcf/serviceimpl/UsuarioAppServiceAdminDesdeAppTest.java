package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioAdminApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;

/**
 * La administración de usuarios desde la app (ADMIN_APP): permisos, listado con filtros y las reglas de seguridad,
 * comprobadas en el servicio y no solo en la ruta.
 */
class UsuarioAppServiceAdminDesdeAppTest {

    private static final int ADMIN = 7;
    private static final int OTRO_ADMIN = 8;
    private static final int FAMILIA = 30;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);

    private UsuarioAppDao usuarioAppDao;
    private UsuarioAppVinculoDao vinculoDao;
    private RolAppDao rolAppDao;
    private UsuarioAppServiceImpl service;

    @BeforeEach
    void setUp() {
        usuarioAppDao = mock(UsuarioAppDao.class);
        vinculoDao = mock(UsuarioAppVinculoDao.class);
        rolAppDao = mock(RolAppDao.class);
        service = new UsuarioAppServiceImpl(usuarioAppDao, vinculoDao, rolAppDao, mock(com.mikedev.mutxamelcf.dao.LimpiezaCuentaAppDao.class), mock(PasswordEncoder.class),
                mock(JwtService.class), CLOCK);

        roles(ADMIN, "ADMIN_APP");
        roles(OTRO_ADMIN, "ADMIN_APP");
        roles(FAMILIA, "FAMILIAR");
    }

    private static RolApp rol(int id, String codigo, String nombre) {
        RolApp rol = new RolApp();
        rol.setId(id);
        rol.setCodigo(codigo);
        rol.setNombre(nombre);
        return rol;
    }

    private void roles(int usuario, String... codigos) {
        int id = 1;
        List<RolApp> lista = new java.util.ArrayList<>();
        for (String c : codigos) {
            lista.add(rol(id++, c, c));
        }
        when(rolAppDao.obtenerPorUsuario(usuario)).thenReturn(lista);
    }

    private UsuarioApp cuenta(int id, String email, String nombre, boolean activo, boolean conToken, boolean caducado,
            boolean activadaAlgunaVez) {
        UsuarioApp u = new UsuarioApp();
        u.setId(id);
        u.setEmail(email);
        u.setNombre(nombre);
        u.setActivo(activo);
        if (conToken) {
            u.setTokenActivacion("123456");
            u.setFechaExpiracionToken(Timestamp.from(Instant.parse(caducado ? "2026-11-01T00:00:00Z"
                    : "2026-12-30T00:00:00Z")));
        }
        if (activadaAlgunaVez) {
            u.setFechaActivacion(Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")));
        }
        when(usuarioAppDao.obtenerPorId(id)).thenReturn(u);
        return u;
    }

    // ---- permisos ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ENTRENADOR", "RRSS", "RETRANSMISION", "FAMILIAR" })
    void quienNoEsAdminAppRecibeSecurityExceptionEnTodasLasOperacionesYNoSeTocaNada(String rolDelSolicitante) {

        int solicitante = 50;
        roles(solicitante, rolDelSolicitante);
        AnadirVinculosRequest anadir = new AnadirVinculosRequest();
        anadir.setVinculos(List.of(new VinculoSolicitado("COORDINADOR", null)));

        List<org.junit.jupiter.api.function.Executable> operaciones = List.of(
                () -> service.listarUsuariosDesdeApp(solicitante, null, null, null),
                () -> service.obtenerOpcionesUsuariosDesdeApp(solicitante),
                () -> service.actualizarNombreDesdeApp(solicitante, FAMILIA, "Ana", null),
                () -> service.agregarVinculosDesdeApp(solicitante, FAMILIA, anadir),
                () -> service.quitarVinculoDesdeApp(solicitante, FAMILIA, new VinculoSolicitado("COORDINADOR", null)),
                () -> service.activarDesdeApp(solicitante, FAMILIA),
                () -> service.desactivarDesdeApp(solicitante, FAMILIA),
                () -> service.eliminarInvitacionDesdeApp(solicitante, FAMILIA),
                () -> service.reenviarInvitacionDesdeApp(solicitante, FAMILIA),
                () -> service.exigirAdminApp(solicitante, "Solo un administrador"));

        for (org.junit.jupiter.api.function.Executable operacion : operaciones) {
            assertThatThrownBy(operacion::execute).isInstanceOf(SecurityException.class);
        }

        verify(usuarioAppDao, never()).desactivarUsuario(anyInt());
        verify(usuarioAppDao, never()).activarUsuario(anyInt());
        verify(usuarioAppDao, never()).eliminar(anyInt());
        verify(usuarioAppDao, never()).listarTodos();
    }

    // ---- listado y filtros ------------------------------------------------------------------------------------------

    private void unaListaDeCuentas() {
        UsuarioApp activa = cuenta(1, "ana.perez@example.com", "Ana Pérez", true, false, false, true);
        UsuarioApp pendiente = cuenta(2, "luis@example.com", "Luis", false, true, false, false);
        UsuarioApp caducada = cuenta(3, "marta@example.com", "Marta", false, true, true, false);
        UsuarioApp desactivada = cuenta(4, "pepe@example.com", "Pepe", false, false, false, true);
        UsuarioApp sinRoles = cuenta(5, "sinroles@example.com", "Sin roles", true, false, false, true);

        when(usuarioAppDao.listarTodos()).thenReturn(List.of(activa, pendiente, caducada, desactivada, sinRoles));
        when(rolAppDao.obtenerTodos()).thenReturn(List.of(rol(1, "JUGADOR", "Jugador"),
                rol(2, "RETRANSMISION", "Retransmisión"), rol(3, "RRSS", "Redes sociales")));

        roles(1, "RRSS", "RETRANSMISION");
        roles(2, "JUGADOR");
        roles(3, "JUGADOR");
        roles(4, "RRSS");
        roles(5);
        when(vinculoDao.obtenerVinculos(anyInt())).thenReturn(List.of());
        when(vinculoDao.obtenerVinculos(2)).thenReturn(List.of(new VinculoUsuarioApp("JUGADOR", 9L, "Luis Mas", "Alevín A")));
    }

    @Test
    void elListadoTraeLosDatosDeLaTablaDelPanelConLosNombresDeLosRoles() {

        unaListaDeCuentas();

        List<UsuarioAdminApp> todos = service.listarUsuariosDesdeApp(ADMIN, null, null, null);

        assertThat(todos).extracting(UsuarioAdminApp::id).containsExactly(1, 2, 3, 4, 5);
        assertThat(todos).extracting(UsuarioAdminApp::estado)
                .containsExactly("ACTIVA", "PENDIENTE", "CADUCADA", "DESACTIVADA", "ACTIVA");

        UsuarioAdminApp ana = todos.get(0);
        assertThat(ana.nombre()).isEqualTo("Ana Pérez");
        assertThat(ana.roles()).extracting(UsuarioAdminApp.Rol::codigo, UsuarioAdminApp.Rol::nombre)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("RRSS", "Redes sociales"),
                        org.assertj.core.groups.Tuple.tuple("RETRANSMISION", "Retransmisión"));

        // Con ficha, el nombre visible es el de la ficha, y el vínculo trae tipo, persona y detalle.
        UsuarioAdminApp luis = todos.get(1);
        assertThat(luis.nombre()).isEqualTo("Luis Mas");
        assertThat(luis.vinculos()).extracting(UsuarioAdminApp.Vinculo::tipo, UsuarioAdminApp.Vinculo::personaId,
                UsuarioAdminApp.Vinculo::nombre, UsuarioAdminApp.Vinculo::detalle)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("JUGADOR", 9L, "Luis Mas", "Alevín A"));
    }

    @Test
    void filtraPorTextoSinAcentosNiMayusculasEnNombreYEmail() {

        unaListaDeCuentas();

        assertThat(service.listarUsuariosDesdeApp(ADMIN, "PEREZ", null, null)).extracting(UsuarioAdminApp::id)
                .containsExactly(1);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, "ana perez", null, null)).extracting(UsuarioAdminApp::id)
                .containsExactly(1);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, "pepe@example", null, null)).extracting(UsuarioAdminApp::id)
                .containsExactly(4);
        // Un texto que solo está en el rol no encuentra nada: busca solo en nombre y email.
        assertThat(service.listarUsuariosDesdeApp(ADMIN, "Redes", null, null)).isEmpty();
    }

    @Test
    void filtraPorRolYPorSinRoles() {

        unaListaDeCuentas();

        assertThat(service.listarUsuariosDesdeApp(ADMIN, null, "RRSS", null)).extracting(UsuarioAdminApp::id)
                .containsExactly(1, 4);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, null, "jugador", null)).extracting(UsuarioAdminApp::id)
                .containsExactly(2, 3);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, null, "SIN_ROLES", null)).extracting(UsuarioAdminApp::id)
                .containsExactly(5);
    }

    @Test
    void filtraPorEstadoYLosFiltrosSeCombinan() {

        unaListaDeCuentas();

        assertThat(service.listarUsuariosDesdeApp(ADMIN, null, null, "ACTIVA")).extracting(UsuarioAdminApp::id)
                .containsExactly(1, 5);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, null, null, "pendiente")).extracting(UsuarioAdminApp::id)
                .containsExactly(2);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, null, null, "CADUCADA")).extracting(UsuarioAdminApp::id)
                .containsExactly(3);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, null, null, "DESACTIVADA")).extracting(UsuarioAdminApp::id)
                .containsExactly(4);
        assertThat(service.listarUsuariosDesdeApp(ADMIN, "example", "RRSS", "ACTIVA")).extracting(UsuarioAdminApp::id)
                .containsExactly(1);
        assertThatThrownBy(() -> service.listarUsuariosDesdeApp(ADMIN, null, null, "OTRO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lasOpcionesNoOfrecenAdminAppYTraenLasPersonasVinculables() {

        when(rolAppDao.obtenerPorCodigo("COORDINADOR")).thenReturn(rol(4, "COORDINADOR", "Coordinador"));
        when(rolAppDao.obtenerPorCodigo("RETRANSMISION")).thenReturn(rol(5, "RETRANSMISION", "Retransmisión"));
        when(rolAppDao.obtenerPorCodigo("RRSS")).thenReturn(rol(6, "RRSS", "Redes sociales"));
        when(rolAppDao.obtenerPorCodigo("ADMIN_APP")).thenReturn(rol(1, "ADMIN_APP", "Administrador"));

        var opciones = service.obtenerOpcionesUsuariosDesdeApp(ADMIN);

        assertThat(opciones.roles()).extracting("codigo").containsExactly("COORDINADOR", "RETRANSMISION", "RRSS");
        assertThat(opciones.personas()).isNotNull();
    }

    // ---- reglas de seguridad ----------------------------------------------------------------------------------------

    @Test
    void unAdminNoPuedeDesactivarseASiMismoNiADesactivarAOtroAdmin() {

        cuenta(ADMIN, "admin@example.com", "Admin", true, false, false, true);
        cuenta(OTRO_ADMIN, "otro@example.com", "Otro", true, false, false, true);

        assertThatThrownBy(() -> service.desactivarDesdeApp(ADMIN, ADMIN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propia cuenta");
        assertThatThrownBy(() -> service.desactivarDesdeApp(ADMIN, OTRO_ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("administrador");

        verify(usuarioAppDao, never()).desactivarUsuario(anyInt());
    }

    @Test
    void unAdminDesactivaYActivaUnaCuentaNormal() {

        cuenta(FAMILIA, "familia@example.com", "Ana", true, false, false, true);

        service.desactivarDesdeApp(ADMIN, FAMILIA);
        verify(usuarioAppDao).desactivarUsuario(FAMILIA);

        service.activarDesdeApp(ADMIN, FAMILIA);
        verify(usuarioAppDao).activarUsuario(FAMILIA);
    }

    @Test
    void unAdminNoPuedeQuitarseElRolNiQuitarRolesAOtroAdmin() {

        cuenta(OTRO_ADMIN, "otro@example.com", "Otro", true, false, false, true);

        assertThatThrownBy(() -> service.quitarVinculoDesdeApp(ADMIN, ADMIN, new VinculoSolicitado("ADMIN_APP", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ti mismo");
        assertThatThrownBy(() -> service.quitarVinculoDesdeApp(ADMIN, OTRO_ADMIN,
                new VinculoSolicitado("COORDINADOR", null))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro administrador");
        assertThatThrownBy(() -> service.quitarVinculoDesdeApp(ADMIN, OTRO_ADMIN,
                new VinculoSolicitado("ADMIN_APP", null))).isInstanceOf(IllegalArgumentException.class);

        verify(rolAppDao, never()).eliminarRol(anyInt(), anyInt());
    }

    @Test
    void unAdminQuitaUnRolAUnaCuentaNormal() {

        cuenta(FAMILIA, "familia@example.com", "Ana", true, false, false, true);
        when(rolAppDao.obtenerPorCodigo("COORDINADOR")).thenReturn(rol(9, "COORDINADOR", "Coordinador"));

        service.quitarVinculoDesdeApp(ADMIN, FAMILIA, new VinculoSolicitado("COORDINADOR", null));

        verify(rolAppDao).eliminarRol(FAMILIA, 9);
    }

    @Test
    void nadieAsignaAdminAppDesdeLaApp() {

        cuenta(FAMILIA, "familia@example.com", "Ana", true, false, false, true);
        AnadirVinculosRequest peticion = new AnadirVinculosRequest();
        peticion.setVinculos(List.of(new VinculoSolicitado("admin_app", null)));

        assertThatThrownBy(() -> service.agregarVinculosDesdeApp(ADMIN, FAMILIA, peticion))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ADMIN_APP");

        verify(rolAppDao, never()).asignarRol(anyInt(), anyInt());
    }

    @Test
    void unAdminAnadeUnRolSinPersonaAUnaCuenta() {

        cuenta(FAMILIA, "familia@example.com", "Ana", true, false, false, true);
        when(rolAppDao.obtenerPorCodigo("RRSS")).thenReturn(rol(6, "RRSS", "Redes sociales"));
        when(rolAppDao.obtenerPorUsuario(FAMILIA)).thenReturn(List.of());
        AnadirVinculosRequest peticion = new AnadirVinculosRequest();
        peticion.setVinculos(List.of(new VinculoSolicitado("RRSS", null)));

        service.agregarVinculosDesdeApp(ADMIN, FAMILIA, peticion);

        verify(rolAppDao).asignarRol(FAMILIA, 6);
    }

    @Test
    void unAdminEditaElNombreDeUnaCuentaSinFicha() {

        cuenta(FAMILIA, "familia@example.com", null, true, false, false, true);
        when(vinculoDao.obtenerVinculos(FAMILIA)).thenReturn(List.of());

        service.actualizarNombreDesdeApp(ADMIN, FAMILIA, "Ana", "Gil");

        verify(usuarioAppDao).actualizarNombre(FAMILIA, "Ana", "Gil");
    }

    @Test
    void soloSeEliminanInvitacionesSinActivarYNuncaLaCuentaDeUnAdmin() {

        cuenta(FAMILIA, "familia@example.com", "Ana", true, false, false, true);
        cuenta(40, "invitada@example.com", "Luis", false, true, false, false);
        cuenta(OTRO_ADMIN, "otro@example.com", "Otro", false, true, false, false);

        // Activa: no se elimina (como en el panel).
        assertThatThrownBy(() -> service.eliminarInvitacionDesdeApp(ADMIN, FAMILIA))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ya activa");
        verify(usuarioAppDao, never()).eliminar(FAMILIA);

        // Otro admin o uno mismo: no.
        assertThatThrownBy(() -> service.eliminarInvitacionDesdeApp(ADMIN, OTRO_ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.eliminarInvitacionDesdeApp(ADMIN, ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        verify(usuarioAppDao, never()).eliminar(OTRO_ADMIN);

        // Una invitación sin activar sí.
        service.eliminarInvitacionDesdeApp(ADMIN, 40);
        verify(usuarioAppDao).eliminar(40);
        verify(vinculoDao).desvincularTodo(40);
    }

    @Test
    void reenviarInvitacionGeneraElCodigoComoElPanelParaUnaCuentaNoActiva() {

        cuenta(40, "invitada@example.com", "Luis", false, true, false, false);
        when(vinculoDao.obtenerVinculos(40)).thenReturn(List.of());

        var invitacion = service.reenviarInvitacionDesdeApp(ADMIN, 40);

        assertThat(invitacion.getEmail()).isEqualTo("invitada@example.com");
        assertThat(invitacion.getTokenActivacion()).isNotBlank();

        cuenta(FAMILIA, "familia@example.com", "Ana", true, false, false, true);
        assertThatThrownBy(() -> service.reenviarInvitacionDesdeApp(ADMIN, FAMILIA))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ya está activa");
        verify(usuarioAppDao, never()).eliminar(any(Integer.class));
    }
}
