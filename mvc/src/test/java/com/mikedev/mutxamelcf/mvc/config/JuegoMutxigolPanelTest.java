package com.mikedev.mutxamelcf.mvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.dao.JuegoDao.PerfilAdmin;
import com.mikedev.mutxamelcf.dao.JuegoDao.Puntuacion;
import com.mikedev.mutxamelcf.service.JuegoConflictoException;
import com.mikedev.mutxamelcf.service.JuegoService;

/** La pantalla "Juego Mutxigol" de Administración de la App: roles, listado, acciones y menú. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JuegoMutxigolPanelTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 24, 18, 3, 12);

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private JuegoService juegoService;

    @Autowired
    private MockMvc mockMvc;

    private static PerfilAdmin perfil(long id, String alias, boolean oculto) {
        return new PerfilAdmin(id, alias, "AZUL", oculto, "Ana Pérez", "ana@example.com", FECHA, 4, 12, 9, 83_450);
    }

    private String pagina(String url, String rol) throws Exception {
        return mockMvc.perform(get(url).with(user("panel").roles(rol))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Roles: los mismos que el resto de "Administración de la App" (SUPER y ADMIN)
    // ------------------------------------------------------------------

    @Test
    void superYAdminEntranYEntrenadorOSinSesionNo() throws Exception {
        when(juegoService.listarPerfilesAdmin(any())).thenReturn(List.of());

        pagina("/admin/juego-mutxigol", "SUPER");
        pagina("/admin/juego-mutxigol", "ADMIN");

        mockMvc.perform(get("/admin/juego-mutxigol").with(user("e").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/juego-mutxigol").with(user("o").roles("OTRO"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/juego-mutxigol")).andExpect(status().is3xxRedirection());
    }

    @Test
    void lasAccionesTampocoLasPuedeHacerUnEntrenadorNiSinCsrf() throws Exception {
        mockMvc.perform(post("/admin/juego-mutxigol/7/alias").param("alias", "Nuevo")
                .with(user("e").roles("ENTRENADOR")).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/juego-mutxigol/7/oculto").param("oculto", "true")
                .with(user("e").roles("ENTRENADOR")).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/juego-mutxigol/puntuaciones/11/borrar").param("usuario", "7")
                .with(user("e").roles("ENTRENADOR")).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/juego-mutxigol/7/puntuaciones/borrar-todas").with(user("e").roles("ENTRENADOR"))
                .with(csrf())).andExpect(status().isForbidden());
        // Sin token CSRF la web rechaza el POST aunque sea ADMIN
        mockMvc.perform(post("/admin/juego-mutxigol/7/oculto").param("oculto", "true").with(user("a").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(juegoService, never()).cambiarAliasAdmin(anyLong(), any());
        verify(juegoService, never()).ocultarAdmin(anyLong(), anyBoolean());
        verify(juegoService, never()).borrarPuntuacionAdmin(anyLong());
        verify(juegoService, never()).borrarPuntuacionesAdmin(anyLong());
    }

    // ------------------------------------------------------------------
    // La lista
    // ------------------------------------------------------------------

    @Test
    void laListaEnseñaAliasCuentaMejoresMarcasYEstado() throws Exception {
        when(juegoService.listarPerfilesAdmin(any())).thenReturn(List.of(perfil(7, "Mutxigol10", false),
                perfil(8, "Oculto1", true)));

        String html = pagina("/admin/juego-mutxigol", "ADMIN");

        assertThat(html).contains("Juego Mutxigol").contains("Mutxigol10").contains("Oculto1").contains("Ana Pérez")
                .contains("ana@example.com").contains("Ocultar del ranking").contains("Mostrar en el ranking")
                .contains("Oculto").contains("Visible");
        // Borrar todas pide confirmación
        assertThat(html).contains("TODAS las puntuaciones");
    }

@Test
void laListaTieneLasColumnasDeLosTresJuegosYNadaDeLosAntiguos() throws Exception {
    when(juegoService.listarPerfilesAdmin(any())).thenReturn(List.of(perfil(7, "Mutxigol10", false)));

    String html = pagina("/admin/juego-mutxigol", "ADMIN");

    assertThat(html).contains(">Atrapa<").contains(">Cabezazos<").contains("Parejas (m:ss,d)")
            .doesNotContain("Penaltis").doesNotContain("Toques").doesNotContain("Regatea");
    // Atrapa 12, Cabezazos 9 y el tiempo de Parejas (83,45 s) como m:ss,d
    assertThat(html).contains(">12<").contains(">9<").contains("1:23,4");
}

@Test
void sinPartidasDeParejasNoSeEnseñaTiempo() throws Exception {
    when(juegoService.listarPerfilesAdmin(any())).thenReturn(List.of(new PerfilAdmin(7, "Nuevo", "AZUL", false,
            "Ana Pérez", "ana@example.com", FECHA, 0, 0, 0, 0)));

    String html = pagina("/admin/juego-mutxigol", "ADMIN");

    assertThat(html).contains("Nuevo").contains("—").doesNotContain("0:00,0");
}

@Test
void laBusquedaPorAliasLlegaAlServicio() throws Exception {
        when(juegoService.listarPerfilesAdmin("mutxi")).thenReturn(List.of(perfil(7, "Mutxigol10", false)));

        String html = pagina("/admin/juego-mutxigol?q=mutxi", "SUPER");

        assertThat(html).contains("Mutxigol10").contains("value=\"mutxi\"");
        verify(juegoService).listarPerfilesAdmin("mutxi");
    }

    @Test
    void conUnPerfilSeleccionadoSalenSusPuntuacionesConBotonDeBorrar() throws Exception {
        when(juegoService.listarPerfilesAdmin(any())).thenReturn(List.of(perfil(7, "Mutxigol10", false)));
        when(juegoService.obtenerPerfilAdmin(7)).thenReturn(perfil(7, "Mutxigol10", false));
        when(juegoService.puntuacionesAdmin(7))
                .thenReturn(List.of(new Puntuacion(11, "ATRAPA", 12, 40_000, FECHA), new Puntuacion(12, "PAREJAS", 8,
                        65_300, FECHA.minusDays(1))));

        String html = pagina("/admin/juego-mutxigol?usuario=7", "ADMIN");

        assertThat(html).contains("1:05,3"); // el tiempo de Parejas, no los 8 puntos
        assertThat(html).contains("Últimas puntuaciones de").contains("ATRAPA").contains("PAREJAS").contains("24/11/2026 18:03:12")
                .contains("/admin/juego-mutxigol/puntuaciones/11/borrar")
                .contains("/admin/juego-mutxigol/puntuaciones/12/borrar").contains("¿Borrar esta puntuación?");
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    @Test
    void cambiarElAliasOcultarYBorrarRedirigenALaListaConMensaje() throws Exception {
        mockMvc.perform(post("/admin/juego-mutxigol/7/alias").param("alias", "Nuevo").param("q", "viejo")
                .with(user("a").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/juego-mutxigol?q=viejo&usuario=7"))
                .andExpect(flash().attribute("mensaje", "Alias cambiado."));
        verify(juegoService).cambiarAliasAdmin(7, "Nuevo");

        mockMvc.perform(post("/admin/juego-mutxigol/7/oculto").param("oculto", "true").with(user("a").roles("SUPER"))
                .with(csrf())).andExpect(redirectedUrl("/admin/juego-mutxigol?usuario=7"))
                .andExpect(flash().attribute("mensaje", "Perfil oculto del ranking."));
        verify(juegoService).ocultarAdmin(7, true);

        mockMvc.perform(post("/admin/juego-mutxigol/7/oculto").param("oculto", "false").with(user("a").roles("SUPER"))
                .with(csrf())).andExpect(flash().attribute("mensaje", "Perfil visible otra vez en el ranking."));
        verify(juegoService).ocultarAdmin(7, false);

        mockMvc.perform(post("/admin/juego-mutxigol/puntuaciones/11/borrar").param("usuario", "7")
                .with(user("a").roles("ADMIN")).with(csrf())).andExpect(redirectedUrl("/admin/juego-mutxigol?usuario=7"))
                .andExpect(flash().attribute("mensaje", "Puntuación borrada."));
        verify(juegoService).borrarPuntuacionAdmin(11);

        mockMvc.perform(post("/admin/juego-mutxigol/7/puntuaciones/borrar-todas").with(user("a").roles("ADMIN"))
                .with(csrf())).andExpect(flash().attribute("mensaje", "Puntuaciones borradas."));
        verify(juegoService).borrarPuntuacionesAdmin(7);
    }

    @Test
    void siElAliasNoValeOEstaEnUsoSeEnseñaElMotivo() throws Exception {
        doThrow(new JuegoConflictoException("ALIAS_EN_USO", "Ese alias ya está en uso")).when(juegoService)
                .cambiarAliasAdmin(7, "Ocupado");
        doThrow(new IllegalArgumentException("Ese alias no está permitido, elige otro")).when(juegoService)
                .cambiarAliasAdmin(7, "mal");
        doThrow(new NoSuchElementException("Esa cuenta no tiene perfil en el juego")).when(juegoService)
                .borrarPuntuacionesAdmin(99);

        mockMvc.perform(post("/admin/juego-mutxigol/7/alias").param("alias", "Ocupado").with(user("a").roles("ADMIN"))
                .with(csrf())).andExpect(flash().attribute("error", "Ese alias ya está en uso"));
        mockMvc.perform(post("/admin/juego-mutxigol/7/alias").param("alias", "mal").with(user("a").roles("ADMIN"))
                .with(csrf())).andExpect(flash().attribute("error", "Ese alias no está permitido, elige otro"));
        mockMvc.perform(post("/admin/juego-mutxigol/99/puntuaciones/borrar-todas").with(user("a").roles("ADMIN"))
                .with(csrf())).andExpect(flash().attribute("error", "Esa cuenta no tiene perfil en el juego"));
    }

    // ------------------------------------------------------------------
    // El menú "Administración de la App"
    // ------------------------------------------------------------------

    @Test
    void elMenuAdministracionDeLaAppTieneJuegoMutxigolParaSuperYAdmin() throws Exception {
        when(juegoService.listarPerfilesAdmin(any())).thenReturn(List.of());

        for (String rol : List.of("SUPER", "ADMIN")) {
            String html = pagina("/admin/juego-mutxigol", rol);
            int inicio = html.indexOf("navbarDropdownApp");
            int fin = html.indexOf("</ul>", inicio);

            assertThat(inicio).isPositive();
            assertThat(html.substring(inicio, fin)).as(rol).contains("href=\"/admin/juego-mutxigol\"")
                    .contains("Juego Mutxigol");
        }
    }
}
