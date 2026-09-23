package com.mikedev.mutxamelcf.mvc.controller;

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
}
