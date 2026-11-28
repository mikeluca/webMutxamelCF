package com.mikedev.mutxamelcf.mvc.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.mvc.communication.CuentaActivadaNotifier;
import com.mikedev.mutxamelcf.mvc.communication.InvitacionUsuario;
import com.mikedev.mutxamelcf.mvc.communication.ReenvioActivacion;
import com.mikedev.mutxamelcf.mvc.controller.AdminUsuariosAppController;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/** Eliminar una invitación: el endpoint de la app y el del panel responden igual ante cada error del servicio. */
class EliminarInvitacionControllersTest {

    private static final String CLAVE_APP = "$.error";
    private static final String CLAVE_PANEL = "$.error";

    private static final String MENSAJE_CONFLICTO = "No se puede eliminar la invitación porque la cuenta tiene datos "
            + "asociados. Desactívala en vez de eliminarla.";

    private static final String MENSAJE_ACTIVIDAD = "Esta cuenta tiene actividad registrada (justificaciones de falta "
            + "a partidos). Desactívala en vez de eliminarla.";

    private static final org.springframework.http.converter.json.MappingJackson2HttpMessageConverter JSON =
            new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                    new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

    private UsuarioAppService servicio;
    private MockMvc app;
    private MockMvc panel;
    private final Principal admin = autenticado();

    private static Principal autenticado() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(true);
        return auth;
    }

    @BeforeEach
    void setUp() {
        servicio = mock(UsuarioAppService.class);
        app = MockMvcBuilders.standaloneSetup(new AppAdminUsuariosController(servicio, mock(InvitacionUsuario.class),
                mock(ReenvioActivacion.class), mock(CuentaActivadaNotifier.class))).setMessageConverters(JSON).build();
        panel = MockMvcBuilders.standaloneSetup(new AdminUsuariosAppController(servicio, mock(InvitacionUsuario.class),
                mock(CuentaActivadaNotifier.class), mock(ReenvioActivacion.class), mock(RolAppDao.class)))
                .setMessageConverters(JSON).build();
    }

    @Test
    void sinErroresResponden200ConElMismoMensaje() throws Exception {
        app.perform(post("/api/app/admin/usuarios/5/eliminar").principal(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Invitación eliminada."));
        panel.perform(post("/admin/usuarios-app/5/eliminar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Invitación eliminada."));
    }

    @Test
    void conActividadResponden400ConElMensajeDelServicio() throws Exception {
        doThrow(new IllegalStateException(MENSAJE_ACTIVIDAD)).when(servicio).eliminarInvitacionDesdeApp(1, 5);
        doThrow(new IllegalStateException(MENSAJE_ACTIVIDAD)).when(servicio).eliminarInvitacion(5);

        app.perform(post("/api/app/admin/usuarios/5/eliminar").principal(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath(CLAVE_APP).value(MENSAJE_ACTIVIDAD));
        panel.perform(post("/admin/usuarios-app/5/eliminar"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath(CLAVE_PANEL).value(MENSAJE_ACTIVIDAD));
    }

    @Test
    void siLaBaseDeDatosRechazaElBorradoResponden409ConElMismoMensaje() throws Exception {
        DataIntegrityViolationException fk = new DataIntegrityViolationException("borrado",
                new RuntimeException("ORA-02292: integrity constraint (SYSTEM.FK_SOLFAM_USUARIO) violated"));
        doThrow(fk).when(servicio).eliminarInvitacionDesdeApp(1, 5);
        doThrow(fk).when(servicio).eliminarInvitacion(5);

        app.perform(post("/api/app/admin/usuarios/5/eliminar").principal(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath(CLAVE_APP).value(MENSAJE_CONFLICTO));
        panel.perform(post("/admin/usuarios-app/5/eliminar"))
                .andExpect(status().isConflict()).andExpect(jsonPath(CLAVE_PANEL).value(MENSAJE_CONFLICTO));
    }

    @Test
    void unaCuentaActivaResponde400EnAmbos() throws Exception {
        String mensaje = "No se puede eliminar una cuenta ya activa. Desactívala si quieres revocarle el acceso.";
        doThrow(new IllegalStateException(mensaje)).when(servicio).eliminarInvitacionDesdeApp(1, 6);
        doThrow(new IllegalStateException(mensaje)).when(servicio).eliminarInvitacion(6);

        app.perform(post("/api/app/admin/usuarios/6/eliminar").principal(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath(CLAVE_APP).value(mensaje));
        panel.perform(post("/admin/usuarios-app/6/eliminar"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath(CLAVE_PANEL).value(mensaje));
    }
}
