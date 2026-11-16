package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.ClubRivalFila;
import com.mikedev.mutxamelcf.model.ResumenGeneracionClubes;
import com.mikedev.mutxamelcf.service.ClubRivalService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.PartidoService;

/** Escudo público (sin sesión, con caché larga) y pantalla "Escudos de rivales" con la seguridad real. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EscudoRivalControllerTest {

    private static final String BASE = "/admin/escudos-rivales";

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private ClubRivalService clubRivalService;

    @MockBean
    private EquipoService equipoService;

    @MockBean
    private PartidoService partidoService;

    @Autowired
    private MockMvc mockMvc;

    private static ClubRivalFila fila(long id, String nombre, boolean escudo) {
        return new ClubRivalFila(id, nombre, 1111, escudo, escudo ? "/api/public/escudos-rivales/" + id + "?v=1" : null,
                List.of("Primera FFCV, Grup 4"), List.of("Primer Equipo"), 3, List.of("ELX CF"));
    }

    private static MockMultipartFile imagen() {
        return new MockMultipartFile("archivo", "escudo.png", "image/png", new byte[] { 1, 2, 3 });
    }

    // ---- API pública ---------------------------------------------------------------------------

    @Test
    void elEscudoPublicoSeDaSinSesionComoPngConCacheLargaEInmutable() throws Exception {
        byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
        when(clubRivalService.obtenerEscudo(7L)).thenReturn(png);

        mockMvc.perform(get("/api/public/escudos-rivales/7").param("v", "3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(content().bytes(png));
    }

    @Test
    void sinEscudoOSinClubElEscudoPublicoDa404() throws Exception {
        when(clubRivalService.obtenerEscudo(8L)).thenReturn(null);

        mockMvc.perform(get("/api/public/escudos-rivales/8")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/escudos-rivales/999")).andExpect(status().isNotFound());
    }

    // ---- contrato JSON de la app ---------------------------------------------------------------

    @Test
    void laPortadaYLosListadosDevuelvenEscudoRivalUrlYRivalEsPropioConEsosNombres() throws Exception {
        com.mikedev.mutxamelcf.model.ResultadoDTO con = new com.mikedev.mutxamelcf.model.ResultadoDTO();
        con.setRival("Pego C.F.");
        con.setEscudoRivalUrl("/api/public/escudos-rivales/7?v=3");
        com.mikedev.mutxamelcf.model.ResultadoDTO propio = new com.mikedev.mutxamelcf.model.ResultadoDTO();
        propio.setRival("Mutxamel C.F. B");
        propio.setRivalEsPropio(true);

        when(partidoService.obtenerResultadoPrimerEquipo(any())).thenReturn(con);
        when(partidoService.obtenerResultados("F")).thenReturn(List.of(con, propio));

        mockMvc.perform(get("/api/public/resultados/primer-equipo"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.escudoRivalUrl").value("/api/public/escudos-rivales/7?v=3"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.rivalEsPropio").value(false));

        mockMvc.perform(get("/api/public/resultados"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[1].rivalEsPropio").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[1].escudoRivalUrl").doesNotExist());
    }

    // ---- seguridad de la pantalla --------------------------------------------------------------

    @Test
    void entrenadorRecibe403EnTodasLasRutasYNadaSeEjecuta() throws Exception {
        var entrenador = user("entrenador").roles("ENTRENADOR");

        mockMvc.perform(get(BASE).with(entrenador)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/generar").with(csrf()).with(entrenador)).andExpect(status().isForbidden());
        mockMvc.perform(multipart(BASE + "/5/escudo").file(imagen()).with(csrf()).with(entrenador))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/5/borrar").with(csrf()).with(entrenador)).andExpect(status().isForbidden());

        verify(clubRivalService, never()).generar();
        verify(clubRivalService, never()).subirEscudo(any(), any());
        verify(clubRivalService, never()).borrar(any());
    }

    @Test
    void sinSesionSeRedirigeAlLoginYSinCsrfSeRechazaAunSiendoAdmin() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().is3xxRedirection());

        mockMvc.perform(post(BASE + "/generar").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(clubRivalService, never()).generar();
    }

    // ---- pantalla ----------------------------------------------------------------------------------

    @Test
    void laPantallaMuestraElContadorYPorDefectoSoloLosClubesSinEscudo() throws Exception {
        when(clubRivalService.listar()).thenReturn(List.of(fila(1, "Elx C.F.", true), fila(2, "Villena C.F.", false)));
        when(clubRivalService.contarConEscudo()).thenReturn(new int[] { 1, 2 });

        mockMvc.perform(get(BASE).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escudos de rivales")))
                .andExpect(content().string(containsString("de <strong>2</strong> clubes con escudo")))
                .andExpect(content().string(containsString("Villena C.F.")))
                // El que ya tiene escudo solo sale en la lista de unir (datalist), no como fila.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-club=\"1\""))));

        mockMvc.perform(get(BASE).param("filtro", "todos").with(user("super").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-club=\"1\"")))
                .andExpect(content().string(containsString("data-club=\"2\"")))
                .andExpect(content().string(containsString("/api/public/escudos-rivales/1?v=1")));
    }

    @Test
    void elBuscadorFiltraPorNombreSinAcentosNiMayusculasYPorAlias() throws Exception {
        when(clubRivalService.listar()).thenReturn(List.of(fila(1, "Elx C.F.", false), fila(2, "Villena C.F.", false)));
        when(clubRivalService.contarConEscudo()).thenReturn(new int[] { 0, 2 });

        mockMvc.perform(get(BASE).param("q", "vilLENA").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("data-club=\"2\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-club=\"1\""))));

        // Por alias (ELX CF).
        mockMvc.perform(get(BASE).param("q", "elx cf").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("data-club=\"1\"")));
    }

    @Test
    void generarClubesMuestraElResumen() throws Exception {
        when(clubRivalService.generar()).thenReturn(new ResumenGeneracionClubes(12, 15, 40));

        mockMvc.perform(post(BASE + "/generar").with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(BASE))
                .andExpect(flash().attribute("resumen", new ResumenGeneracionClubes(12, 15, 40)));
    }

    @Test
    void subirEscudoLoPasaAlServicioYUnErrorSeMuestraClaro() throws Exception {
        mockMvc.perform(multipart(BASE + "/5/escudo").file(imagen()).with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl(BASE))
                .andExpect(flash().attribute("mensaje", "Escudo guardado"));
        verify(clubRivalService).subirEscudo(eq(5L), eq(new byte[] { 1, 2, 3 }));

        org.mockito.Mockito.doThrow(new IllegalArgumentException("El archivo no es una imagen válida (usa PNG o JPG)"))
                .when(clubRivalService).subirEscudo(eq(6L), any());

        mockMvc.perform(multipart(BASE + "/6/escudo").file(imagen()).with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("error", "El archivo no es una imagen válida (usa PNG o JPG)"));
    }

    @Test
    void sinArchivoYMasDe2MbSeRechazanSinLlegarAlServicio() throws Exception {
        mockMvc.perform(multipart(BASE + "/5/escudo")
                        .file(new MockMultipartFile("archivo", "vacio.png", "image/png", new byte[0]))
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("error", "Selecciona la imagen del escudo (PNG o JPG)"));

        mockMvc.perform(multipart(BASE + "/5/escudo")
                        .file(new MockMultipartFile("archivo", "grande.png", "image/png",
                                new byte[2 * 1024 * 1024 + 1]))
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("error", "El escudo pesa más de 2 MB"));

        verify(clubRivalService, never()).subirEscudo(any(), any());
    }

    @Test
    void quitarRenombrarUnirYBorrarVanAlServicio() throws Exception {
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post(BASE + "/5/quitar-escudo").with(csrf()).with(admin))
                .andExpect(flash().attribute("mensaje", "Escudo quitado"));
        verify(clubRivalService).quitarEscudo(5L);

        mockMvc.perform(post(BASE + "/5/renombrar").param("nombre", "C.F. Pego").with(csrf()).with(admin))
                .andExpect(flash().attribute("mensaje", "Club renombrado"));
        verify(clubRivalService).renombrar(5L, "C.F. Pego");

        // El destino llega como "Nombre · #id" (lo que ofrece la lista) o, escrito a mano, por su nombre.
        mockMvc.perform(post(BASE + "/5/unir").param("destino", "Elx C.F. · #9").with(csrf()).with(admin))
                .andExpect(flash().attribute("mensaje", "Clubes unidos: el club de origen se ha borrado"));
        verify(clubRivalService).unir(5L, 9L);

        when(clubRivalService.listar()).thenReturn(List.of(fila(11, "Altea", false)));
        mockMvc.perform(post(BASE + "/5/unir").param("destino", "altea").with(csrf()).with(admin));
        verify(clubRivalService).unir(5L, 11L);

        mockMvc.perform(post(BASE + "/5/unir").param("destino", "No existe").with(csrf()).with(admin))
                .andExpect(flash().attribute("error", "Elige el club destino de la lista"));

        mockMvc.perform(post(BASE + "/5/borrar").with(csrf()).with(admin))
                .andExpect(flash().attribute("mensaje", "Club borrado"));
        verify(clubRivalService).borrar(5L);
    }

    @Test
    void unirDosClubesConCodigosDistintosMuestraElMotivo() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("No se pueden unir dos clubes con códigos de la FFCV distintos"))
                .when(clubRivalService).unir(5L, 9L);

        mockMvc.perform(post(BASE + "/5/unir").param("destino", "X · #9").with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("error", "No se pueden unir dos clubes con códigos de la FFCV distintos"));
    }
}
