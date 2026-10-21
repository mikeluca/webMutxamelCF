package com.mikedev.mutxamelcf.mvc.communication;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.service.UsuarioAppService;
import com.mikedev.mutxamelcf.serviceimpl.LimitadorReenvioActivacion;

/**
 * El único sitio que reenvía una invitación de activación: genera el código nuevo
 * (UsuarioAppService), manda el email y aplica el límite de un reenvío cada 10
 * minutos por usuario. Lo usan la ruta de la app (solo cuentas pendientes, solo
 * ADMIN_APP) y la del panel web, para que no puedan divergir.
 */
@Component
public class ReenvioActivacion {

    /** Email al que se ha reenviado. */
    public record Resultado(String email) {
    }

    /** Se reenvió hace menos de 10 minutos. */
    public static class DemasiadoPronto extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient Duration espera;

        public DemasiadoPronto(Duration espera) {
            super("Ya se reenvió la activación a este usuario hace poco");
            this.espera = espera;
        }

        public Duration getEspera() {
            return espera;
        }

        public long minutosRestantes() {
            return Math.max(1, (espera.toSeconds() + 59) / 60);
        }
    }

    /** El código se generó pero el email no salió. */
    public static class EnvioFallido extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public EnvioFallido() {
            super("No se ha podido enviar el email");
        }
    }

    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;
    private final LimitadorReenvioActivacion limitador;

    public ReenvioActivacion(
            UsuarioAppService usuarioAppService,
            ComunicacionesService comunicacionesService,
            LimitadorReenvioActivacion limitador) {

        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
        this.limitador = limitador;
    }

    /** Panel web: cualquier cuenta no activa y no eliminada (la autorización la da el panel). */
    public Resultado desdePanel(int usuarioAppId) {
        return reenviar(usuarioAppId, () -> usuarioAppService.reenviarInvitacion(usuarioAppId));
    }

    /**
     * Recuperación de contraseña de una cuenta pendiente de activar: la petición no
     * viene de un administrador sino del propio titular, y se aplica el mismo límite.
     */
    public Resultado desdeRecuperacion(int usuarioAppId) {
        return reenviar(usuarioAppId, () -> usuarioAppService.reenviarInvitacion(usuarioAppId));
    }

    /** App: solo un ADMIN_APP y solo cuentas pendientes (lo comprueba UsuarioAppService). */
    public Resultado desdeApp(int solicitanteId, int usuarioAppId) {
        // La autorización va ANTES de reservar el límite: quien no es administrador no
        // puede bloquearle a otro el reenvío durante 10 minutos.
        usuarioAppService.exigirAdminApp(solicitanteId);

        return reenviar(usuarioAppId, () -> usuarioAppService.reenviarActivacionPendiente(solicitanteId, usuarioAppId));
    }

    private Resultado reenviar(int usuarioAppId, Supplier<InvitacionUsuarioApp> generar) {

        // La marca se pone antes de enviar y de forma atómica.
        Duration espera = limitador.reservar(usuarioAppId);

        if (!espera.isZero()) {
            throw new DemasiadoPronto(espera);
        }

        boolean correcto = false;

        try {

            InvitacionUsuarioApp invitacion = generar.get();

            boolean enviado = comunicacionesService.enviarInvitacionApp(
                    invitacion.getEmail(),
                    invitacion.getNombrePersona(),
                    invitacion.getTokenActivacion());

            if (!enviado) {
                throw new EnvioFallido();
            }

            correcto = true;

            return new Resultado(invitacion.getEmail());

        } finally {

            // Si algo falla (validación, email...) no se bloquea el reintento.
            if (!correcto) {
                limitador.liberar(usuarioAppId);
            }
        }
    }
}
