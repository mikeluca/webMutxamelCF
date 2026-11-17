package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.mvc.controller.AppEquipoGestionController;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorEquipoAppDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

/** "posicionCodigo" y "editable" en las plantillas que usa la app. */
class PlantillaEditableTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static JugadorPublicDTO publico(String posicion) {
        JugadorPublicDTO dto = new JugadorPublicDTO();
        dto.setPosicion(posicion);
        return dto;
    }

    // ---- posicionCodigo -----------------------------------------------------------------------

    @Test
    void posicionCodigoSaleDelTextoGuardadoYElTextoSeConserva() {

        JugadorPublicDTO mayusculas = publico("PORTERO");
        JugadorPublicDTO minusculas = publico("portero");
        JugadorPublicDTO normal = publico("Centrocampista");
        JugadorPublicDTO acento = publico("Défensa");
        JugadorPublicDTO antiguo = publico("DEL");
        JugadorPublicDTO vacio = publico(null);

        assertThat(mayusculas.getPosicionCodigo()).isEqualTo("PORTERO");
        assertThat(minusculas.getPosicionCodigo()).isEqualTo("PORTERO");
        assertThat(normal.getPosicionCodigo()).isEqualTo("CENTROCAMPISTA");
        assertThat(acento.getPosicionCodigo()).isEqualTo("DEFENSA");
        assertThat(antiguo.getPosicionCodigo()).isNull();
        assertThat(vacio.getPosicionCodigo()).isNull();

        // "posicion" sigue siendo el texto tal cual (versiones antiguas y valores que no son de la lista)
        assertThat(minusculas.getPosicion()).isEqualTo("portero");
        assertThat(antiguo.getPosicion()).isEqualTo("DEL");
    }

    @Test
    void elJsonLlevaPosicionCodigoYEditable() throws Exception {

        JugadorPublicDTO dto = publico("delantero");
        dto.setEditable(true);

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto);

        assertThat(json).contains("\"posicion\":\"delantero\"").contains("\"posicionCodigo\":\"DELANTERO\"")
                .contains("\"editable\":true");

        JugadorEquipoAppDTO app = new JugadorEquipoAppDTO(1L, "A", "B", 3, "Extremo", null);
        String jsonApp = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(app);

        assertThat(jsonApp).contains("\"posicionCodigo\":\"EXTREMO\"").contains("\"editable\":false");
    }

    // ---- editable en la plantilla publica -----------------------------------------------------

    private PublicEquipoController controllerPublico(EquipoGestionDao gestion, boolean enPreparacion) {

        EquipoService equipoService = mock(EquipoService.class);
        JugadorService jugadorService = mock(JugadorService.class);
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(5L);
        equipo.setNombre("Cadete A");

        when(equipoService.obtenerEquipoPorId(5L)).thenReturn(equipo);
        when(jugadorService.plantillaEnPreparacion("Cadete A")).thenReturn(enPreparacion);
        when(jugadorService.obtenerJugadoresPublicosPorEquipo("Cadete A"))
                .thenAnswer(i -> new ArrayList<>(List.of(publico("Defensa"), publico("Portero"))));

        return new PublicEquipoController(equipoService, jugadorService, mock(CuerpoTecnicoService.class), gestion);
    }

    private static void sesion(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(usuario, null, "ROLE_X"));
    }

    @Test
    void editableEsTrueSiElUsuarioGestionaElEquipo() {

        EquipoGestionDao gestion = mock(EquipoGestionDao.class);
        when(gestion.puedeGestionarEquipo(7L, 5L)).thenReturn(true);
        sesion("7");

        List<JugadorPublicDTO> jugadores = controllerPublico(gestion, false).obtenerJugadores(5L);

        assertThat(jugadores).hasSize(2).allSatisfy(j -> assertThat(j.isEditable()).isTrue());
    }

    @Test
    void editableEsFalseSinPermisoSinSesionOComoAnonimo() {

        EquipoGestionDao gestion = mock(EquipoGestionDao.class);
        when(gestion.puedeGestionarEquipo(7L, 5L)).thenReturn(true);
        PublicEquipoController controller = controllerPublico(gestion, false);

        // Sin sesión
        assertThat(controller.obtenerJugadores(5L)).allSatisfy(j -> assertThat(j.isEditable()).isFalse());

        // Un usuario sin permiso sobre ese equipo (p. ej. entrenador de otro)
        sesion("8");
        assertThat(controller.obtenerJugadores(5L)).allSatisfy(j -> assertThat(j.isEditable()).isFalse());

        // Anónimo de Spring Security
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(controller.obtenerJugadores(5L)).allSatisfy(j -> assertThat(j.isEditable()).isFalse());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tambienLaPlantillaConEnPreparacionLlevaEditable() {

        EquipoGestionDao gestion = mock(EquipoGestionDao.class);
        when(gestion.puedeGestionarEquipo(7L, 5L)).thenReturn(true);
        sesion("7");

        ResponseEntity<Map<String, Object>> respuesta = controllerPublico(gestion, false).obtenerPlantilla(5L);
        List<JugadorPublicDTO> jugadores = (List<JugadorPublicDTO>) respuesta.getBody().get("jugadores");

        assertThat(jugadores).allSatisfy(j -> assertThat(j.isEditable()).isTrue());
    }

    @Test
    void elConstructorSinPermisosNuncaDaEditable() {

        EquipoService equipoService = mock(EquipoService.class);
        JugadorService jugadorService = mock(JugadorService.class);
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(5L);
        equipo.setNombre("Cadete A");
        when(equipoService.obtenerEquipoPorId(5L)).thenReturn(equipo);
        when(jugadorService.obtenerJugadoresPublicosPorEquipo("Cadete A"))
                .thenAnswer(i -> new ArrayList<>(List.of(publico("Defensa"))));
        sesion("7");

        assertThat(new PublicEquipoController(equipoService, jugadorService, mock(CuerpoTecnicoService.class))
                .obtenerJugadores(5L)).allSatisfy(j -> assertThat(j.isEditable()).isFalse());
    }

    // ---- editable en /api/app/equipos/jugadores -------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void laPlantillaDeLaAppDaEditableYSuCodigo() {

        EquipoGestionDao gestion = mock(EquipoGestionDao.class);
        JugadorService jugadorService = mock(JugadorService.class);
        when(gestion.existeEquipo(5L)).thenReturn(true);
        when(gestion.puedeGestionarEquipo(7L, 5L)).thenReturn(true);
        when(gestion.obtenerJugadoresPorEquipo(5L)).thenReturn(List.of(11L));

        JugadorDTO jugador = new JugadorDTO();
        jugador.setId(11L);
        jugador.setPosicion("portero");
        when(jugadorService.obtenerJugadorPorId(11L)).thenReturn(jugador);

        AppEquipoGestionController controller = new AppEquipoGestionController(gestion, jugadorService,
                mock(FamiliarService.class), mock(JustificacionFaltaPartidoDao.class), mock(PartidoDao.class));
        TestingAuthenticationToken auth = new TestingAuthenticationToken("7", null);
        auth.setAuthenticated(true);

        List<JugadorEquipoAppDTO> lista = (List<JugadorEquipoAppDTO>) controller.obtenerJugadores(5L, null, auth)
                .getBody();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).isEditable()).isTrue();
        assertThat(lista.get(0).getPosicionCodigo()).isEqualTo("PORTERO");
        assertThat(lista.get(0).getPosicion()).isEqualTo("portero");
    }
}
