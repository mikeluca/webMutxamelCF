package com.mikedev.mutxamelcf.serviceimpl;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.PreferenciasNotificacionDao;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacion;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionRequest;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionResponse;
import com.mikedev.mutxamelcf.service.PreferenciasNotificacionService;

@Service
public class PreferenciasNotificacionServiceImpl
                implements PreferenciasNotificacionService {

        private final PreferenciasNotificacionDao preferenciasDao;

        public PreferenciasNotificacionServiceImpl(
                        PreferenciasNotificacionDao preferenciasDao) {

                this.preferenciasDao = preferenciasDao;
        }

        @Override
        public PreferenciasNotificacionResponse obtenerPorUsuario(
                        Long usuarioId) {

                if (usuarioId == null) {
                        throw new IllegalArgumentException(
                                        "El usuario es obligatorio");
                }

                PreferenciasNotificacion preferencias = obtenerPreferencias(usuarioId);

                return convertirAResponse(preferencias);
        }

        @Override
        public PreferenciasNotificacionResponse actualizar(
                        Long usuarioId,
                        PreferenciasNotificacionRequest request) {

                if (usuarioId == null) {
                        throw new IllegalArgumentException(
                                        "El usuario es obligatorio");
                }

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "Las preferencias son obligatorias");
                }

                PreferenciasNotificacion preferencias = obtenerPreferencias(usuarioId);

                preferencias.setNotificacionesActivadas(
                                request.isNotificacionesActivadas()
                                                ? 1
                                                : 0);

                preferencias.setNoticiasActivadas(
                                request.isNoticiasActivadas()
                                                ? 1
                                                : 0);

                preferencias.setComunicacionesActivadas(1);

                preferencias.setMensajesActivados(
                                request.isMensajesActivados()
                                                ? 1
                                                : 0);

                preferencias.setResultadosActivados(
                                request.isResultadosActivados()
                                                ? 1
                                                : 0);

                preferenciasDao.actualizar(preferencias);

                return convertirAResponse(preferencias);
        }

        @Override
        public boolean puedeRecibir(
                        Long usuarioId,
                        String tipo) {

                if (usuarioId == null || tipo == null) {
                        return false;
                }

                PreferenciasNotificacion preferencias = obtenerPreferencias(usuarioId);

                /*
                 * La preferencia general tiene prioridad.
                 */
                if (!estaActivada(
                                preferencias.getNotificacionesActivadas())) {

                        return false;
                }

                String tipoNormalizado = tipo.trim().toUpperCase();

                switch (tipoNormalizado) {

                        case "NOTICIA":
                        case "NOTICIAS":
                                return estaActivada(
                                                preferencias.getNoticiasActivadas());

                        case "COMUNICACION":
                        case "COMUNICACIONES":
                                return estaActivada(
                                                preferencias.getComunicacionesActivadas());

                        case "MENSAJE":
                        case "MENSAJES":
                                return estaActivada(
                                                preferencias.getMensajesActivados());

                        case "RESULTADO":
                        case "RESULTADOS":
                                return estaActivada(
                                                preferencias.getResultadosActivados());

                        default:
                                return false;
                }
        }

        private PreferenciasNotificacion obtenerPreferencias(
                        Long usuarioId) {

                PreferenciasNotificacion preferencias = preferenciasDao.obtenerPorUsuario(usuarioId);

                /*
                 * Por seguridad, si todavía no existe una fila para
                 * el usuario, creamos las preferencias activadas.
                 */
                if (preferencias == null) {

                        preferencias = new PreferenciasNotificacion();

                        preferencias.setUsuarioAppId(usuarioId);
                        preferencias.setNotificacionesActivadas(1);
                        preferencias.setNoticiasActivadas(1);
                        preferencias.setComunicacionesActivadas(1);
                        preferencias.setMensajesActivados(1);
                        preferencias.setResultadosActivados(1);

                        preferenciasDao.guardar(preferencias);
                }

                return preferencias;
        }

        private boolean estaActivada(Integer valor) {
                return valor != null && valor == 1;
        }

        private PreferenciasNotificacionResponse convertirAResponse(
                        PreferenciasNotificacion preferencias) {

                PreferenciasNotificacionResponse response = new PreferenciasNotificacionResponse();

                response.setUsuarioAppId(
                                preferencias.getUsuarioAppId());

                response.setNotificacionesActivadas(
                                estaActivada(
                                                preferencias.getNotificacionesActivadas()));

                response.setNoticiasActivadas(
                                estaActivada(
                                                preferencias.getNoticiasActivadas()));

                response.setComunicacionesActivadas(
                                estaActivada(
                                                preferencias.getComunicacionesActivadas()));

                response.setMensajesActivados(
                                estaActivada(
                                                preferencias.getMensajesActivados()));

                response.setResultadosActivados(
                                estaActivada(
                                                preferencias.getResultadosActivados()));

                return response;
        }
}