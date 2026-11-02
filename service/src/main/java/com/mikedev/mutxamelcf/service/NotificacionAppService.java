package com.mikedev.mutxamelcf.service;

import java.util.List;
import java.util.function.Function;

import com.mikedev.mutxamelcf.model.NotificacionApp;
import com.mikedev.mutxamelcf.model.NotificacionAppResponse;
import com.mikedev.mutxamelcf.model.NotificacionesNoLeidasResponse;
import com.mikedev.mutxamelcf.model.TextoDifusion;

public interface NotificacionAppService {

        NotificacionApp crear(
                        Long usuarioId,
                        String tipo,
                        String titulo,
                        String mensaje,
                        Long referenciaId);

        List<NotificacionAppResponse> obtenerPorUsuario(
                        Long usuarioId);

        List<NotificacionAppResponse> obtenerNoLeidas(
                        Long usuarioId);

        void marcarComoLeida(
                        Long id,
                        Long usuarioId);

        void marcarTodasComoLeidas(
                        Long usuarioId);

        void marcarLeidasPorReferencias(
                        Long usuarioId,
                        List<Long> referenciaIds);

        NotificacionesNoLeidasResponse contarNoLeidas(
                        Long usuarioId);

        boolean puedeRecibir(
                        Long usuarioId,
                        String tipo);

        int contarComunicacionesNoLeidas(Long usuarioId);

        /**
         * Crea la notificación interna y manda el push a todas las cuentas
         * activas de la app cuyas preferencias permitan recibir avisos de
         * este {@code tipo} (p. ej. "NOTICIA" o "RESULTADO"). A diferencia
         * de una comunicación, no hay destinatarios que elegir: es siempre
         * todo el mundo que no lo haya desactivado. Cada cuenta recibe el
         * texto que genera {@code texto} para su idioma.
         */
        void difundirATodos(
                        String tipo,
                        Function<String, TextoDifusion> texto,
                        Long referenciaId);
}
