package com.mikedev.mutxamelcf.mvc.communication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

class CuentaActivadaNotifierTest {

    private UsuarioAppService usuarioAppService;
    private ComunicacionesService comunicacionesService;
    private CuentaActivadaNotifier notifier;

    @BeforeEach
    void setUp() {
        usuarioAppService = mock(UsuarioAppService.class);
        comunicacionesService = mock(ComunicacionesService.class);
        notifier = new CuentaActivadaNotifier(usuarioAppService, comunicacionesService);
    }

    private static RolApp rol(String codigo) {
        RolApp rol = new RolApp();
        rol.setCodigo(codigo);
        return rol;
    }

    @Test
    void enviaElCorreoConElEmailElNombreYLosRoles() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(4);
        usuario.setEmail("ana@example.com");

        when(usuarioAppService.obtenerPorId(4)).thenReturn(usuario);
        when(usuarioAppService.obtenerRoles(4)).thenReturn(List.of(rol("FAMILIAR"), rol("ENTRENADOR")));
        when(usuarioAppService.obtenerNombrePersona(4)).thenReturn("Ana López");

        notifier.notificar(4);

        verify(comunicacionesService).enviarCuentaActivada(
                "ana@example.com", "Ana López", List.of("FAMILIAR", "ENTRENADOR"));
    }

    @Test
    void noEnviaNadaSiElUsuarioNoExiste() {
        when(usuarioAppService.obtenerPorId(4)).thenReturn(null);

        notifier.notificar(4);

        verify(comunicacionesService, never()).enviarCuentaActivada(anyString(), any(), any());
    }

    @Test
    void unFalloAlEnviarNoPropagaLaExcepcion() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setEmail("ana@example.com");

        when(usuarioAppService.obtenerPorId(4)).thenReturn(usuario);
        when(usuarioAppService.obtenerRoles(4)).thenReturn(List.of());
        doThrow(new RuntimeException("smtp caído")).when(comunicacionesService)
                .enviarCuentaActivada(anyString(), any(), any());

        notifier.notificar(4);
    }
}
