package com.mikedev.mutxamelcf.mvc.communication;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.UsuarioAppService;
import com.mikedev.mutxamelcf.serviceimpl.LimitadorRecuperacionContrasena;

import jakarta.annotation.PreDestroy;

/**
 * Orquestra "¿Has olvidado tu contraseña?" para que la respuesta de /recuperar no
 * dependa de si el email tiene cuenta:
 * <ul>
 * <li>cuenta activa y no eliminada: código de recuperación por email;</li>
 * <li>cuenta pendiente de activar (nunca activada): se reenvía la invitación con el
 * límite de {@link ReenvioActivacion};</li>
 * <li>cuenta desactivada, eliminada o inexistente: no se hace nada.</li>
 * </ul>
 * Todo el trabajo (consulta, escritura y SMTP) va en un hilo aparte tras comprobar los
 * límites, así que la petición tarda lo mismo en los cuatro casos. Los emails de
 * confirmación también salen en segundo plano y son best-effort.
 */
@Component
public class RecuperacionContrasena {

    private static final Logger logger = LoggerFactory.getLogger(RecuperacionContrasena.class);

    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;
    private final ReenvioActivacion reenvioActivacion;
    private final LimitadorRecuperacionContrasena limitador;
    private final Executor ejecutor;
    private final ExecutorService propio;

    @Autowired
    public RecuperacionContrasena(
            UsuarioAppService usuarioAppService,
            ComunicacionesService comunicacionesService,
            ReenvioActivacion reenvioActivacion,
            LimitadorRecuperacionContrasena limitador) {

        this(usuarioAppService, comunicacionesService, reenvioActivacion, limitador, null);
    }

    /** Para los tests: un {@code Executor} que ejecuta en el mismo hilo. */
    RecuperacionContrasena(
            UsuarioAppService usuarioAppService,
            ComunicacionesService comunicacionesService,
            ReenvioActivacion reenvioActivacion,
            LimitadorRecuperacionContrasena limitador,
            Executor ejecutor) {

        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
        this.reenvioActivacion = reenvioActivacion;
        this.limitador = limitador;

        if (ejecutor == null) {
            this.propio = Executors.newSingleThreadExecutor(tarea -> {
                Thread hilo = new Thread(tarea, "recuperacion-contrasena");
                hilo.setDaemon(true);
                return hilo;
            });
            this.ejecutor = propio;
        } else {
            this.propio = null;
            this.ejecutor = ejecutor;
        }
    }

    @PreDestroy
    void cerrar() {
        if (propio != null) {
            propio.shutdown();
        }
    }

    /** Atiende una petición de /recuperar. Nunca lanza ni revela si el email tiene cuenta. */
    public void solicitar(String email, String ip) {

        String normalizado = email == null ? "" : email.trim().toLowerCase();

        if (!limitador.permitir(normalizado, ip)) {
            logger.warn("Recuperación de contraseña limitada: email={}, ip={}", enmascarar(normalizado), ip);
            return;
        }

        ejecutar(() -> procesar(normalizado));
    }

    /** Email de "Tu contraseña se ha cambiado"; se llama cuando la transacción ya se ha confirmado. */
    public void avisarContrasenaCambiada(int usuarioAppId) {

        ejecutar(() -> {

            try {

                UsuarioApp usuario = usuarioAppService.obtenerPorId(usuarioAppId);

                if (usuario == null || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
                    return;
                }

                comunicacionesService.enviarContrasenaCambiada(
                        usuario.getEmail(),
                        usuarioAppService.obtenerNombreDePila(usuarioAppId));

            } catch (Exception e) {
                logger.warn("No se ha podido enviar el aviso de contraseña cambiada: usuarioAppId={}, error={}",
                        usuarioAppId, e.getMessage());
            }
        });
    }

    private void ejecutar(Runnable tarea) {

        try {
            ejecutor.execute(tarea);
        } catch (RuntimeException e) {
            logger.warn("No se ha podido lanzar la tarea de recuperación de contraseña: {}", e.getMessage());
        }
    }

    private void procesar(String email) {

        try {

            UsuarioApp usuario = usuarioAppService.obtenerPorEmail(email);

            if (usuario == null || usuario.isEliminada()) {
                return;
            }

            if (usuario.isActivo()) {

                String codigo = usuarioAppService.generarCodigoRecuperacion(usuario.getId());

                boolean enviado = comunicacionesService.enviarCodigoRecuperacion(
                        usuario.getEmail(),
                        usuarioAppService.obtenerNombreDePila(usuario.getId()),
                        codigo,
                        UsuarioAppService.MINUTOS_VALIDEZ_RECUPERACION);

                if (!enviado) {
                    logger.warn("No se ha podido enviar el código de recuperación: usuarioAppId={}", usuario.getId());
                }

                return;
            }

            // Inactiva: pendiente de activar solo si nunca se activó; si no, la
            // desactivó un administrador y no se le ofrece nada.
            if (usuario.getFechaActivacion() == null) {
                reenvioActivacion.desdeRecuperacion(usuario.getId());
            }

        } catch (ReenvioActivacion.DemasiadoPronto e) {
            logger.warn("Recuperación de una cuenta pendiente: invitación reenviada hace poco ({} min restantes)",
                    e.minutosRestantes());
        } catch (Exception e) {
            logger.warn("Error en la recuperación de contraseña: {}", e.getMessage());
        }
    }

    /** "ana@dominio.es" -> "a***@dominio.es": basta para localizar el caso sin guardar el email completo. */
    static String enmascarar(String email) {

        if (email == null || email.isBlank()) {
            return "";
        }

        int arroba = email.indexOf('@');

        if (arroba <= 0) {
            return email.charAt(0) + "***";
        }

        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
