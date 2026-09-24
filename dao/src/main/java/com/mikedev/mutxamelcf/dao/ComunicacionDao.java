package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacion;

public interface ComunicacionDao {

        Long guardar(Comunicacion comunicacion);

        Comunicacion obtenerPorId(Long id);

        List<Comunicacion> obtenerTodas();

        List<Comunicacion> obtenerPorEquipo(Long equipoId);

        List<Comunicacion> obtenerPorCategoria(String categoria);

        void guardarEquipo(Long comunicacionId, Long equipoId);

        void guardarCategoria(Long comunicacionId, String categoria);

        void eliminar(Long id);

        boolean entrenadorPuedeGestionarEquipo(
                        Long usuarioAppId,
                        Long equipoId);

        boolean existeEquipo(Long equipoId);

        boolean existeCategoria(String categoria);

        List<Long> obtenerEquiposDeEntrenador(Long usuarioAppId);

        List<Long> obtenerEquiposDeJugador(Long usuarioAppId);

        List<Long> obtenerEquiposDeFamiliar(Long usuarioAppId);

        List<Comunicacion> obtenerParaUsuario(Long usuarioAppId);

        List<Comunicacion> obtenerPorEquipos(List<Long> equipoIds);

        List<Comunicacion> obtenerPorEquiposYCategorias(List<Long> equipoIds);

        List<Long> obtenerUsuariosDelEquipo(Long equipoId);

        List<Long> obtenerUsuariosDeCategoria(String categoria);

        void guardarUsuario(
                        Long comunicacionId,
                        Long usuarioAppId);

        List<Comunicacion> obtenerPorUsuarioDirecto(
                        Long usuarioAppId);

        boolean usuarioPuedeVerDirectamente(
                        Long comunicacionId,
                        Long usuarioAppId);

        List<Long> obtenerDestinatariosDirectosPermitidos(
                        Long usuarioAppId);

        List<Comunicacion> obtenerEnviadasPorUsuario(
                        Long usuarioAppId);

        List<DestinatarioComunicacion> obtenerDestinatariosDirectos(
                        Long usuarioAppId);

        /**
         * Hilo completo de mensajes privados (TIPO='PRIVADA') entre
         * usuarioId y otroUsuarioId, en cualquiera de los dos
         * sentidos, ordenado del más antiguo al más reciente.
         */
        List<Comunicacion> obtenerConversacion(
                        Long usuarioId,
                        Long otroUsuarioId);

        /**
         * Una página del hilo de mensajes privados entre usuarioId y
         * otroUsuarioId (mismo criterio de participación que
         * {@link #obtenerConversacion}), devuelta en orden
         * DESCENDENTE (más reciente primero). Si antesDeId no es
         * null, solo devuelve mensajes con ID menor que antesDeId
         * (para pedir la página anterior a un cursor). limite acota
         * el número máximo de mensajes devueltos.
         */
        List<Comunicacion> obtenerConversacionPagina(
                        Long usuarioId,
                        Long otroUsuarioId,
                        Long antesDeId,
                        int limite);

        /**
         * Todos los mensajes privados (TIPO='PRIVADA') en los que
         * usuarioId participa (como autor o como destinatario), con
         * {@link Comunicacion#getContraparteId()} relleno, ordenados
         * del más reciente al más antiguo.
         */
        List<Comunicacion> obtenerPrivadasDeUsuario(
                        Long usuarioId);

}