package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.service.EquipoService;

/** La página del panel de Equipos con la plantilla real: el grupo P de los equipos de pretemporada. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipoAdminPaginaTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private EquipoService equipoService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elDesplegableDeGrupoTieneLaOpcionPretemporada() throws Exception {
        EquipoDTO pretemporada = new EquipoDTO(40L, "Alevín", "P", "D", "Alevín (Pretemporada)", "F");
        pretemporada.setActivoTemporada(true);
        pretemporada.setTipoTemporada("PRETEMPORADA");
        when(equipoService.obtenerTodosConEstadoTemporada()).thenReturn(List.of(pretemporada));
        when(equipoService.obtenerCategorias()).thenReturn(List.of("Alevín"));

        mockMvc.perform(get("/admin/equipos").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<option value=\"P\">Pretemporada</option>")))
                // Al editar un equipo se le añade su grupo actual a la lista si no estuviera, y no se sustituye por otro
                .andExpect(content().string(containsString("function asegurarOpcionDeGrupo(grupo)")))
                .andExpect(content().string(containsString("asegurarOpcionDeGrupo(data.grupo);")))
                // El nombre de un equipo de pretemporada no se recompone como "Alevín P"
                .andExpect(content().string(containsString("grupo === 'P' ? categoria + ' (Pretemporada)'")))
                // Y el botón de editar lleva el grupo del equipo
                .andExpect(content().string(containsString("data-grupo=\"P\"")));
    }
}
