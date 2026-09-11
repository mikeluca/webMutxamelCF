package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.NotificacionApp;
import com.mikedev.mutxamelcf.model.NotificacionAppResponse;
import com.mikedev.mutxamelcf.model.NotificacionesNoLeidasResponse;

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

        NotificacionesNoLeidasResponse contarNoLeidas(
                        Long usuarioId);

        boolean puedeRecibir(
                        Long usuarioId,
                        String tipo);

        int contarComunicacionesNoLeidas(Long usuarioId);
}