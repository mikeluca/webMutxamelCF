package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.EquipacionResponse;
import com.mikedev.mutxamelcf.service.EquipacionService;

class AppEquipacionControllerTest {

    private final EquipacionService servicio = mock(EquipacionService.class);
    private final AppEquipacionController controller = new AppEquipacionController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void sinAutenticacionEs401() {
        assertThat(controller.listar(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelveLosEquipajesActivos() {
        List<EquipacionResponse> lista = List.of(
                new EquipacionResponse(1L, "Primera", "Blanca", "Blanco", "Blancas", "#FFFFFF"));
        when(servicio.obtenerActivasParaApp(7L)).thenReturn(lista);

        var resultado = controller.listar(autenticado("7"));

        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.getBody()).isEqualTo(lista);
    }

    @Test
    void quienNoGestionaEquiposEs403() {
        when(servicio.obtenerActivasParaApp(8L)).thenThrow(new SecurityException("sin permiso"));

        var resultado = controller.listar(autenticado("8"));

        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resultado.getBody()).isEqualTo("sin permiso");
    }
}
