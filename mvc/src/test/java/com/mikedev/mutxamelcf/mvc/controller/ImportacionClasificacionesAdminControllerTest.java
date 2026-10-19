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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.ClasificacionFilaPublicDTO;
import com.mikedev.mutxamelcf.model.ClasificacionPublicDTO;
import com.mikedev.mutxamelcf.model.EquipoClasificacionPrevia;
import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;
import com.mikedev.mutxamelcf.model.ResultadoImportacionClasificaciones;
import com.mikedev.mutxamelcf.service.ClasificacionService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.ImportacionClasificacionesService;
import com.mikedev.mutxamelcf.service.PartidoService;

/**
 * Seguridad real (SecurityConfig), flujo de sesión del importador de
 * clasificaciones y API pública de la clasificación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportacionClasificacionesAdminControllerTest {

    private static final String BASE = "/admin/ffcv/clasificaciones";

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private ImportacionClasificacionesService importacionService;

    @MockBean
    private ClasificacionService clasificacionService;

    @MockBean
    private EquipoService equipoService;

    @MockBean
    private PartidoService partidoService;

    @Autowired
    private MockMvc mockMvc;

    private static MockMultipartFile xlsx(String nombre) {
        return new MockMultipartFile("archivo", nombre,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "datos".getBytes());
    }

    private static PrevisualizacionClasificaciones previa() {
        EquipoClasificacionPrevia equipo = new EquipoClasificacionPrevia(
                1L, "Primer Equipo", true, null, "Primera FFCV, Grup 4", 3, null, 2, 6, 3,
                false, true, true, true, List.of("Primera importación"), List.of());
        return new PrevisualizacionClasificaciones(9, 4, List.of(equipo), List.of(), List.of());
    }

    private static MockHttpSession sesionConPrevia() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(ImportacionClasificacionesAdminController.SESION_PREVIA, previa());
        return sesion;
    }

    // ---- seguridad -------------------------------------------------------------------

    @Test
    void entrenadorRecibe403EnTodasLasRutas() throws Exception {
        mockMvc.perform(get(BASE).with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart(BASE + "/previsualizar").file(xlsx("ListadoClasificaciones.xlsx"))
                        .with(csrf()).with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/confirmar").param("equipoId", "1")
                        .with(csrf()).with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());

        verify(importacionService, never()).previsualizar(any());
        verify(importacionService, never()).confirmar(any(), any(), any());
    }

    @Test
    void sinTokenCsrfSeRechazaAunSiendoAdmin() throws Exception {
        mockMvc.perform(multipart(BASE + "/previsualizar").file(xlsx("ListadoClasificaciones.xlsx"))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/confirmar").param("equipoId", "1").with(user("super").roles("SUPER")))
                .andExpect(status().isForbidden());

        verify(importacionService, never()).previsualizar(any());
        verify(importacionService, never()).confirmar(any(), any(), any());
    }

    // ---- flujo -----------------------------------------------------------------------

    @Test
    void adminVeLaPantallaYLaVistaPreviaGuardadaEnSesion() throws Exception {
        mockMvc.perform(get(BASE).session(sesionConPrevia()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Importar clasificaciones FFCV")))
                .andExpect(content().string(containsString("Primer Equipo")))
                .andExpect(content().string(containsString("Primera FFCV, Grup 4")));
    }

    @Test
    void previsualizarGuardaLaVistaPreviaEnSesionYRedirige() throws Exception {
        when(importacionService.previsualizar(any())).thenReturn(previa());

        mockMvc.perform(multipart(BASE + "/previsualizar").file(xlsx("ListadoClasificaciones.xlsx"))
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(BASE))
                .andExpect(request().sessionAttribute(
                        ImportacionClasificacionesAdminController.SESION_PREVIA, previa()));
    }

    @Test
    void rechazaArchivosQueNoSonXlsx() throws Exception {
        mockMvc.perform(multipart(BASE + "/previsualizar").file(xlsx("clasificaciones.csv"))
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString(".xlsx")));

        verify(importacionService, never()).previsualizar(any());
    }

    @Test
    void siFaltaUnaCabeceraElErrorLlegaALaPantalla() throws Exception {
        when(importacionService.previsualizar(any()))
                .thenThrow(new IllegalArgumentException("Faltan columnas en el archivo: Puntos"));

        mockMvc.perform(multipart(BASE + "/previsualizar").file(xlsx("ListadoClasificaciones.xlsx"))
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Faltan columnas en el archivo: Puntos"));
    }

    @Test
    void confirmarUsaLaVistaPreviaDeLaSesionYLaLimpia() throws Exception {
        when(importacionService.confirmar(eq("admin"), any(), eq(List.of(1L))))
                .thenReturn(new ResultadoImportacionClasificaciones(1, 4, List.of("Primer Equipo"), List.of()));

        MockHttpSession sesion = sesionConPrevia();

        mockMvc.perform(post(BASE + "/confirmar").param("equipoId", "1").session(sesion)
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(BASE))
                .andExpect(flash().attributeExists("resultado"));

        verify(importacionService).confirmar(eq("admin"), eq(previa()), eq(List.of(1L)));

        org.junit.jupiter.api.Assertions.assertNull(
                sesion.getAttribute(ImportacionClasificacionesAdminController.SESION_PREVIA));
    }

    @Test
    void confirmarSinVistaPreviaEnSesionNoImportaNada() throws Exception {
        mockMvc.perform(post(BASE + "/confirmar").param("equipoId", "1")
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        verify(importacionService, never()).confirmar(any(), any(), any());
    }

    @Test
    void elCalendarioEnlazaConElImportadorDeClasificaciones() throws Exception {
        when(equipoService.obtenerTodos()).thenReturn(List.of());

        mockMvc.perform(get("/admin/calendario-resultados").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(BASE)));
    }

    // ---- API pública -----------------------------------------------------------------

    @Test
    void laApiPublicaDevuelveLaTablaOrdenada() throws Exception {
        when(clasificacionService.obtenerPublicaPorEquipo(1L)).thenReturn(new ClasificacionPublicDTO(
                "Primera FFCV, Grup 4", 3, LocalDateTime.of(2026, 10, 19, 10, 30),
                List.of(
                        new ClasificacionFilaPublicDTO(1, "Monnegre", false, 9, 3, 3, 0, 0, 8, 1),
                        new ClasificacionFilaPublicDTO(2, "Mutxamel C.F.", true, 6, 3, 2, 0, 1, 5, 3))));

        mockMvc.perform(get("/api/public/equipos/1/clasificacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competicion").value("Primera FFCV, Grup 4"))
                .andExpect(jsonPath("$.jornada").value(3))
                .andExpect(jsonPath("$.fechaImportacion").value("2026-10-19T10:30:00"))
                .andExpect(jsonPath("$.clasificacion[0].posicion").value(1))
                .andExpect(jsonPath("$.clasificacion[0].esNuestro").value(false))
                .andExpect(jsonPath("$.clasificacion[1].equipo").value("Mutxamel C.F."))
                .andExpect(jsonPath("$.clasificacion[1].esNuestro").value(true))
                .andExpect(jsonPath("$.clasificacion[1].gf").value(5));
    }

    @Test
    void laApiPublicaDa404SiNoHayClasificacion() throws Exception {
        when(clasificacionService.obtenerPublicaPorEquipo(2L)).thenReturn(null);

        mockMvc.perform(get("/api/public/equipos/2/clasificacion"))
                .andExpect(status().isNotFound());
    }

}
