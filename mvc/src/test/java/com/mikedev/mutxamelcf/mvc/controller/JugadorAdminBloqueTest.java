package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.CambioEquipoJugador;
import com.mikedev.mutxamelcf.model.CambioEquipoJugador.Resultado;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

/**
 * Las acciones en bloque del panel de jugadores y la plantilla, con la seguridad y la plantilla reales (MockMvc): borrar,
 * dar de baja y cambiar de equipo a varios jugadores a la vez, cada uno por su cuenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JugadorAdminBloqueTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private JugadorService jugadorService;

    @MockBean
    private EquipoService equipoService;

    @MockBean
    private FamiliarService familiarService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void limpiar() {
        reset(jugadorService, equipoService, familiarService);
    }

    private static JugadorDTO jugador(long id, String nombre, String apellidos) {
        JugadorDTO j = new JugadorDTO();
        j.setId(id);
        j.setNombre(nombre);
        j.setApellidos(apellidos);
        return j;
    }

    private static String ids(long... ids) {
        return "{\"ids\":[" + LongStream.of(ids).mapToObj(String::valueOf).collect(Collectors.joining(",")) + "]}";
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String json, String rol)
            throws Exception {
        return mockMvc.perform(post(url).with(csrf()).with(user("u").roles(rol)).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ---- borrar-multiple ----

    @Test
    void borrarMultipleBorraATodosLosBorrables() throws Exception {
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García"));
        when(jugadorService.obtenerJugadorPorId(2L)).thenReturn(jugador(2, "Luis", "Mora"));

        postJson("/admin/jugadores/borrar-multiple", ids(1, 2), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctos", hasSize(2)))
                .andExpect(jsonPath("$.correctos[0].nombre").value("Pepe García"))
                .andExpect(jsonPath("$.errores", hasSize(0)));

        verify(jugadorService).eliminarJugador(1L);
        verify(jugadorService).eliminarJugador(2L);
    }

    @Test
    void borrarMultipleConMezclaBorraLosBorrablesYExplicaLosDemas() throws Exception {
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García"));
        when(jugadorService.obtenerJugadorPorId(2L)).thenReturn(jugador(2, "Luis", "Mora"));
        when(jugadorService.obtenerJugadorPorId(3L)).thenReturn(jugador(3, "Ana", "Sanz"));
        doThrow(new IllegalStateException("No se puede eliminar el jugador porque tiene cuotas asignadas."))
                .when(jugadorService).eliminarJugador(2L);
        doThrow(new RuntimeException("fallo raro de base de datos")).when(jugadorService).eliminarJugador(3L);

        postJson("/admin/jugadores/borrar-multiple", ids(1, 2, 3), "ENTRENADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctos", hasSize(1)))
                .andExpect(jsonPath("$.correctos[0].id").value(1))
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].nombre").value("Luis Mora"))
                .andExpect(jsonPath("$.errores[0].motivo").value(containsString("tiene cuotas asignadas")))
                // Un fallo inesperado no filtra el detalle interno
                .andExpect(jsonPath("$.errores[1].motivo").value("No se ha podido eliminar el jugador."));

        // El primero se borra aunque el segundo y el tercero fallen: cada uno por su cuenta
        verify(jugadorService).eliminarJugador(1L);
        verify(jugadorService).eliminarJugador(2L);
        verify(jugadorService).eliminarJugador(3L);
    }

    @Test
    void borrarMultipleConIdsQueNoExistenLosMarcaComoError() throws Exception {
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García"));
        when(jugadorService.obtenerJugadorPorId(404L)).thenReturn(null);

        postJson("/admin/jugadores/borrar-multiple", ids(1, 404), "SUPER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctos", hasSize(1)))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].id").value(404))
                .andExpect(jsonPath("$.errores[0].motivo").value("el jugador no existe"));

        verify(jugadorService, never()).eliminarJugador(404L);
    }

    @Test
    void borrarMultipleIgnoraLosIdsRepetidos() throws Exception {
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García"));

        postJson("/admin/jugadores/borrar-multiple", ids(1, 1, 1), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctos", hasSize(1)));

        verify(jugadorService).eliminarJugador(1L);
    }

    @Test
    void borrarMultipleConListaVaciaOSinListaDevuelve400() throws Exception {
        postJson("/admin/jugadores/borrar-multiple", "{\"ids\":[]}", "ADMIN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        postJson("/admin/jugadores/borrar-multiple", "{}", "ADMIN").andExpect(status().isBadRequest());

        verify(jugadorService, never()).eliminarJugador(anyLong());
    }

    @Test
    void borrarMultipleConMasDe200Devuelve400YNoBorraNada() throws Exception {
        long[] muchos = LongStream.rangeClosed(1, 201).toArray();

        postJson("/admin/jugadores/borrar-multiple", ids(muchos), "ADMIN").andExpect(status().isBadRequest());

        verify(jugadorService, never()).eliminarJugador(anyLong());
    }

    @Test
    void borrarMultipleAdmiteExactamente200() throws Exception {
        long[] justos = LongStream.rangeClosed(1, 200).toArray();
        when(jugadorService.obtenerJugadorPorId(anyLong())).thenReturn(jugador(1, "Pepe", "García"));

        postJson("/admin/jugadores/borrar-multiple", ids(justos), "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.correctos", hasSize(200)));
    }

    @Test
    void borrarMultipleTienenLosMismosPermisosQueElBorradoIndividual() throws Exception {
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García"));

        // Sin sesión: no pasa (redirige al login) y sin CSRF tampoco
        mockMvc.perform(post("/admin/jugadores/borrar-multiple").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(ids(1))).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/jugadores/borrar-multiple").with(user("u").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(ids(1))).andExpect(status().isForbidden());
        // Un rol que no es del panel tampoco
        postJson("/admin/jugadores/borrar-multiple", ids(1), "FAMILIAR").andExpect(status().isForbidden());

        // Los tres roles del panel sí, igual que en el borrado individual
        for (String rol : List.of("SUPER", "ADMIN", "ENTRENADOR")) {
            postJson("/admin/jugadores/borrar-multiple", ids(1), rol).andExpect(status().isOk());
            mockMvc.perform(post("/admin/jugadores/borrar/1").with(csrf()).with(user("u").roles(rol)))
                    .andExpect(status().isOk());
        }
    }

    // ---- baja-multiple ----

    @Test
    void bajaMultipleSaltaALosQueYaEstanDeBajaYLoDice() throws Exception {
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García"));
        when(jugadorService.obtenerJugadorPorId(2L)).thenReturn(jugador(2, "Luis", "Mora"));
        when(jugadorService.obtenerJugadorPorId(3L)).thenReturn(jugador(3, "Ana", "Sanz"));
        doThrow(new IllegalStateException("El jugador ya está de baja.")).when(jugadorService).darDeBaja(2L);
        doThrow(new RuntimeException("x")).when(jugadorService).darDeBaja(3L);

        postJson("/admin/jugadores/baja-multiple", ids(1, 2, 3), "ENTRENADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctos", hasSize(1)))
                .andExpect(jsonPath("$.correctos[0].nombre").value("Pepe García"))
                .andExpect(jsonPath("$.saltados", hasSize(1)))
                .andExpect(jsonPath("$.saltados[0].nombre").value("Luis Mora"))
                .andExpect(jsonPath("$.saltados[0].motivo").value("El jugador ya está de baja."))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].id").value(3));

        verify(jugadorService).darDeBaja(1L);
    }

    @Test
    void bajaMultipleValidaLaLista() throws Exception {
        postJson("/admin/jugadores/baja-multiple", "{\"ids\":[]}", "ADMIN").andExpect(status().isBadRequest());
        postJson("/admin/jugadores/baja-multiple", ids(LongStream.rangeClosed(1, 201).toArray()), "ADMIN")
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/jugadores/baja-multiple").with(user("u").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(ids(1))).andExpect(status().isForbidden());

        verify(jugadorService, never()).darDeBaja(anyLong());
    }

    // ---- cambiar-equipo-multiple ----

    private static EquipoDTO equipo(long id, String nombre) {
        EquipoDTO e = new EquipoDTO();
        e.setId(id);
        e.setNombre(nombre);
        e.setCategoria("Veteranos");
        e.setDeporte("F");
        return e;
    }

    @Test
    void cambiarEquipoMultipleResumeMovidosSinDorsalSaltadosYErrores() throws Exception {
        when(equipoService.obtenerEquipoPorId(7L)).thenReturn(equipo(7, "Veteranos A"));
        for (long id = 1; id <= 5; id++) {
            when(jugadorService.obtenerJugadorPorId(id)).thenReturn(jugador(id, "J" + id, "Ap" + id));
        }
        when(jugadorService.cambiarEquipo(1L, 7L)).thenReturn(new CambioEquipoJugador(Resultado.MOVIDO, ""));
        when(jugadorService.cambiarEquipo(2L, 7L))
                .thenReturn(new CambioEquipoJugador(Resultado.MOVIDO_SIN_DORSAL, "movido sin dorsal, el 7 ya lo tiene Juan López"));
        when(jugadorService.cambiarEquipo(3L, 7L))
                .thenReturn(new CambioEquipoJugador(Resultado.SALTADO, "ya está en ese equipo"));
        when(jugadorService.cambiarEquipo(4L, 7L))
                .thenThrow(new IllegalStateException("Está de baja: hay que reactivarlo para asignarle un equipo."));
        when(jugadorService.cambiarEquipo(5L, 7L)).thenThrow(new RuntimeException("boom"));

        postJson("/admin/jugadores/cambiar-equipo-multiple", "{\"ids\":[1,2,3,4,5],\"equipoId\":7}", "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctos", hasSize(1)))
                .andExpect(jsonPath("$.conAviso", hasSize(1)))
                .andExpect(jsonPath("$.conAviso[0].nombre").value("J2 Ap2"))
                .andExpect(jsonPath("$.conAviso[0].motivo").value("movido sin dorsal, el 7 ya lo tiene Juan López"))
                .andExpect(jsonPath("$.saltados", hasSize(2)))
                .andExpect(jsonPath("$.saltados[1].motivo").value(containsString("reactivarlo")))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].id").value(5));
    }

    @Test
    void cambiarEquipoMultipleConEquipoQueNoExisteDevuelve400() throws Exception {
        when(equipoService.obtenerEquipoPorId(404L)).thenThrow(new EmptyResultDataAccessException(1));

        postJson("/admin/jugadores/cambiar-equipo-multiple", "{\"ids\":[1],\"equipoId\":404}", "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El equipo seleccionado no existe."));
        postJson("/admin/jugadores/cambiar-equipo-multiple", "{\"ids\":[1]}", "ADMIN")
                .andExpect(status().isBadRequest());

        verify(jugadorService, never()).cambiarEquipo(anyLong(), anyLong());
    }

    @Test
    void cambiarEquipoMultipleValidaLaListaYLosPermisos() throws Exception {
        postJson("/admin/jugadores/cambiar-equipo-multiple", "{\"ids\":[],\"equipoId\":7}", "ADMIN")
                .andExpect(status().isBadRequest());
        String muchos = LongStream.rangeClosed(1, 201).mapToObj(String::valueOf).collect(Collectors.joining(","));
        postJson("/admin/jugadores/cambiar-equipo-multiple", "{\"ids\":[" + muchos + "],\"equipoId\":7}", "ADMIN")
                .andExpect(status().isBadRequest());
        postJson("/admin/jugadores/cambiar-equipo-multiple", "{\"ids\":[1],\"equipoId\":7}", "FAMILIAR")
                .andExpect(status().isForbidden());

        verify(jugadorService, never()).cambiarEquipo(anyLong(), eq(7L));
    }

    // ---- plantilla ----

    @Test
    void laUrlConCategoriaYTextoDejaLosFiltrosPuestosAlCargar() throws Exception {
        when(equipoService.obtenerTodos()).thenReturn(List.of(equipo(7, "Veteranos A")));
        when(equipoService.obtenerCategorias()).thenReturn(List.of("Senior", "Veteranos"));
        when(jugadorService.obtenerActivos()).thenReturn(List.of(jugador(1, "Pepe", "García")));
        when(familiarService.obtenerTodos()).thenReturn(List.of());

        mockMvc.perform(get("/admin/jugadores?estado=activos&categoria=Veteranos&q=pepe")
                .with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                // La categoría de la URL llega seleccionada y las demás no
                .andExpect(content().string(containsString("<option value=\"Veteranos\" selected=\"selected\">")))
                .andExpect(content().string(not(containsString("<option value=\"Senior\" selected"))))
                // Y el texto buscado, en la caja
                .andExpect(content().string(containsString("value=\"pepe\"")))
                // El script los aplica al cargar y los mantiene en la URL sin recargar
                .andExpect(content().string(containsString("aplicarFiltrosDeLaUrl")))
                .andExpect(content().string(containsString("history.replaceState")))
                // La selección: casilla por fila con su etiqueta y barra anunciada
                .andExpect(content().string(containsString("aria-label=\"Seleccionar a Pepe García\"")))
                .andExpect(content().string(containsString("aria-live=\"polite\"")))
                // Ninguna acción se guarda en el navegador
                .andExpect(content().string(not(containsString("localStorage"))))
                // Y ya no se vuelve a '/admin/jugadores' a secas tras guardar
                .andExpect(content().string(not(containsString("window.location.href = '/admin/jugadores'"))));
    }

    @Test
    void sinFiltrosEnLaUrlNingunaCategoriaViajaSeleccionada() throws Exception {
        when(equipoService.obtenerTodos()).thenReturn(List.of());
        when(equipoService.obtenerCategorias()).thenReturn(List.of("Veteranos"));
        when(jugadorService.obtenerActivos()).thenReturn(List.of());
        when(familiarService.obtenerTodos()).thenReturn(List.of());

        mockMvc.perform(get("/admin/jugadores").with(user("u").roles("ENTRENADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<option value=\"Veteranos\" selected"))));
    }
}
