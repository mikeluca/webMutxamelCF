package com.mikedev.mutxamelcf.mvc.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;

/**
 * La vista previa de clasificaciones que sube un ADMIN_APP desde la app. El panel web la guarda en la sesión HTTP y
 * confirma esa, sin aceptar datos del navegador; la API de la app no tiene sesión, así que se guarda aquí, en memoria,
 * una por administrador y durante {@link #VIGENCIA}: la nueva sustituye a la anterior y se consume al confirmar.
 * En memoria y por instancia, igual que los limitadores (un solo contenedor).
 */
@Component
public class PrevisualizacionesClasificacionesApp {

    static final Duration VIGENCIA = Duration.ofMinutes(30);

    private record Guardada(PrevisualizacionClasificaciones previa, Instant desde) {
    }

    private final ConcurrentMap<Integer, Guardada> porUsuario = new ConcurrentHashMap<>();
    private final Clock clock;

    public PrevisualizacionesClasificacionesApp(Clock clock) {
        this.clock = clock;
    }

    public void guardar(int usuarioId, PrevisualizacionClasificaciones previa) {
        purgar();
        porUsuario.put(usuarioId, new Guardada(previa, clock.instant()));
    }

    /** La vista previa vigente del administrador, o null si no hay o ha caducado. No la consume. */
    public PrevisualizacionClasificaciones vigente(int usuarioId) {
        Guardada guardada = porUsuario.get(usuarioId);

        if (guardada == null || guardada.desde().plus(VIGENCIA).isBefore(clock.instant())) {
            porUsuario.remove(usuarioId);
            return null;
        }

        return guardada.previa();
    }

    public void descartar(int usuarioId) {
        porUsuario.remove(usuarioId);
    }

    private void purgar() {
        Instant limite = clock.instant().minus(VIGENCIA);
        porUsuario.values().removeIf(g -> g.desde().isBefore(limite));
    }
}
