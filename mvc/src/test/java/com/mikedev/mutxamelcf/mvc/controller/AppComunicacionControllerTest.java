package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ComunicacionRequest;
import com.mikedev.mutxamelcf.model.ComunicacionResponse;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacionResponse;
import com.mikedev.mutxamelcf.model.MensajeConversacionResponse;
import com.mikedev.mutxamelcf.service.ComunicacionService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppComunicacionControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void crearDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.crear(new ComunicacionRequest(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve201ConLaComunicacionCreada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.crear(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(new Comunicacion());

        ResponseEntity<?> response = controller.crear(new ComunicacionRequest(), autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void crearDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.crear(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.crear(new ComunicacionRequest(), autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerParaUsuarioDevuelveLaLista() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.listarParaUsuario(1L)).thenReturn(List.of(new ComunicacionResponse()));

        assertThat(controller.obtenerParaUsuario(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listarConversacionesDevuelveLaLista() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.listarConversacionesParaUsuario(1L)).thenReturn(List.of(new ComunicacionResponse()));

        assertThat(controller.listarConversaciones(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerPorIdDevuelve404SiNoExiste() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.obtenerPorId(5L, 1L)).thenThrow(new IllegalArgumentException("no existe"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorIdDevuelveLaComunicacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.obtenerPorId(5L, 1L)).thenReturn(new Comunicacion());

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void eliminarDevuelve204CuandoTieneExito() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        ResponseEntity<?> response = controller.eliminar(5L, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).eliminar(5L, 1L);
    }

    @Test
    void eliminarDevuelve404SiNoExiste() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        doThrow(new IllegalArgumentException("no existe")).when(service).eliminar(5L, 1L);

        assertThat(controller.eliminar(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerEnviadasDevuelveLaLista() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.listarEnviadasParaUsuario(1L)).thenReturn(List.of(new ComunicacionResponse()));

        assertThat(controller.obtenerEnviadas(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerDestinatariosDevuelveLaLista() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.obtenerDestinatariosDirectos(1L)).thenReturn(List.of(new DestinatarioComunicacionResponse()));

        assertThat(controller.obtenerDestinatarios(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerConversacionDevuelveLosMensajes() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        when(service.obtenerConversacion(1L, 2L)).thenReturn(List.of(new MensajeConversacionResponse()));

        assertThat(controller.obtenerConversacion(2L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void marcarConversacionLeidaDevuelve204() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        ResponseEntity<?> response = controller.marcarConversacionLeida(2L, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).marcarConversacionLeida(1L, 2L);
    }

    @Test
    void crearDevuelve401ConAutenticacionNoAutenticada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        Authentication auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(false);

        assertThat(controller.crear(new ComunicacionRequest(), auth).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.crear(new ComunicacionRequest(), autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve400SiElServicioLanzaIllegalArgument() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.crear(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenThrow(new IllegalArgumentException("titulo obligatorio"));

        assertThat(controller.crear(new ComunicacionRequest(), autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.crear(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenThrow(new RuntimeException("fallo"));

        assertThat(controller.crear(new ComunicacionRequest(), autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerParaUsuarioDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerParaUsuario(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerParaUsuarioDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.listarParaUsuario(1L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerParaUsuario(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerParaUsuarioDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.listarParaUsuario(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerParaUsuario(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void listarConversacionesDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.listarConversaciones(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listarConversacionesDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.listarConversacionesParaUsuario(1L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.listarConversaciones(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listarConversacionesDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.listarConversacionesParaUsuario(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.listarConversaciones(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerPorIdDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerPorId(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorIdDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.obtenerPorId(5L, 1L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorIdDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.obtenerPorId(5L, 1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void eliminarDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.eliminar(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eliminarDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        doThrow(new SecurityException("sin permiso")).when(service).eliminar(5L, 1L);

        assertThat(controller.eliminar(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void eliminarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        doThrow(new RuntimeException("fallo")).when(service).eliminar(5L, 1L);

        assertThat(controller.eliminar(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerEnviadasDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerEnviadas(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerEnviadasDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.listarEnviadasParaUsuario(1L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerEnviadas(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerEnviadasDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.listarEnviadasParaUsuario(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerEnviadas(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerDestinatariosDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerDestinatarios(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerDestinatariosDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.obtenerDestinatariosDirectos(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerDestinatarios(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerConversacionDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerConversacion(2L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerConversacionDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.obtenerConversacion(1L, 2L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerConversacion(2L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerConversacionDevuelve400SiElServicioLanzaIllegalArgument() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.obtenerConversacion(1L, 2L)).thenThrow(new IllegalArgumentException("otro usuario invalido"));

        assertThat(controller.obtenerConversacion(2L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerConversacionDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        when(service.obtenerConversacion(1L, 2L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerConversacion(2L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void marcarConversacionLeidaDevuelve401SinAutenticacion() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.marcarConversacionLeida(2L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marcarConversacionLeidaDevuelve403SiElServicioDeniegaPorRol() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        doThrow(new SecurityException("sin permiso")).when(service).marcarConversacionLeida(1L, 2L);

        assertThat(controller.marcarConversacionLeida(2L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void marcarConversacionLeidaDevuelve500SiElServicioLanzaExcepcionInesperada() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);
        doThrow(new RuntimeException("fallo")).when(service).marcarConversacionLeida(1L, 2L);

        assertThat(controller.marcarConversacionLeida(2L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerParaUsuarioDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerParaUsuario(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorIdDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerPorId(5L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eliminarDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.eliminar(5L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerEnviadasDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerEnviadas(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerDestinatariosDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerDestinatarios(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerConversacionDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.obtenerConversacion(2L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marcarConversacionLeidaDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        ComunicacionService service = mock(ComunicacionService.class);
        AppComunicacionController controller = new AppComunicacionController(service);

        assertThat(controller.marcarConversacionLeida(2L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
