package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.ConvocatoriaGuardarRequest;
import com.mikedev.mutxamelcf.model.ConvocatoriaResponse;
import com.mikedev.mutxamelcf.service.ConvocatoriaService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConvocatoriaControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void crearDevuelve401SinAutenticacion() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.crear(new ConvocatoriaGuardarRequest(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve201ConLaConvocatoriaCreada() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        when(service.crear(1L, request)).thenReturn(new ConvocatoriaResponse());

        ResponseEntity<?> response = controller.crear(request, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void crearDevuelve403SiElServicioDeniegaPorRol() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void actualizarDevuelveLaConvocatoriaActualizada() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenReturn(new ConvocatoriaResponse());

        ResponseEntity<?> response = controller.actualizar(5L, request, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void actualizarDevuelve400SiElServicioLanzaIllegalArgument() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenThrow(new IllegalArgumentException("no valido"));

        assertThat(controller.actualizar(5L, request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerPorIdDevuelve404SiNoExiste() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        when(service.obtenerPorId(1L, 5L)).thenThrow(new IllegalArgumentException("no existe"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorIdDevuelveLaConvocatoria() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        when(service.obtenerPorId(1L, 5L)).thenReturn(new ConvocatoriaResponse());

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerPorEquipoDevuelve400SiFaltaElEquipoId() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.obtenerPorEquipo(null, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerPorEquipoDevuelveLaLista() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        when(service.obtenerPorEquipo(1L, 5L)).thenReturn(List.of(new ConvocatoriaResponse()));

        assertThat(controller.obtenerPorEquipo(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void eliminarDevuelve204CuandoTieneExito() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        ResponseEntity<?> response = controller.eliminar(5L, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).eliminar(1L, 5L);
    }

    @Test
    void eliminarDevuelve404SiNoExiste() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        doThrow(new IllegalArgumentException("no existe")).when(service).eliminar(1L, 5L);

        assertThat(controller.eliminar(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void crearDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.crear(new ConvocatoriaGuardarRequest(), autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void actualizarDevuelve401SinAutenticacion() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.actualizar(5L, new ConvocatoriaGuardarRequest(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualizarDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.actualizar(5L, new ConvocatoriaGuardarRequest(), autenticado("no-numero"))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualizarDevuelve403SiElServicioDeniegaPorRol() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.actualizar(5L, request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void actualizarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.actualizar(5L, request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerPorIdDevuelve401SinAutenticacion() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.obtenerPorId(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorIdDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.obtenerPorId(5L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorIdDevuelve403SiElServicioDeniegaPorRol() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        when(service.obtenerPorId(1L, 5L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorIdDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        when(service.obtenerPorId(1L, 5L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerPorEquipoDevuelve401SinAutenticacion() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.obtenerPorEquipo(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorEquipoDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.obtenerPorEquipo(5L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorEquipoDevuelve403SiElServicioDeniegaPorRol() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        when(service.obtenerPorEquipo(1L, 5L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerPorEquipo(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorEquipoDevuelve400SiElServicioLanzaIllegalArgument() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        when(service.obtenerPorEquipo(1L, 5L)).thenThrow(new IllegalArgumentException("equipo invalido"));

        assertThat(controller.obtenerPorEquipo(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerPorEquipoDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        when(service.obtenerPorEquipo(1L, 5L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerPorEquipo(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void eliminarDevuelve401SinAutenticacion() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.eliminar(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eliminarDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);

        assertThat(controller.eliminar(5L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eliminarDevuelve403SiElServicioDeniegaPorRol() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        doThrow(new SecurityException("sin permiso")).when(service).eliminar(1L, 5L);

        assertThat(controller.eliminar(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void eliminarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ConvocatoriaService service = mock(ConvocatoriaService.class);
        AppConvocatoriaController controller = new AppConvocatoriaController(service);
        doThrow(new RuntimeException("fallo")).when(service).eliminar(1L, 5L);

        assertThat(controller.eliminar(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
