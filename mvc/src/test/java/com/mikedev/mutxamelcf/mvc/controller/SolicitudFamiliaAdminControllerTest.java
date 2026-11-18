package com.mikedev.mutxamelcf.mvc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Coincidencia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.HijoRevision;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Revision;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Revisor;

class SolicitudFamiliaAdminControllerTest {

    private static final org.springframework.security.core.Authentication ADMIN = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "admin", null, List.of());

    private SolicitudFamiliaService servicio;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        servicio = mock(SolicitudFamiliaService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SolicitudFamiliaAdminController(servicio)).build();
    }

    private static Revision revision() {

        Coincidencia c = new Coincidencia(55L, "Luis Pérez Gómez", 8L, "Alevín B", true);
        HijoRevision h = new HijoRevision(31L, "Luis", "Pérez", "Alevín B", "PENDIENTE", null, List.of(c));

        return new Revision(12L, "Ana", "Pérez Gil", "ana@example.com", null, "MADRE", "PENDIENTE", null,
                LocalDateTime.of(2026, 11, 18, 10, 0), null, List.of(h));
    }

    @Test
    void laPantallaLlevaLasPendientesYElHistoricoComoPanel() throws Exception {

        when(servicio.pendientes(any())).thenReturn(List.of(revision()));
        when(servicio.historico(any())).thenReturn(List.of());

        mvc.perform(get("/admin/solicitudes-familia").principal(ADMIN)).andExpect(status().isOk())
                .andExpect(view().name("admin/solicitudes-familia")).andExpect(model().attributeExists("pendientes"))
                .andExpect(model().attributeExists("historico"));

        verify(servicio).pendientes(Revisor.panel("admin"));
        verify(servicio).historico(Revisor.panel("admin"));
    }

    @Test
    void aprobarPasaElJugadorElegidoYRedirigeConMensaje() throws Exception {

        mvc.perform(post("/admin/solicitudes-familia/hijos/31/aprobar").param("jugadorId", "55")
                .principal(ADMIN)).andExpect(redirectedUrl("/admin/solicitudes-familia"))
                .andExpect(flash().attributeExists("mensaje"));

        verify(servicio).aprobarHijo(Revisor.panel("admin"), 31, 55);
    }

    @Test
    void aprobarSinJugadorNoLlamaAlServicioYAvisa() throws Exception {

        mvc.perform(post("/admin/solicitudes-familia/hijos/31/aprobar").principal(ADMIN))
                .andExpect(redirectedUrl("/admin/solicitudes-familia")).andExpect(flash().attributeExists("error"));

        verify(servicio, never()).aprobarHijo(any(), anyLong(), anyLong());
    }

    @Test
    void aprobarMuestraElMensajeDeUnConflicto() throws Exception {

        when(servicio.aprobarHijo(any(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("La solicitud ya no está pendiente de revisar"));

        mvc.perform(post("/admin/solicitudes-familia/hijos/31/aprobar").param("jugadorId", "55")
                .principal(ADMIN)).andExpect(flash().attribute("error",
                        "La solicitud ya no está pendiente de revisar"));
    }

    @Test
    void rechazarGuardaElMotivoOpcional() throws Exception {

        mvc.perform(post("/admin/solicitudes-familia/hijos/31/rechazar").param("motivo", "No es de nuestro club")
                .principal(ADMIN)).andExpect(redirectedUrl("/admin/solicitudes-familia"))
                .andExpect(flash().attributeExists("mensaje"));
        mvc.perform(post("/admin/solicitudes-familia/hijos/32/rechazar").principal(ADMIN))
                .andExpect(flash().attributeExists("mensaje"));

        verify(servicio).rechazarHijo(Revisor.panel("admin"), 31, "No es de nuestro club");
        verify(servicio).rechazarHijo(Revisor.panel("admin"), 32, null);
    }

    @Test
    void laBusquedaDeJugadoresDevuelveJson() throws Exception {

        when(servicio.buscarJugadores(any(), any()))
                .thenReturn(List.of(new Coincidencia(55L, "Luis Pérez Gómez", 8L, "Alevín B", false)));

        mvc.perform(get("/admin/solicitudes-familia/jugadores").param("q", "luis").principal(ADMIN))
                .andExpect(status().isOk()).andExpect(content().json(
                        "[{\"jugadorId\":55,\"nombre\":\"Luis Pérez Gómez\",\"equipo\":\"Alevín B\"}]"));

        verify(servicio).buscarJugadores(Revisor.panel("admin"), "luis");
    }
}
