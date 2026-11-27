package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
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
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.CambioEquipoJugador;
import com.mikedev.mutxamelcf.model.CambioEquipoJugador.Resultado;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorDeFamiliar;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

/** Las páginas de Jugadores y de Familiares con la plantilla y la seguridad reales. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListadosPanelPaginaTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private JugadorService jugadorService;

    @MockBean
    private EquipoService equipoService;

    @MockBean
    private FamiliarService familiarService;

    @MockBean
    private FamiliarJugadorService familiarJugadorService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void limpiar() {
        reset(jugadorService, equipoService, familiarService, familiarJugadorService);
    }

    private static EquipoDTO equipo(long id, String nombre, String categoria) {
        EquipoDTO e = new EquipoDTO();
        e.setId(id);
        e.setNombre(nombre);
        e.setCategoria(categoria);
        e.setDeporte("F");
        return e;
    }

    private static JugadorDTO jugador(long id, String nombre, String apellidos, Integer dorsal, String posicion) {
        JugadorDTO j = new JugadorDTO();
        j.setId(id);
        j.setNombre(nombre);
        j.setApellidos(apellidos);
        j.setCategoria("Alevín");
        j.setEquipo("Alevín B");
        j.setEquipoId(7L);
        j.setDeporte("F");
        j.setDorsal(dorsal);
        j.setPosicion(posicion);
        return j;
    }

    private void datosDeJugadores(Map<Long, Integer> conteo) {
        when(equipoService.obtenerTodos())
                .thenReturn(List.of(equipo(7, "Alevín B", "Alevín"), equipo(8, "Infantil A", "Infantil")));
        when(equipoService.obtenerCategorias()).thenReturn(List.of("Alevín", "Infantil"));
        when(jugadorService.obtenerActivos()).thenReturn(List.of(jugador(1, "Pepe", "García", 9, "Portero"),
                jugador(2, "Luis", "Mora", null, null)));
        when(jugadorService.contarActivosPorEquipo()).thenReturn(conteo);
        when(familiarService.obtenerTodos()).thenReturn(List.of());
    }

    // ---- Jugadores ----

    @Test
    void laTablaDeJugadoresYaNoTieneDeporteYTieneDorsalYPosicion() throws Exception {
        datosDeJugadores(Map.of(7L, 2));

        mockMvc.perform(get("/admin/jugadores").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(">Deporte</th>"))))
                .andExpect(content().string(not(containsString("Fútbol Sala"))))
                .andExpect(content().string(containsString(">Nombre completo</th>")))
                .andExpect(content().string(containsString(">Categoría</th>")))
                .andExpect(content().string(containsString(">Equipo</th>")))
                .andExpect(content().string(containsString(">Dorsal</th>")))
                .andExpect(content().string(containsString(">Posición</th>")))
                .andExpect(content().string(containsString(">Acciones</th>")))
                // Con dorsal y posición, o vacío si no los tiene
                .andExpect(content().string(containsString("<td class=\"align-middle col-dorsal\">9</td>")))
                .andExpect(content().string(containsString("<td class=\"align-middle col-posicion\">Portero</td>")))
                .andExpect(content().string(containsString("<td class=\"align-middle col-dorsal\"></td>")))
                .andExpect(content().string(containsString("<td class=\"align-middle col-posicion\"></td>")));
    }

    @Test
    void elFiltroDeEquipoOfreceLosEquiposYSuParametroEnLaUrlLlegaSeleccionado() throws Exception {
        datosDeJugadores(Map.of(7L, 2));

        mockMvc.perform(get("/admin/jugadores?equipo=8&categoria=Infantil").with(user("u").roles("ENTRENADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"filtroEquipo\"")))
                .andExpect(content().string(containsString("<option value=\"\">Todos</option>")))
                // Cada equipo con su categoría, para que el desplegable enseñe solo los de la categoría elegida
                .andExpect(content().string(containsString("<option value=\"7\" data-categoria=\"Alevín\">Alevín B</option>")))
                .andExpect(content().string(containsString(
                        "<option value=\"8\" data-categoria=\"Infantil\" selected=\"selected\">Infantil A</option>")))
                // El script guarda y lee el equipo en la URL y devuelve a "Todos" el que no es de la categoría
                .andExpect(content().string(containsString("equipo: document.getElementById('filtroEquipo').value")))
                .andExpect(content().string(containsString("params.get('equipo')")))
                .andExpect(content().string(containsString("FiltrosPanel.crearSelectorDeEquipos(")));
    }

    @Test
    void elContadorDelEquipoCuentaLosActivosSinBajasYEnSingularConUno() throws Exception {
        datosDeJugadores(new java.util.TreeMap<>(Map.of(7L, 12, 8L, 1)));

        mockMvc.perform(get("/admin/jugadores?equipo=7").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">12 jugadores en Alevín B</p>")))
                // El conteo de todos los equipos viaja a la página para recalcular sin pedir nada
                .andExpect(content().string(containsString("data-conteo=\"{&quot;7&quot;:12,&quot;8&quot;:1}\"")));
        mockMvc.perform(get("/admin/jugadores?equipo=8&estado=todos").with(user("u").roles("ADMIN")))
                .andExpect(content().string(containsString(">1 jugador en Infantil A</p>")));
        // Sin equipo elegido, el indicador no aparece
        mockMvc.perform(get("/admin/jugadores").with(user("u").roles("ADMIN")))
                .andExpect(content().string(containsString("style=\"display:none;\"")))
                .andExpect(content().string(not(containsString("jugadores en Alevín B</p>"))));
    }

    @Test
    void elContadorSeActualizaTrasUnCambioDeEquipoEnBloque() throws Exception {
        when(equipoService.obtenerEquipoPorId(8L)).thenReturn(equipo(8, "Infantil A", "Infantil"));
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García", 9, "Portero"));
        when(jugadorService.cambiarEquipo(1L, 8L)).thenReturn(new CambioEquipoJugador(Resultado.MOVIDO, ""));
        // Después del cambio: el equipo 7 pierde uno y el 8 gana uno
        when(jugadorService.contarActivosPorEquipo()).thenReturn(Map.of(7L, 11, 8L, 2));

        mockMvc.perform(post("/admin/jugadores/cambiar-equipo-multiple").with(csrf()).with(user("u").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1],\"equipoId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteoEquipos.7").value(11))
                .andExpect(jsonPath("$.conteoEquipos.8").value(2));
    }

    @Test
    void elContadorSeActualizaTambienTrasBorrarOCambiarDeBaja() throws Exception {
        when(jugadorService.obtenerJugadorPorId(1L)).thenReturn(jugador(1, "Pepe", "García", 9, "Portero"));
        when(jugadorService.contarActivosPorEquipo()).thenReturn(Map.of(7L, 11));

        mockMvc.perform(post("/admin/jugadores/borrar-multiple").with(csrf()).with(user("u").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}"))
                .andExpect(jsonPath("$.conteoEquipos.7").value(11));
        mockMvc.perform(post("/admin/jugadores/baja-multiple").with(csrf()).with(user("u").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}"))
                .andExpect(jsonPath("$.conteoEquipos.7").value(11));
    }

    // ---- Familiares ----

    private static FamiliarDTO familiar(long id, String nombre, String apellidos) {
        FamiliarDTO f = new FamiliarDTO();
        f.setId(id);
        f.setNombre(nombre);
        f.setApellidos(apellidos);
        f.setTelefono("600000000");
        f.setEmail(nombre.toLowerCase() + "@ejemplo.test");
        f.setRecibeInfoClub(1);
        f.setWhatsappActivo(1);
        return f;
    }

    /**
     * Equipos: 7 Alevín B, 9 Alevín A (Alevín) y 8 Infantil A (Infantil). Familiares: 1 Alonso (sin jugadores), 2 Blanco
     * (Eva, en el 7), 3 Cano (Raúl en el 9, Bea en el 8 y Pau de baja) y 4 Dura (Dani, en el 7).
     */
    private void datosDeFamiliares() {
        when(familiarService.obtenerTodos()).thenReturn(List.of(familiar(1, "Ana", "Alonso"),
                familiar(2, "Beto", "Blanco"), familiar(3, "Carla", "Cano"), familiar(4, "Diego", "Dura")));
        when(jugadorService.obtenerTodos()).thenReturn(List.of());
        when(equipoService.obtenerCategorias()).thenReturn(List.of("Alevín", "Infantil"));
        when(equipoService.obtenerTodos()).thenReturn(List.of(equipo(7, "Alevín B", "Alevín"),
                equipo(9, "Alevín A", "Alevín"), equipo(8, "Infantil A", "Infantil")));
        when(familiarJugadorService.obtenerJugadoresDeTodosLosFamiliares()).thenReturn(Map.of(
                2L, List.of(new JugadorDeFamiliar(2L, 11L, "Eva", "Pérez", 7L, "Alevín B", "Alevín", false)),
                3L, List.of(new JugadorDeFamiliar(3L, 12L, "Raúl", "Sanz", 9L, "Alevín A", "Alevín", false),
                        new JugadorDeFamiliar(3L, 13L, "Bea", "Ibáñez", 8L, "Infantil A", "Infantil", false),
                        new JugadorDeFamiliar(3L, 14L, "Pau", "Vila", null, null, null, true)),
                4L, List.of(new JugadorDeFamiliar(4L, 15L, "Dani", "Ruiz", 7L, "Alevín B", "Alevín", false))));
    }

    @Test
    void laTablaDeFamiliaresTieneLasColumnasNuevasConCeroUnoYVariosJugadores() throws Exception {
        datosDeFamiliares();

        mockMvc.perform(get("/admin/familiares").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                // Fuera Info Club y WhatsApp de la tabla (siguen en el modal del familiar)
                .andExpect(content().string(not(containsString(">Info Club</th>"))))
                .andExpect(content().string(not(containsString(">WhatsApp</th>"))))
                .andExpect(content().string(containsString("WhatsApp Activo")))
                .andExpect(content().string(containsString(">Jugador/es asociado/s</th>")))
                .andExpect(content().string(containsString(">Equipo</th>")))
                // Con un jugador
                .andExpect(content().string(containsString(">Eva Pérez</div>")))
                .andExpect(content().string(containsString(">Alevín B</div>")))
                // Con varios, uno por línea; el de baja, "Sin equipo"
                .andExpect(content().string(containsString(">Raúl Sanz</div>")))
                .andExpect(content().string(containsString(">Bea Ibáñez</div>")))
                .andExpect(content().string(containsString(">Infantil A</div>")))
                .andExpect(content().string(containsString(">Pau Vila</div>")))
                .andExpect(content().string(containsString(">Sin equipo</div>")))
                // Sin jugadores: un guion en las dos columnas
                .andExpect(content().string(containsString("<span class=\"text-muted\">—</span>")));
    }

    @Test
    void unFamiliarConJugadoresDeDosCategoriasSaleEnAmbasYNoEnLaDeBaja() throws Exception {
        datosDeFamiliares();

        mockMvc.perform(get("/admin/familiares?categoria=Infantil&q=bea").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                // Las categorías de cada familiar, sin la del jugador de baja
                .andExpect(content().string(containsString("data-categorias=\"||\"")))
                .andExpect(content().string(containsString("data-categorias=\"|Alevín|\"")))
                .andExpect(content().string(containsString("data-categorias=\"|Alevín|Infantil|\"")))
                // El filtro llega puesto desde la URL, igual que en Jugadores
                .andExpect(content().string(containsString("<option value=\"Infantil\" selected=\"selected\">Infantil</option>")))
                .andExpect(content().string(containsString("value=\"bea\"")))
                .andExpect(content().string(containsString("fila.dataset.categorias.includes('|' + categoria + '|')")))
                .andExpect(content().string(containsString("history.replaceState")))
                .andExpect(content().string(not(containsString("localStorage"))));
    }

    @Test
    void losJugadoresDeLosFamiliaresSePidenEnUnaSolaLlamadaParaTodoElListado() throws Exception {
        datosDeFamiliares();

        mockMvc.perform(get("/admin/familiares").with(user("u").roles("ADMIN"))).andExpect(status().isOk());

        verify(familiarJugadorService, times(1)).obtenerJugadoresDeTodosLosFamiliares();
        verify(familiarJugadorService, never()).obtenerJugadoresDeFamiliar(anyLong());
    }

    @Test
    void laColumnaEquipoDeFamiliaresMuestraSoloElNombreSinLaCategoria() throws Exception {
        datosDeFamiliares();

        mockMvc.perform(get("/admin/familiares").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<th style=\"width: 20%;\">Equipo</th>")))
                .andExpect(content().string(not(containsString("<th style=\"width: 20%;\">Equipo / Categoría</th>"))))
                .andExpect(content().string(not(containsString(" · "))))
                // Un equipo por línea, alineado con su jugador; el de baja, "Sin equipo"
                .andExpect(content().string(containsString("class=\"equipo-asociado\">Alevín A</div>")))
                .andExpect(content().string(containsString("class=\"equipo-asociado\">Infantil A</div>")))
                .andExpect(content().string(containsString("class=\"equipo-asociado\">Sin equipo</div>")));
    }

    @Test
    void unFamiliarConHijosEnDosEquiposSaleEnAmbosYNoEnLosDemas() throws Exception {
        datosDeFamiliares();

        mockMvc.perform(get("/admin/familiares").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                // data-equipos como data-categorias: los equipos de los jugadores ACTIVOS (el de baja no aporta)
                .andExpect(content().string(containsString("data-equipos=\"|9|8|\"")))
                .andExpect(content().string(containsString("data-equipos=\"|7|\"")))
                .andExpect(content().string(containsString("data-equipos=\"||\"")))
                .andExpect(content().string(containsString("fila.dataset.equipos.includes('|' + equipo + '|')")));
    }

    @Test
    void elFiltroDeEquipoDeFamiliaresEsElMismoQueElDeJugadoresYSeLimitaALaCategoria() throws Exception {
        datosDeFamiliares();

        mockMvc.perform(get("/admin/familiares?categoria=Infantil&equipo=8").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"filtroEquipo\"")))
                .andExpect(content().string(containsString("<option value=\"\">Todos</option>")))
                // Cada equipo con su categoría; el equipo de la URL llega elegido
                .andExpect(content().string(containsString("<option value=\"7\" data-categoria=\"Alevín\">Alevín B</option>")))
                .andExpect(content().string(containsString("<option value=\"9\" data-categoria=\"Alevín\">Alevín A</option>")))
                .andExpect(content().string(containsString(
                        "<option value=\"8\" data-categoria=\"Infantil\" selected=\"selected\">Infantil A</option>")))
                // La limitación por categoría y la URL salen del script compartido con Jugadores
                .andExpect(content().string(containsString("/js/filtros-panel.js")))
                .andExpect(content().string(containsString("FiltrosPanel.crearSelectorDeEquipos(")))
                .andExpect(content().string(containsString("equipo: document.getElementById('filtroEquipo').value")))
                .andExpect(content().string(containsString("params.get('equipo')")));
    }

    @Test
    void elScriptCompartidoLimitaLosEquiposALaCategoriaYLaPaginaDeJugadoresLoUsaIgual() throws Exception {
        String js = new String(new org.springframework.core.io.ClassPathResource("static/js/filtros-panel.js")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        // Solo los equipos de la categoría elegida y, si el elegido no es de ella, "Todos"
        org.assertj.core.api.Assertions.assertThat(js).contains("o.dataset.categoria === categoria");
        org.assertj.core.api.Assertions.assertThat(js)
                .contains("selectEquipo.value = Array.from(selectEquipo.options).some(o => o.value === elegido) ? elegido : '';");

        when(equipoService.obtenerTodos()).thenReturn(List.of());
        when(equipoService.obtenerCategorias()).thenReturn(List.of());
        when(jugadorService.obtenerActivos()).thenReturn(List.of());
        when(familiarService.obtenerTodos()).thenReturn(List.of());
        mockMvc.perform(get("/admin/jugadores").with(user("u").roles("ADMIN")))
                .andExpect(content().string(containsString("/js/filtros-panel.js")))
                .andExpect(content().string(containsString("FiltrosPanel.crearSelectorDeEquipos(")))
                // Ya no hay una copia propia de esa lógica en la página
                .andExpect(content().string(not(containsString("const opcionesDeEquipo"))));
    }

    @Test
    void elContadorDeFamiliaresDelEquipoCuentaLosQueTienenUnJugadorActivoEnEl() throws Exception {
        datosDeFamiliares();

        // Equipo 7: Blanco (Eva) y Dura (Dani)
        mockMvc.perform(get("/admin/familiares?equipo=7").with(user("u").roles("ADMIN")))
                .andExpect(content().string(containsString(">2 familiares de jugadores de Alevín B</p>")));
        // Equipo 8: solo Cano (Bea): singular
        mockMvc.perform(get("/admin/familiares?equipo=8").with(user("u").roles("ADMIN")))
                .andExpect(content().string(containsString(">1 familiar de jugadores de Infantil A</p>")));
        // Sin equipo elegido o con uno que no existe, el indicador no aparece
        mockMvc.perform(get("/admin/familiares").with(user("u").roles("ADMIN")))
                .andExpect(content().string(not(containsString("de jugadores de Alevín B</p>"))))
                .andExpect(content().string(containsString("style=\"display:none;\"")));
        mockMvc.perform(get("/admin/familiares?equipo=999").with(user("u").roles("ADMIN")))
                .andExpect(content().string(not(containsString("de jugadores de Alevín"))));
        // El navegador lo recalcula al filtrar, con el mismo formato (y "(mostrando N)" si otro filtro reduce la lista)
        mockMvc.perform(get("/admin/familiares?equipo=7").with(user("u").roles("ADMIN")))
                .andExpect(content().string(containsString("FiltrosPanel.textoContador(delEquipo.length, 'familiar', 'familiares', 'de jugadores de',")));
    }
}
