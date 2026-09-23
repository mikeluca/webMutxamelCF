package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private JugadorService jugadoresService;
    private CuerpoTecnicoService cuerpoTecnicoService;
    private EquipoService equiposService;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        jugadoresService = mock(JugadorService.class);
        cuerpoTecnicoService = mock(CuerpoTecnicoService.class);
        equiposService = mock(EquipoService.class);

        controller = new AdminController(jugadoresService, cuerpoTecnicoService, equiposService);
    }

    @Test
    void dashboardPrincipalRedirigeAPagosParaRolSuper() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "oficina", "n/a", List.of(new SimpleGrantedAuthority("ROLE_SUPER")));

        String destino = controller.dashboardPrincipal(authentication);

        assertEquals("redirect:/admin/pagos", destino);
    }

    @Test
    void dashboardPrincipalRedirigeAAdminParaOtrosRoles() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "entrenador", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ENTRENADOR")));

        String destino = controller.dashboardPrincipal(authentication);

        assertEquals("redirect:/admin/admin", destino);
    }

    @Test
    void dashboardPrincipalSinAutenticacionRedirigeAAdmin() {
        String destino = controller.dashboardPrincipal(null);

        assertEquals("redirect:/admin/admin", destino);
    }

    @Test
    void loginNoRompeConListasVacias() {
        when(jugadoresService.obtenerTodos()).thenReturn(Collections.emptyList());
        when(equiposService.obtenerTodos()).thenReturn(Collections.emptyList());
        when(cuerpoTecnicoService.obtenerTodos()).thenReturn(Collections.emptyList());

        Model model = new ExtendedModelMap();

        String vista = controller.login(model);

        assertEquals("admin/admin", vista);
        assertEquals(0, model.getAttribute("totalJugadores"));
        assertEquals(0, model.getAttribute("totalEquipos"));
        assertEquals(0L, model.getAttribute("totalEntrenadores"));
    }

    @Test
    void loginConstruyeElResumenPorCategoriaYCuentaEntrenadoresUnicos() {
        JugadorDTO jugador = new JugadorDTO();
        jugador.setCategoria("Senior");
        when(jugadoresService.obtenerTodos()).thenReturn(List.of(jugador));

        EquipoDTO equipo = new EquipoDTO();
        equipo.setCategoria("Senior");
        equipo.setOrden("1");
        when(equiposService.obtenerTodos()).thenReturn(List.of(equipo));

        CuerpoTecnicoDTO entrenador1 = new CuerpoTecnicoDTO();
        entrenador1.setCategoria("Senior");
        entrenador1.setNombre("Juan");
        entrenador1.setApellidos("Perez");
        CuerpoTecnicoDTO entrenador2 = new CuerpoTecnicoDTO();
        entrenador2.setCategoria("Senior");
        entrenador2.setNombre("Juan");
        entrenador2.setApellidos("Perez");
        when(cuerpoTecnicoService.obtenerTodos()).thenReturn(List.of(entrenador1, entrenador2));

        Model model = new ExtendedModelMap();
        String vista = controller.login(model);

        assertEquals("admin/admin", vista);
        assertEquals(1L, model.getAttribute("totalEntrenadores"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resumen = (List<Map<String, Object>>) model.getAttribute("resumenCategorias");
        assertEquals(1, resumen.size());
        assertEquals("Senior", resumen.get(0).get("categoria"));
        assertEquals(1L, resumen.get(0).get("equipos"));
        assertEquals(1L, resumen.get(0).get("jugadores"));
        assertEquals(1, resumen.get(0).get("entrenadores"));
    }

    @Test
    void logoutDevuelveLaVistaIndex() {
        assertEquals("index", controller.logout());
    }
}
