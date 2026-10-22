package com.mikedev.mutxamelcf.mvc.communication;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.InvitacionAppRequest;
import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.model.InvitarUsuarioAppRequest;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * El único sitio que invita a un usuario a la app: crea la cuenta con sus vínculos y roles
 * (UsuarioAppService.invitarUsuario) y manda el email de invitación con el código de
 * activación. Lo usan el panel web (SUPER) y la app (ADMIN_APP, con su propia autorización
 * en el servicio), para que no puedan divergir.
 */
@Component
public class InvitacionUsuario {

    /** email: al que se ha invitado; enviado: false si la cuenta se creó pero el email no salió. */
    public record Resultado(String email, boolean enviado) {
    }

    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;

    public InvitacionUsuario(UsuarioAppService usuarioAppService, ComunicacionesService comunicacionesService) {
        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
    }

    /** Panel web: la autorización la da el panel. */
    public Resultado desdePanel(InvitarUsuarioAppRequest request) {
        return enviar(usuarioAppService.invitarUsuario(request));
    }

    /** App: solo ADMIN_APP y nunca el rol ADMIN_APP (lo comprueba UsuarioAppService). */
    public Resultado desdeApp(int solicitanteId, InvitacionAppRequest request) {
        return enviar(usuarioAppService.invitarUsuarioDesdeApp(solicitanteId, request));
    }

    private Resultado enviar(InvitacionUsuarioApp invitacion) {

        boolean enviado = comunicacionesService.enviarInvitacionApp(
                invitacion.getEmail(),
                invitacion.getNombrePersona(),
                invitacion.getTokenActivacion());

        return new Resultado(invitacion.getEmail(), enviado);
    }
}
