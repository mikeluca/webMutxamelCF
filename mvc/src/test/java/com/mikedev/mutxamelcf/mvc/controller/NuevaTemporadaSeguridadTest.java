package com.mikedev.mutxamelcf.mvc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.ResumenCierreDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.Fila;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.GrupoCategoria;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.model.ResultadoPreguntaContinuidadDTO;
import com.mikedev.mutxamelcf.service.ContinuidadService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

/**
 * Seguridad real (SecurityConfig) del asistente "Nueva temporada": solo el rol SUPER. ADMIN y ENTRENADOR reciben
 * 403, también para las acciones que cambian la plantilla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NuevaTemporadaSeguridadTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private NuevaTemporadaService nuevaTemporadaService;

    @MockBean
    private EquipoService equipoService;

    @MockBean
    private ContinuidadService continuidadService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminNoVeElAsistente() throws Exception {
        mockMvc.perform(get("/admin/nueva-temporada").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void entrenadorNoVeElAsistente() throws Exception {
        mockMvc.perform(get("/admin/nueva-temporada").with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminNoPuedeCerrarNiTocarElBorrador() throws Exception {
        mockMvc.perform(post("/admin/nueva-temporada/cerrar").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("temporadaId", "2").param("confirmacion", "2027-28"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/nueva-temporada/continuidad").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("temporadaId", "2").param("jugadorId", "1").param("continuidad", "SIGUE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/nueva-temporada/prueba/anadir").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("temporadaId", "2").param("nombre", "Luis").param("apellidos", "Gil")
                        .param("anioNacimiento", "2016"))
                .andExpect(status().isForbidden());

        verify(nuevaTemporadaService, never()).cerrar(anyLong(), any());
        verify(nuevaTemporadaService, never()).marcarContinuidad(any(), any(), any());
        verify(nuevaTemporadaService, never()).anadirPrueba(any(), any(), any(), any());
    }

    @Test
    void superVeElAsistente() throws Exception {
        mockMvc.perform(get("/admin/nueva-temporada").with(user("super").roles("SUPER")))
                .andExpect(status().isOk());
    }

    @Test
    void laPaginaPintaElBorradorConCategoriasContinuidadYPruebas() throws Exception {
        Temporada destino = new Temporada();
        destino.setId(2L);
        destino.setNombre("2027-28");
        when(nuevaTemporadaService.temporadasDestino()).thenReturn(List.of(destino));

        Fila formativo = new Fila(1L, "Pablo Gil", 2016, 10L, "Alevín A", null, null, "Alevín", null, false, true,
                "Sin año de nacimiento", false, false, "CONFIRMADO", "DUDA", false);
        Fila prueba = new Fila(2L, "Luis Prueba", 2016, null, null, null, null, "Alevín", null, false, false, null,
                false, false, "PRUEBA", "SIGUE", false);
        Fila adulto = new Fila(3L, "Ana Primer", 1990, 11L, "Primer Equipo", 11L, "Primer Equipo", "Primer Equipo", 9,
                false, false, null, false, false, "CONFIRMADO", "SIGUE", true);
        when(nuevaTemporadaService.revision(2L)).thenReturn(new RevisionTemporadaDTO(destino,
                List.of(new RevisionTemporadaDTO.GrupoEquipo(11L, "Primer Equipo", 1, List.of(adulto))),
                List.of(new GrupoCategoria("Alevín", 2, List.of(formativo, prueba))), List.of(formativo), List.of(),
                List.of(), List.of(), List.of(), 3));
        when(nuevaTemporadaService.resumenCierre(2L)).thenReturn(new ResumenCierreDTO("2027-28", "2026-27",
                Map.of("Primer Equipo", 1, "Alevín (Pretemporada)", 2), 0, 0,
                new ResumenCierreDTO.Pasos(5, 3, 12, List.of("Alevín (Pretemporada)"), List.of(), 0, 1, 1, 1, 7, 4, 4, 2, 6,
                        9, 120),
                List.of("1 fila(s) pendiente(s) de revisar (incluye la continuidad sin resolver).")));

        mockMvc.perform(get("/admin/nueva-temporada").param("temporadaId", "2").with(user("super").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alevín (sin equipo)")))
                .andExpect(content().string(containsString("Cerrar temporada")))
                .andExpect(content().string(containsString("Alevín (Pretemporada)")))
                .andExpect(content().string(containsString("Los equipos se publicarán en septiembre")))
                .andExpect(content().string(containsString("Alta de un jugador de prueba")))
                .andExpect(content().string(containsString("continuidad(this, 'NO_SIGUE')")))
                .andExpect(content().string(containsString("Categoría: Alevín")))
                .andExpect(content().string(containsString("text-bg-warning")))
                .andExpect(content().string(containsString("Prueba")));
    }

    @Test
    void superPuedeCerrarMarcarContinuidadYAnadirPruebas() throws Exception {
        mockMvc.perform(post("/admin/nueva-temporada/cerrar").with(csrf()).with(user("super").roles("SUPER"))
                        .param("temporadaId", "2").param("confirmacion", "2027-28"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").exists());
        mockMvc.perform(post("/admin/nueva-temporada/continuidad").with(csrf()).with(user("super").roles("SUPER"))
                        .param("temporadaId", "2").param("jugadorId", "1").param("continuidad", "NO_SIGUE"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/nueva-temporada/prueba/anadir").with(csrf()).with(user("super").roles("SUPER"))
                        .param("temporadaId", "2").param("nombre", "Luis").param("apellidos", "Gil")
                        .param("anioNacimiento", "2016"))
                .andExpect(status().isOk());

        verify(nuevaTemporadaService).cerrar(2L, "2027-28");
        verify(nuevaTemporadaService).marcarContinuidad(2L, 1L, "NO_SIGUE");
        verify(nuevaTemporadaService).anadirPrueba(2L, "Luis", "Gil", 2016);
    }

    @Test
    void sinTokenCsrfSeRechazaAunSiendoSuper() throws Exception {
        mockMvc.perform(post("/admin/nueva-temporada/cerrar").with(user("super").roles("SUPER"))
                        .param("temporadaId", "2").param("confirmacion", "2027-28"))
                .andExpect(status().isForbidden());

        verify(nuevaTemporadaService, never()).cerrar(anyLong(), any());
    }

    @Test
    void pedirLaConfirmacionDeContinuidadEsSoloDeSuper() throws Exception {
        when(continuidadService.pedirConfirmacion(2L))
                .thenReturn(new ResultadoPreguntaContinuidadDTO(2L, "2027-28", 3, 2, 1));

        mockMvc.perform(post("/admin/nueva-temporada/pedir-continuidad").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("temporadaId", "2"))
                .andExpect(status().isForbidden());
        verify(continuidadService, never()).pedirConfirmacion(any());

        mockMvc.perform(post("/admin/nueva-temporada/pedir-continuidad").with(csrf()).with(user("super").roles("SUPER"))
                        .param("temporadaId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").exists());
    }
}
