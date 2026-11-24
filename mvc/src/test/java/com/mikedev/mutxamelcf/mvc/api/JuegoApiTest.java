package com.mikedev.mutxamelcf.mvc.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.dao.JuegoDao;
import com.mikedev.mutxamelcf.service.JuegoConflictoException;
import com.mikedev.mutxamelcf.service.JuegoLimiteEnviosException;
import com.mikedev.mutxamelcf.service.JuegoService;
import com.mikedev.mutxamelcf.service.JuegoService.FilaRanking;
import com.mikedev.mutxamelcf.service.JuegoService.MarcaConPosicion;
import com.mikedev.mutxamelcf.service.JuegoService.MarcasJuego;
import com.mikedev.mutxamelcf.service.JuegoService.PerfilJuego;
import com.mikedev.mutxamelcf.service.JuegoService.ResultadoPuntuacion;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Las rutas del juego de Mutxigol con la configuración real de seguridad: el ranking es público, el perfil y las
 * puntuaciones piden sesión (cualquier rol) y /admin/juego solo ADMIN_APP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JuegoApiTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 24, 18, 3, 12);

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private JuegoService juegoService;

    @MockBean
    private UsuarioAppService usuarioAppService;

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------------
    // Ranking público
    // ------------------------------------------------------------------

    @Test
    void elRankingEsPublicoYSoloLlevaAliasPuntosYFecha() throws Exception {
        when(juegoService.ranking("PENALTIS", "SEMANA")).thenReturn(List.of(new FilaRanking(1, "Mutxigol10", 14, FECHA),
                new FilaRanking(2, "Pepe-Gol", 12, FECHA.minusDays(1))));

        mockMvc.perform(get("/api/public/juego/ranking").param("juego", "PENALTIS").param("periodo", "SEMANA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].posicion").value(1)).andExpect(jsonPath("$[0].alias").value("Mutxigol10"))
                .andExpect(jsonPath("$[0].puntos").value(14)).andExpect(jsonPath("$[0].fecha").value("2026-11-24T18:03:12"))
                // Nada del nombre real ni de la cuenta
                .andExpect(jsonPath("$[0].usuarioId").doesNotExist()).andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].nombre").doesNotExist());
    }

    @Test
    void elRankingConUnJuegoOPeriodoInvalidoEs400() throws Exception {
        when(juegoService.ranking("AJEDREZ", null)).thenThrow(new IllegalArgumentException("El juego no vale"));

        mockMvc.perform(get("/api/public/juego/ranking").param("juego", "AJEDREZ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El juego no vale"));
    }

    // ------------------------------------------------------------------
    // Con sesión
    // ------------------------------------------------------------------

    @Test
    void elPerfilYLasPuntuacionesSinSesionSon401() throws Exception {
        mockMvc.perform(get("/api/app/juego/perfil")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/app/juego/perfil").contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/app/juego/puntuacion").contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(csrf())).andExpect(status().isUnauthorized());

        verify(juegoService, never()).perfil(anyInt());
    }

    @Test
    void cualquierRolDeLaAppPuedeJugar() throws Exception {
        when(juegoService.perfil(anyInt())).thenReturn(new PerfilJuego("Ana", "AZUL", false, Map.of()));

        for (String rol : List.of("JUGADOR", "FAMILIAR", "ENTRENADOR", "COORDINADOR", "RETRANSMISION", "RRSS",
                "ADMIN_APP")) {
            mockMvc.perform(get("/api/app/juego/perfil").with(user("5").roles(rol))).andExpect(status().isOk());
        }
    }

    @Test
    void elPerfilDevuelveAliasEquipacionYMarcasConPosicion() throws Exception {
        when(juegoService.perfil(5)).thenReturn(new PerfilJuego("Ana", "ROSA", false,
                Map.of("PENALTIS", new MarcasJuego(new MarcaConPosicion(8, FECHA, 3), null))));

        mockMvc.perform(get("/api/app/juego/perfil").with(user("5").roles("JUGADOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("Ana")).andExpect(jsonPath("$.equipacion").value("ROSA"))
                .andExpect(jsonPath("$.juegos.PENALTIS.semana.puntos").value(8))
                .andExpect(jsonPath("$.juegos.PENALTIS.semana.posicion").value(3))
                .andExpect(jsonPath("$.juegos.PENALTIS.historico").doesNotExist());
    }

    @Test
    void cambiarAliasYEquipacionLlegaAlServicioConElIdDeLaSesion() throws Exception {
        when(juegoService.actualizarPerfil(5, "Mutxi", "ROSA")).thenReturn(new PerfilJuego("Mutxi", "ROSA", false, Map.of()));

        mockMvc.perform(put("/api/app/juego/perfil").contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"Mutxi\",\"equipacion\":\"ROSA\"}").with(user("5").roles("JUGADOR")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alias").value("Mutxi"));

        verify(juegoService).actualizarPerfil(5, "Mutxi", "ROSA");
    }

    @Test
    void unAliasEnUsoEs409ConCodigo() throws Exception {
        when(juegoService.actualizarPerfil(eq(5), anyString(), any()))
                .thenThrow(new JuegoConflictoException("ALIAS_EN_USO", "Ese alias ya está en uso"));

        mockMvc.perform(put("/api/app/juego/perfil").contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"Beto\"}").with(user("5").roles("JUGADOR")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Ese alias ya está en uso"))
                .andExpect(jsonPath("$.codigo").value("ALIAS_EN_USO"));
    }

    @Test
    void unAliasNoValidoEs400() throws Exception {
        when(juegoService.actualizarPerfil(eq(5), anyString(), any()))
                .thenThrow(new IllegalArgumentException("El alias no puede parecer un email"));

        mockMvc.perform(put("/api/app/juego/perfil").contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"a@b.es\"}").with(user("5").roles("JUGADOR")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("El alias no puede parecer un email"));
    }

    @Test
    void guardarUnaPuntuacionDevuelve201ConRecordsYPosiciones() throws Exception {
        when(juegoService.guardarPuntuacion(5, "PENALTIS", 12, 40_000))
                .thenReturn(new ResultadoPuntuacion("PENALTIS", 12, true, false, 3, 7));

        mockMvc.perform(post("/api/app/juego/puntuacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"juego\":\"PENALTIS\",\"puntos\":12,\"duracionMs\":40000}")
                .with(user("5").roles("JUGADOR")).with(csrf())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordSemana").value(true)).andExpect(jsonPath("$.recordHistorico").value(false))
                .andExpect(jsonPath("$.posicionSemana").value(3)).andExpect(jsonPath("$.posicionHistorico").value(7));
    }

    @Test
    void sinAliasEs409ConCodigoAliasRequerido() throws Exception {
        when(juegoService.guardarPuntuacion(eq(5), anyString(), any(), any())).thenThrow(
                new JuegoConflictoException("ALIAS_REQUERIDO", "Elige un alias para guardar tu puntuación"));

        mockMvc.perform(post("/api/app/juego/puntuacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"juego\":\"TOQUES\",\"puntos\":5,\"duracionMs\":20000}").with(user("5").roles("JUGADOR"))
                .with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ALIAS_REQUERIDO"));
    }

    @Test
    void unaPuntuacionImplausibleEs400() throws Exception {
        when(juegoService.guardarPuntuacion(eq(5), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException("La puntuación no es válida para esa duración"));

        mockMvc.perform(post("/api/app/juego/puntuacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"juego\":\"TOQUES\",\"puntos\":999,\"duracionMs\":3000}").with(user("5").roles("JUGADOR"))
                .with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    void pasarseDelLimiteDeEnviosEs429() throws Exception {
        when(juegoService.guardarPuntuacion(eq(5), anyString(), any(), any()))
                .thenThrow(new JuegoLimiteEnviosException("Vas muy rápido"));

        mockMvc.perform(post("/api/app/juego/puntuacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"juego\":\"TOQUES\",\"puntos\":5,\"duracionMs\":20000}").with(user("5").roles("JUGADOR"))
                .with(csrf())).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("Vas muy rápido"));
    }

    // ------------------------------------------------------------------
    // Moderación desde la app: solo ADMIN_APP
    // ------------------------------------------------------------------

    private static JuegoDao.PerfilAdmin perfilAdmin() {
        return new JuegoDao.PerfilAdmin(7, "Ana", "AZUL", false, "Ana Pérez", "ana@example.com", FECHA, 3, 4, 5, 6);
    }

    @Test
    void laModeracionSoloLaHaceUnAdminApp() throws Exception {
        doThrow(new SecurityException("Solo un administrador puede moderar el juego")).when(usuarioAppService)
                .exigirAdminApp(eq(5), anyString());

        mockMvc.perform(get("/api/app/admin/juego/perfiles").with(user("5").roles("COORDINADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/app/admin/juego/perfiles/7/puntuaciones").with(user("5").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/app/admin/juego/perfiles/7/alias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"Nuevo\"}").with(user("5").roles("JUGADOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/app/admin/juego/perfiles/7/oculto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oculto\":true}").with(user("5").roles("JUGADOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/app/admin/juego/puntuaciones/11").with(user("5").roles("COORDINADOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/app/admin/juego/perfiles/7/puntuaciones").with(user("5").roles("COORDINADOR"))
                .with(csrf())).andExpect(status().isForbidden());

        verify(juegoService, never()).listarPerfilesAdmin(any());
        verify(juegoService, never()).cambiarAliasAdmin(anyLong(), any());
        verify(juegoService, never()).ocultarAdmin(anyLong(), anyBoolean());
        verify(juegoService, never()).borrarPuntuacionAdmin(anyLong());
        verify(juegoService, never()).borrarPuntuacionesAdmin(anyLong());
    }

    @Test
    void sinSesionLaModeracionEs401() throws Exception {
        mockMvc.perform(get("/api/app/admin/juego/perfiles")).andExpect(status().isUnauthorized());
    }

    @Test
    void unAdminAppListaConElFiltroDeAliasYVeLaCuenta() throws Exception {
        when(juegoService.listarPerfilesAdmin("an")).thenReturn(List.of(perfilAdmin()));

        mockMvc.perform(get("/api/app/admin/juego/perfiles").param("alias", "an").with(user("5").roles("ADMIN_APP")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].alias").value("Ana"))
                .andExpect(jsonPath("$[0].nombre").value("Ana Pérez")).andExpect(jsonPath("$[0].email").value("ana@example.com"))
                .andExpect(jsonPath("$[0].mejorPenaltis").value(4));

        verify(usuarioAppService).exigirAdminApp(eq(5), anyString());
    }

    @Test
    void unAdminAppCambiaElAliasOcultaYBorra() throws Exception {
        when(juegoService.cambiarAliasAdmin(7, "Nuevo")).thenReturn(perfilAdmin());
        when(juegoService.ocultarAdmin(7, true)).thenReturn(perfilAdmin());
        when(juegoService.borrarPuntuacionesAdmin(7)).thenReturn(3);

        mockMvc.perform(put("/api/app/admin/juego/perfiles/7/alias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"Nuevo\"}").with(user("5").roles("ADMIN_APP")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/app/admin/juego/perfiles/7/oculto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oculto\":true}").with(user("5").roles("ADMIN_APP")).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/app/admin/juego/puntuaciones/11").with(user("5").roles("ADMIN_APP")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/app/admin/juego/perfiles/7/puntuaciones").with(user("5").roles("ADMIN_APP"))
                .with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.borradas").value(3));

        verify(juegoService).borrarPuntuacionAdmin(11);
    }

    @Test
    void ocultarSinIndicarSiSeOcultaEs400YUnPerfilInexistenteEs404() throws Exception {
        when(juegoService.puntuacionesAdmin(99)).thenThrow(new NoSuchElementException("Esa cuenta no tiene perfil"));
        when(juegoService.cambiarAliasAdmin(7, "Ocupado"))
                .thenThrow(new JuegoConflictoException("ALIAS_EN_USO", "Ese alias ya está en uso"));

        mockMvc.perform(put("/api/app/admin/juego/perfiles/7/oculto").contentType(MediaType.APPLICATION_JSON)
                .content("{}").with(user("5").roles("ADMIN_APP")).with(csrf())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/app/admin/juego/perfiles/99/puntuaciones").with(user("5").roles("ADMIN_APP")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/app/admin/juego/perfiles/7/alias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"Ocupado\"}").with(user("5").roles("ADMIN_APP")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ALIAS_EN_USO"));
    }
}
