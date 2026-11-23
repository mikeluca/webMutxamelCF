package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.mikedev.mutxamelcf.model.CompeticionVeteranosPrevia;
import com.mikedev.mutxamelcf.model.FilaClasificacionVeteranos;
import com.mikedev.mutxamelcf.model.PrevisualizacionVeteranos;
import com.mikedev.mutxamelcf.model.ResultadoImportacionVeteranos;
import com.mikedev.mutxamelcf.service.ImportacionClasificacionesService;
import com.mikedev.mutxamelcf.service.ImportacionVeteranosService;

/**
 * La importación de la clasificación de veteranos (LVCA) en el panel: seguridad real (SUPER y ADMIN, como la importación
 * de la FFCV), flujo con la vista previa en la sesión y el bloque aparte de la pantalla de clasificaciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportacionVeteranosAdminControllerTest {

    private static final String BASE = "/admin/ffcv/veteranos";
    private static final String PANTALLA = "/admin/ffcv/clasificaciones";

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private ImportacionVeteranosService importacionService;

    @MockBean
    private ImportacionClasificacionesService importacionClasificaciones;

    @Autowired
    private MockMvc mockMvc;

    private static MockMultipartFile html(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "text/html", contenido);
    }

    private static PrevisualizacionVeteranos previa() {
        return new PrevisualizacionVeteranos(9L, "Veteranos", List.of(
                new CompeticionVeteranosPrevia("Liga Veteranos Ciudad de Alicante", 5, 2, List.of(
                        new FilaClasificacionVeteranos(1, "UA VETERANOS", 5, 4, 1, 0, 10, 3, 7, 13, false),
                        new FilaClasificacionVeteranos(2, "MUTXAMEL C.F.V", 5, 3, 1, 1, 8, 5, 3, 10, true)),
                        List.of("UA VETERANOS"))),
                List.of("UA VETERANOS"));
    }

    private static MockHttpSession sesionConPrevia() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(ImportacionVeteranosAdminController.SESION_PREVIA, previa());
        return sesion;
    }

    // ---- seguridad -------------------------------------------------------------------------------------------------

    @Test
    void quienNoEsSuperNiAdminRecibe403EnTodasLasRutas() throws Exception {

        for (String rol : List.of("ENTRENADOR", "OFICINA", "USER")) {

            mockMvc.perform(multipart(BASE + "/previsualizar").file(html("score.html", "x".getBytes())).with(csrf())
                    .with(user("u").roles(rol))).andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/confirmar").with(csrf()).with(user("u").roles(rol)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/cancelar").with(csrf()).with(user("u").roles(rol)))
                    .andExpect(status().isForbidden());
        }

        verify(importacionService, never()).previsualizar(any());
        verify(importacionService, never()).confirmar(anyString(), any());
    }

    @Test
    void sinTokenCsrfSeRechazaAunSiendoAdmin() throws Exception {

        mockMvc.perform(multipart(BASE + "/previsualizar").file(html("score.html", "x".getBytes()))
                .with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());

        verify(importacionService, never()).previsualizar(any());
    }

    // ---- flujo -----------------------------------------------------------------------------------------------------

    @Test
    void superYAdminPrevisualizanYLaVistaPreviaQuedaEnLaSesion() throws Exception {

        byte[] contenido = "<html>".getBytes();
        when(importacionService.previsualizar(contenido)).thenReturn(previa());

        for (String rol : List.of("SUPER", "ADMIN")) {

            MockHttpSession sesion = new MockHttpSession();

            mockMvc.perform(multipart(BASE + "/previsualizar").file(html("score.html", contenido)).session(sesion)
                    .with(csrf()).with(user("u").roles(rol))).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(PANTALLA));

            org.assertj.core.api.Assertions.assertThat(sesion.getAttribute(ImportacionVeteranosAdminController.SESION_PREVIA))
                    .isEqualTo(previa());
        }
    }

    @Test
    void laPantallaEnseñaElBloqueDeVeteranosConLaTablaYLosClubesQueSeCrean() throws Exception {

        mockMvc.perform(get(PANTALLA).session(sesionConPrevia()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clasificación de veteranos (LVCA)")))
                .andExpect(content().string(containsString("Liga Veteranos Ciudad de Alicante")))
                .andExpect(content().string(containsString("MUTXAMEL C.F.V")))
                .andExpect(content().string(containsString("table-primary")))
                .andExpect(content().string(containsString("Clubes que se crearán")))
                .andExpect(content().string(containsString("/admin/ffcv/veteranos/confirmar")));

        // Sin vista previa, solo el selector de fichero.
        mockMvc.perform(get(PANTALLA).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Clasificación de veteranos (LVCA)")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Clubes que se crearán"))));
    }

    @Test
    void ficheroNoValidoVuelveConSuMensaje() throws Exception {

        mockMvc.perform(multipart(BASE + "/previsualizar").file(html("score.html", new byte[0])).with(csrf())
                .with(user("admin").roles("ADMIN"))).andExpect(redirectedUrl(PANTALLA))
                .andExpect(flash().attribute("errorVeteranos", containsString("Selecciona la página guardada")));

        mockMvc.perform(multipart(BASE + "/previsualizar").file(html("datos.csv", "x".getBytes())).with(csrf())
                .with(user("admin").roles("ADMIN"))).andExpect(redirectedUrl(PANTALLA))
                .andExpect(flash().attribute("errorVeteranos", containsString(".html")));

        when(importacionService.previsualizar(any())).thenThrow(new IllegalArgumentException(
                "No parece la clasificación de la Liga Veteranos Ciudad de Alicante"));

        mockMvc.perform(multipart(BASE + "/previsualizar").file(html("otra.html", "basura".getBytes())).with(csrf())
                .with(user("admin").roles("ADMIN"))).andExpect(redirectedUrl(PANTALLA))
                .andExpect(flash().attribute("errorVeteranos",
                        "No parece la clasificación de la Liga Veteranos Ciudad de Alicante"));
    }

    @Test
    void confirmarGuardaLaVistaPreviaDeLaSesionUnaSolaVez() throws Exception {

        when(importacionService.confirmar(anyString(), any())).thenReturn(new ResultadoImportacionVeteranos(1, 2,
                List.of("Liga Veteranos Ciudad de Alicante"), List.of("UA VETERANOS")));

        MockHttpSession sesion = sesionConPrevia();

        mockMvc.perform(post(BASE + "/confirmar").session(sesion).with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl(PANTALLA)).andExpect(flash().attributeExists("resultadoVeteranos"));

        verify(importacionService).confirmar("admin", previa());
        org.assertj.core.api.Assertions.assertThat(sesion.getAttribute(ImportacionVeteranosAdminController.SESION_PREVIA))
                .isNull();

        // Sin vista previa (ya importada o caducada) no se confirma nada.
        mockMvc.perform(post(BASE + "/confirmar").session(sesion).with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("errorVeteranos",
                        "La vista previa ha caducado: vuelve a subir el archivo"));
    }

    @Test
    void cancelarQuitaLaVistaPrevia() throws Exception {

        MockHttpSession sesion = sesionConPrevia();

        mockMvc.perform(post(BASE + "/cancelar").session(sesion).with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl(PANTALLA)).andExpect(request().sessionAttributeDoesNotExist(
                        ImportacionVeteranosAdminController.SESION_PREVIA));
    }
}
