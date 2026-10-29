package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.MorosidadDTO;
import com.mikedev.mutxamelcf.model.MorosidadDTO.Fila;
import com.mikedev.mutxamelcf.model.MorosidadDTO.TotalTemporada;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.MorosidadService;

/** Morosidad con la seguridad real: mismos roles que Pagos (solo SUPER). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MorosidadControllerTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private MorosidadService morosidadService;

    @Autowired
    private MockMvc mockMvc;

    private static MorosidadDTO morosidad() {
        Fila pendiente = new Fila(1L, 1L, "2025/2026", 10L, "Ana Pérez", "Alevín A", "Cuota mensual", "2025-10",
                new BigDecimal("30"), new BigDecimal("12"), new BigDecimal("18"), "PARCIAL", "PARCIAL");
        Fila incobrable = new Fila(2L, 1L, "2025/2026", 11L, "Biel Gil", null, "Matrícula", null,
                new BigDecimal("60"), BigDecimal.ZERO, new BigDecimal("60"), "INCOBRABLE", "INCOBRABLE");
        Fila cobrada = new Fila(3L, 1L, "2025/2026", 12L, "Carla Sanz", "Alevín B", "Cuota mensual", "2025-11",
                new BigDecimal("30"), new BigDecimal("30"), BigDecimal.ZERO, "PAGADO", "Cobrada");

        return new MorosidadDTO(List.of(pendiente, incobrable, cobrada),
                List.of(new TotalTemporada(1L, "2025/2026", 3, new BigDecimal("120"), new BigDecimal("42"),
                        new BigDecimal("78"), new BigDecimal("60"))));
    }

    private void conDatos() {
        TemporadaDTO t = new TemporadaDTO();
        t.setId(1L);
        t.setNombre("2025/2026");
        t.setFechaInicio(Date.valueOf("2025-07-01"));
        when(morosidadService.temporadasAnteriores()).thenReturn(List.of(t));
        when(morosidadService.obtener(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(morosidad());
    }

    @Test
    void soloSuperEntraEnMorosidad() throws Exception {
        conDatos();

        mockMvc.perform(get("/admin/pagos/morosidad").with(user("s").roles("SUPER"))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/pagos/morosidad").with(user("a").roles("ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/pagos/morosidad").with(user("e").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/pagos/morosidad/exportar").with(user("a").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/pagos/morosidad/cobrar").with(csrf()).with(user("a").roles("ADMIN"))
                .param("cuotaId", "1")).andExpect(status().isForbidden());

        verify(morosidadService, never()).cobrar(any());
    }

    @Test
    void laPaginaPintaLasColumnasLosTotalesYLasAcciones() throws Exception {
        conDatos();

        mockMvc.perform(get("/admin/pagos/morosidad").with(user("s").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Pérez")))
                .andExpect(content().string(containsString("Alevín A")))
                .andExpect(content().string(containsString("Cuota mensual")))
                .andExpect(content().string(containsString("2025-10")))
                .andExpect(content().string(containsString("18,00 €")))
                .andExpect(content().string(containsString("PARCIAL")))
                .andExpect(content().string(containsString("INCOBRABLE")))
                .andExpect(content().string(containsString("Cobrada")))
                .andExpect(content().string(containsString("Marcar cobrada")))
                .andExpect(content().string(containsString("Deshacer incobrable")))
                .andExpect(content().string(containsString("Totales por temporada")))
                .andExpect(content().string(containsString("78,00 €")))
                .andExpect(content().string(containsString("Exportar CSV")));
    }

    @Test
    void sinCuotasDiceQueNoHayNadaPendiente() throws Exception {
        when(morosidadService.temporadasAnteriores()).thenReturn(List.of());
        when(morosidadService.obtener(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new MorosidadDTO(List.of(), List.of()));

        mockMvc.perform(get("/admin/pagos/morosidad").with(user("s").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No hay cuotas pendientes de temporadas anteriores.")));
    }

    @Test
    void unaTemporadaNoValidaMuestraElErrorSinRomperLaPagina() throws Exception {
        when(morosidadService.temporadasAnteriores()).thenReturn(List.of());
        when(morosidadService.obtener(eq(99L), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalArgumentException("La temporada no es una temporada anterior a la activa."));

        mockMvc.perform(get("/admin/pagos/morosidad").param("temporadaId", "99").with(user("s").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La temporada no es una temporada anterior a la activa.")));
    }

    @Test
    void cobrarMarcarYDeshacerRedirigenConsevandoElFiltro() throws Exception {
        mockMvc.perform(post("/admin/pagos/morosidad/cobrar").with(csrf()).with(user("s").roles("SUPER"))
                .param("cuotaId", "1").param("temporadaId", "1").param("cobradas", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pagos/morosidad?temporadaId=1&cobradas=true"))
                .andExpect(flash().attributeExists("mensaje"));
        mockMvc.perform(post("/admin/pagos/morosidad/incobrable").with(csrf()).with(user("s").roles("SUPER"))
                .param("cuotaId", "2")).andExpect(redirectedUrl("/admin/pagos/morosidad"))
                .andExpect(flash().attributeExists("mensaje"));
        mockMvc.perform(post("/admin/pagos/morosidad/deshacer-incobrable").with(csrf()).with(user("s").roles("SUPER"))
                .param("cuotaId", "2").param("temporadaId", "1")).andExpect(redirectedUrl("/admin/pagos/morosidad?temporadaId=1"));

        verify(morosidadService).cobrar(1L);
        verify(morosidadService).marcarIncobrable(2L);
        verify(morosidadService).deshacerIncobrable(2L);
    }

    @Test
    void unaAccionRechazadaVuelveConSuMotivo() throws Exception {
        doThrow(new IllegalArgumentException("La cuota ya está cobrada.")).when(morosidadService).cobrar(1L);

        mockMvc.perform(post("/admin/pagos/morosidad/cobrar").with(csrf()).with(user("s").roles("SUPER"))
                .param("cuotaId", "1")).andExpect(redirectedUrl("/admin/pagos/morosidad"))
                .andExpect(flash().attribute("error", "La cuota ya está cobrada."));
    }

    @Test
    void exportaElCsvConBomYNombreDeFichero() throws Exception {
        when(morosidadService.exportarCsv(1L, false))
                .thenReturn("Temporada;Jugador\n2025/2026;Ana Pérez\n");

        byte[] cuerpo = mockMvc.perform(get("/admin/pagos/morosidad/exportar").param("temporadaId", "1")
                .with(user("s").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("morosidad-1.csv")))
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsByteArray();

        String texto = new String(cuerpo, java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(texto).startsWith("﻿Temporada;Jugador")
                .contains("Ana Pérez");
    }

    @Test
    void exportarUnaTemporadaNoValidaEsUn400() throws Exception {
        when(morosidadService.exportarCsv(eq(99L), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalArgumentException("no es anterior"));

        mockMvc.perform(get("/admin/pagos/morosidad/exportar").param("temporadaId", "99")
                .with(user("s").roles("SUPER"))).andExpect(status().isBadRequest());
    }
}
