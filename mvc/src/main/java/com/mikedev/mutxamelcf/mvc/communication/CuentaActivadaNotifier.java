package com.mikedev.mutxamelcf.mvc.communication;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Avisa por email a quien acaba de activar su cuenta de la app, tanto si
 * la activa él mismo con su código como si lo hace un administrador desde
 * el panel. Es best-effort: un fallo al enviar nunca debe deshacer ni
 * romper la activación.
 */
@Component
public class CuentaActivadaNotifier {

    private static final Logger logger = LoggerFactory.getLogger(CuentaActivadaNotifier.class);

    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;

    public CuentaActivadaNotifier(
            UsuarioAppService usuarioAppService,
            ComunicacionesService comunicacionesService) {

        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
    }

    public void notificar(int usuarioAppId) {

        try {

            UsuarioApp usuario = usuarioAppService.obtenerPorId(usuarioAppId);

            if (usuario == null || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
                return;
            }

            List<String> roles = usuarioAppService.obtenerRoles(usuarioAppId).stream()
                    .map(RolApp::getCodigo)
                    .toList();

            comunicacionesService.enviarCuentaActivada(
                    usuario.getEmail(),
                    usuarioAppService.obtenerNombrePersona(usuarioAppId),
                    roles);

        } catch (Exception e) {

            // Privacidad: se registra el id, no el email.
            logger.warn("No se ha podido enviar el aviso de cuenta activada: usuarioAppId={}, error={}",
                    usuarioAppId, e.getMessage());
        }
    }
}
