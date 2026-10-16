package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.EquipoFfcvDTO;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.service.EquipoFfcvService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.ImportacionFfcvService;
import com.mikedev.mutxamelcf.service.PartidoService;

/**
 * Seguridad real (SecurityConfig) de las rutas del importador FFCV: solo
 * SUPER y ADMIN, nunca ENTRENADOR, y con CSRF obligatorio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportacionFfcvSeguridadTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private ImportacionFfcvService importacionFfcvService;

    @MockBean
    private EquipoFfcvService equipoFfcvService;

    @MockBean
    private EquipoService equipoService;

    @MockBean
    private PartidoService partidoService;

    @Autowired
    private MockMvc mockMvc;

    private static final String JSON_CONFIRMAR = """
            {"previsualizacion":{"desde":null,"hasta":null,"filas":[],"descansan":[],"desaparecidos":[],"errores":[]},
             "asignaciones":[]}
            """;

    private static MockMultipartFile csv() {
        return new MockMultipartFile("archivo", "LstPartidos.csv", "text/csv", "datos".getBytes());
    }

    // ---- ENTRENADOR: 403 -------------------------------------------------------

    @Test
    void entrenadorRecibe403AlPrevisualizar() throws Exception {
        mockMvc.perform(multipart("/admin/ffcv/previsualizar").file(csv())
                        .with(csrf()).with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());

        verify(importacionFfcvService, never()).previsualizar(any());
    }

    @Test
    void entrenadorRecibe403AlConfirmar() throws Exception {
        mockMvc.perform(post("/admin/ffcv/confirmar").with(csrf()).with(user("entrenador").roles("ENTRENADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CONFIRMAR))
                .andExpect(status().isForbidden());

        verify(importacionFfcvService, never()).confirmar(any(), any(), any());
    }

    @Test
    void entrenadorRecibe403EnLasRelacionesEquipoFfcv() throws Exception {
        mockMvc.perform(get("/admin/equipos-ffcv").with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/equipos-ffcv/crear").with(csrf()).with(user("entrenador").roles("ENTRENADOR"))
                        .param("competicion", "X").param("equipoId", "1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/equipos-ffcv/1/eliminar").with(csrf()).with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());

        verify(equipoFfcvService, never()).crear(any(), any(), any());
        verify(equipoFfcvService, never()).eliminar(any());
    }

    // ---- CSRF obligatorio ----------------------------------------------------------

    @Test
    void sinTokenCsrfSeRechazaPrevisualizarAunSiendoAdmin() throws Exception {
        mockMvc.perform(multipart("/admin/ffcv/previsualizar").file(csv()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(importacionFfcvService, never()).previsualizar(any());
    }

    @Test
    void sinTokenCsrfSeRechazaConfirmarAunSiendoSuper() throws Exception {
        mockMvc.perform(post("/admin/ffcv/confirmar").with(user("super").roles("SUPER"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CONFIRMAR))
                .andExpect(status().isForbidden());

        verify(importacionFfcvService, never()).confirmar(any(), any(), any());
    }

    @Test
    void sinTokenCsrfSeRechazanLasEscriturasDeRelaciones() throws Exception {
        mockMvc.perform(post("/admin/equipos-ffcv/crear").with(user("admin").roles("ADMIN"))
                        .param("competicion", "X").param("equipoId", "1"))
                .andExpect(status().isForbidden());

        verify(equipoFfcvService, never()).crear(any(), any(), any());
    }

    // ---- SUPER y ADMIN: permitido ----------------------------------------------------

    @Test
    void adminPuedeSubirYPrevisualizarConCsrf() throws Exception {
        when(importacionFfcvService.previsualizar(any()))
                .thenReturn(new PrevisualizacionFfcv(null, null, List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(multipart("/admin/ffcv/previsualizar").file(csv())
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas").isArray());
    }

    @Test
    void superPuedeConfirmarConCsrfEnviadoComoCabeceraXCsrfToken() throws Exception {
        when(importacionFfcvService.confirmar(any(), any(), any()))
                .thenReturn(new com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv(0, 0, 0, 0, 0, 0, 0, List.of()));

        mockMvc.perform(post("/admin/ffcv/confirmar").with(csrf().asHeader()).with(user("super").roles("SUPER"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CONFIRMAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(0));
    }

    @Test
    void sinSesionNoSePuedePrevisualizar() throws Exception {
        mockMvc.perform(multipart("/admin/ffcv/previsualizar").file(csv()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(importacionFfcvService, never()).previsualizar(any());
    }

    @Test
    void adminVeLaPantallaDeRelacionesEquipoFfcv() throws Exception {
        when(equipoFfcvService.listar())
                .thenReturn(List.of(new EquipoFfcvDTO(1L, 2L, "Filial", "Segona FFCV, Grup 7", "B")));
        EquipoDTO filial = new EquipoDTO();
        filial.setId(2L);
        filial.setNombre("Filial");
        when(equipoService.obtenerTodos()).thenReturn(List.of(filial));

        mockMvc.perform(get("/admin/equipos-ffcv").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Segona FFCV, Grup 7")))
                .andExpect(content().string(containsString("form=\"rel-1\"")));
    }

    // ---- botón en calendario-resultados ------------------------------------------------

    private void prepararCalendario() {
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(2L);
        equipo.setNombre("Filial");
        equipo.setDeporte("F");
        when(equipoService.obtenerTodos()).thenReturn(List.of(equipo));
        when(partidoService.obtenerPorEquipo(2L)).thenReturn(List.of());
    }

    @Test
    void adminVeElBotonImportarPartidosFfcv() throws Exception {
        prepararCalendario();

        mockMvc.perform(get("/admin/calendario-resultados").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Importar partidos FFCV")));
    }

    @Test
    void superVeElBotonImportarPartidosFfcv() throws Exception {
        prepararCalendario();

        mockMvc.perform(get("/admin/calendario-resultados").with(user("super").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Importar partidos FFCV")));
    }

    @Test
    void entrenadorNoVeElBotonImportarPartidosFfcv() throws Exception {
        prepararCalendario();

        mockMvc.perform(get("/admin/calendario-resultados").with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Importar partidos FFCV"))));
    }

}
