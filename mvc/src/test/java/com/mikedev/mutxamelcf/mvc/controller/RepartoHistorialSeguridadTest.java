package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.ClasificacionFilaPublicDTO;
import com.mikedev.mutxamelcf.model.EquipoRepartoDTO;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO;
import com.mikedev.mutxamelcf.model.JugadorRepartoDTO;
import com.mikedev.mutxamelcf.model.PartidosEquipo;
import com.mikedev.mutxamelcf.model.PruebasSinConfirmarException;
import com.mikedev.mutxamelcf.model.RepartoCategoriaDTO;
import com.mikedev.mutxamelcf.model.ResultadoDevolucionDTO;
import com.mikedev.mutxamelcf.model.ResultadoRepartoDTO;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.HistorialTemporadaService;
import com.mikedev.mutxamelcf.service.RepartoService;

/**
 * Seguridad real (SecurityConfig) del reparto y del historial del panel: solo SUPER y ADMIN; el ENTRENADOR recibe 403.
 * Además comprueba que las dos páginas se pintan con sus datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepartoHistorialSeguridadTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private RepartoService repartoService;

    @MockBean
    private HistorialTemporadaService historialService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elEntrenadorNoEntraNiEnElRepartoNiEnElHistorial() throws Exception {
        mockMvc.perform(get("/admin/reparto").with(user("e").roles("ENTRENADOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/historial").with(user("e").roles("ENTRENADOR"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/reparto/equipos").with(csrf()).with(user("e").roles("ENTRENADOR"))
                .param("categoria", "Alevín")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/reparto/asignar").with(csrf()).with(user("e").roles("ENTRENADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"equipoId\":1,\"jugadores\":[]}"))
                .andExpect(status().isForbidden());

        verify(repartoService, never()).asignar(any());
        verify(repartoService, never()).crearEquipo(any());
    }

    @Test
    void superYAdminEntranEnElRepartoYEnElHistorial() throws Exception {
        for (String rol : List.of("SUPER", "ADMIN")) {
            mockMvc.perform(get("/admin/reparto").with(user("u").roles(rol))).andExpect(status().isOk());
            mockMvc.perform(get("/admin/historial").with(user("u").roles(rol))).andExpect(status().isOk());
        }
    }

    @Test
    void elRepartoPintaPorAsignarYEquiposDeLaCategoria() throws Exception {
        JugadorRepartoDTO ana = new JugadorRepartoDTO(1L, "Ana Pérez", 2015, null, "CONFIRMADO", false);
        JugadorRepartoDTO biel = new JugadorRepartoDTO(2L, "Biel Gil", 2015, null, "PRUEBA", false);
        JugadorRepartoDTO eva = new JugadorRepartoDTO(3L, "Eva Ruiz", 2014, 9, "CONFIRMADO", true);
        when(repartoService.categorias()).thenReturn(List.of("Alevín"));
        when(repartoService.obtenerCategoria("Alevín")).thenReturn(new RepartoCategoriaDTO("Alevín",
                new EquipoRepartoDTO(10L, "Alevín (Pretemporada)", "P", "F8", true, List.of()), List.of(ana, biel),
                List.of(new EquipoRepartoDTO(11L, "Alevín A", "A", "F8", false, List.of(eva)))));

        mockMvc.perform(get("/admin/reparto").param("categoria", "Alevín").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Pérez")))
                .andExpect(content().string(containsString("Prueba")))
                .andExpect(content().string(containsString("Alevín (Pretemporada)")))
                .andExpect(content().string(containsString("Alevín A")))
                .andExpect(content().string(containsString("Eva Ruiz")))
                .andExpect(content().string(containsString("Nuevo equipo")));
    }

    @Test
    void asignarDevuelveElResultadoYLaPruebaSinConfirmarUn409() throws Exception {
        when(repartoService.asignar(any())).thenReturn(new ResultadoRepartoDTO(1, List.of("El dorsal 7 está repetido"),
                true, false, List.of()));

        mockMvc.perform(post("/admin/reparto/asignar").with(csrf()).with(user("a").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipoId\":11,\"jugadores\":[{\"jugadorId\":1,\"dorsal\":7}],\"confirmarPruebas\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avisos[0]").value("El dorsal 7 está repetido"));

        when(repartoService.asignar(any())).thenThrow(new PruebasSinConfirmarException(List.of("Biel Gil")));

        mockMvc.perform(post("/admin/reparto/asignar").with(csrf()).with(user("a").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipoId\":11,\"jugadores\":[{\"jugadorId\":2,\"dorsal\":null}],\"confirmarPruebas\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.requiereConfirmacion").value(true));
    }

    @Test
    void elHistorialPintaPlantillasStaffClasificacionYEstadisticas() throws Exception {
        Temporada pasada = new Temporada(1L, "2025/2026", new Date(), new Date(), 0);
        when(historialService.temporadas()).thenReturn(List.of(pasada));
        when(historialService.obtener(1L, 5L)).thenReturn(new HistorialTemporadaDTO(pasada,
                List.of(new HistorialTemporadaDTO.PlantillaEquipo("Cadete A", List.of(
                        new HistorialTemporadaDTO.JugadorPlantilla(1L, "Pau Sanz", 4, "CONFIRMADO", 2010)))),
                List.of(new HistorialTemporadaDTO.StaffEquipo("Cadete A",
                        List.of(new HistorialTemporadaDTO.PersonaStaff("Luis Mora", "Entrenador")))),
                List.of(new HistorialTemporadaDTO.TablaClasificacion(5L, "Cadete A", "Liga", 30, List.of(
                        new ClasificacionFilaPublicDTO(1, "Mutxamel CF", true, 70, 30, 22, 4, 4, 80, 20)))),
                List.of(new HistorialTemporadaDTO.EquipoOpcion(5L, "Cadete A")),
                new HistorialTemporadaDTO.EstadisticasEquipo(5L, "Cadete A", new PartidosEquipo(30, 22, 4, 4, 80, 20),
                        50, 48, List.of(new HistorialTemporadaDTO.JugadorEstadisticas(1L, "Pau Sanz", 4, 28, 12, 3, 2, 0,
                                50, 45)))));

        mockMvc.perform(get("/admin/historial").param("temporadaId", "1").param("equipoId", "5")
                .with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2025/2026")))
                .andExpect(content().string(containsString("Pau Sanz")))
                .andExpect(content().string(containsString("Luis Mora")))
                .andExpect(content().string(containsString("Mutxamel CF")))
                .andExpect(content().string(containsString("45/50")));
    }

    @Test
    void unaTemporadaInexistenteMuestraElErrorSinRomperLaPagina() throws Exception {
        when(historialService.temporadas()).thenReturn(List.of());
        when(historialService.obtener(99L, null)).thenThrow(new IllegalArgumentException("La temporada no existe."));

        mockMvc.perform(get("/admin/historial").param("temporadaId", "99").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("La temporada no existe.")));
    }

    @Test
    void devolverAPretemporadaSoloLoHaceSuperOAdmin() throws Exception {
        when(repartoService.devolverAPretemporada(any())).thenReturn(new ResultadoDevolucionDTO(1, true, java.util.List.of()));

        mockMvc.perform(post("/admin/reparto/devolver").with(csrf()).with(user("e").roles("ENTRENADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"jugadorIds\":[1]}"))
                .andExpect(status().isForbidden());
        verify(repartoService, never()).devolverAPretemporada(any());

        mockMvc.perform(post("/admin/reparto/devolver").with(csrf()).with(user("a").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"jugadorIds\":[1]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(containsString("1 jugador(es) devuelto(s)")));

        when(repartoService.devolverAPretemporada(any())).thenThrow(new IllegalArgumentException("sin equipo definitivo"));
        mockMvc.perform(post("/admin/reparto/devolver").with(csrf()).with(user("a").roles("SUPER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"jugadorIds\":[1]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("sin equipo definitivo"));
    }
}
