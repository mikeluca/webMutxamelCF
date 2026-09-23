package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.CuotaFamiliarResponse;
import com.mikedev.mutxamelcf.service.CuotaFamiliarService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppCuotaControllerTest {

    @Test
    void misCuotasDevuelve401SiNoHayAutenticacion() {
        CuotaFamiliarService service = mock(CuotaFamiliarService.class);
        AppCuotaController controller = new AppCuotaController(service);

        ResponseEntity<?> response = controller.misCuotas(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void misCuotasDevuelve401SiElNombreNoEsUnIdValido() {
        CuotaFamiliarService service = mock(CuotaFamiliarService.class);
        AppCuotaController controller = new AppCuotaController(service);

        Authentication auth = new TestingAuthenticationToken("no-es-un-numero", null);
        auth.setAuthenticated(true);

        ResponseEntity<?> response = controller.misCuotas(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void misCuotasDevuelveLasCuotasDelUsuario() {
        CuotaFamiliarService service = mock(CuotaFamiliarService.class);
        AppCuotaController controller = new AppCuotaController(service);

        when(service.obtenerCuotasDeMisJugadores(1)).thenReturn(List.of(new CuotaFamiliarResponse()));

        Authentication auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(true);

        ResponseEntity<?> response = controller.misCuotas(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).hasSize(1);
    }

    @Test
    void misCuotasDevuelve500SiElServicioFalla() {
        CuotaFamiliarService service = mock(CuotaFamiliarService.class);
        AppCuotaController controller = new AppCuotaController(service);

        when(service.obtenerCuotasDeMisJugadores(1)).thenThrow(new RuntimeException("fallo"));

        Authentication auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(true);

        ResponseEntity<?> response = controller.misCuotas(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
