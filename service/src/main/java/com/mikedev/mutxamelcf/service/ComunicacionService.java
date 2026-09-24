package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ComunicacionResponse;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacionResponse;
import com.mikedev.mutxamelcf.model.MensajeConversacionResponse;

public interface ComunicacionService {

        Comunicacion crear(
                        Comunicacion comunicacion,
                        List<Long> equipoIds,
                        List<String> categorias,
                        List<Long> destinatariosIds,
                        Long usuarioId);

        Comunicacion obtenerPorId(
                        Long id,
                        Long usuarioId);

        List<Comunicacion> obtenerTodas();

        void eliminar(
                        Long id,
                        Long usuarioId);

        List<Comunicacion> obtenerParaUsuario(
                        Long usuarioId);

        boolean puedeVer(
                        Long comunicacionId,
                        Long usuarioId);

        Comunicacion crearPrivada(
                        Comunicacion comunicacion,
                        List<Long> usuariosDestino,
                        Long usuarioId);

        List<Comunicacion> obtenerEnviadasPorUsuario(
                        Long usuarioId);

        List<DestinatarioComunicacionResponse> obtenerDestinatariosDirectos(
                        Long usuarioId);

        /**
         * Avisos de equipo/categoría recibidos por el usuario
         * (pestaña "Recibidas"). No incluye conversaciones privadas.
         */
        List<ComunicacionResponse> listarParaUsuario(
                        Long usuarioId);

        /**
         * Una entrada por cada conversación privada activa del
         * usuario, con el último mensaje y el número de no leídos
         * (pestaña "Conversaciones"), ordenado por fecha descendente.
         */
        List<ComunicacionResponse> listarConversacionesParaUsuario(
                        Long usuarioId);

        /**
         * Avisos de equipo/categoría creados por el usuario (pestaña
         * "Enviadas"). Las conversaciones privadas no se duplican
         * aquí: solo viven en {@link #listarParaUsuario(Long)}.
         */
        List<ComunicacionResponse> listarEnviadasParaUsuario(
                        Long usuarioId);

        /**
         * Una página (ascendente: más antiguo primero) del hilo de la
         * conversación privada entre usuarioId y otroUsuarioId. Valida
         * que otroUsuarioId sea un destinatario permitido para
         * usuarioId, igual que al crear un mensaje nuevo.
         *
         * Sin antesDeId, devuelve los últimos "limite" mensajes (los
         * más recientes). Con antesDeId, devuelve los "limite"
         * mensajes inmediatamente anteriores a ese mensaje (para
         * cargar historial anterior). limite es opcional: si viene
         * null se usa 20; en cualquier caso se acota entre 1 y 50.
         */
        List<MensajeConversacionResponse> obtenerConversacionPagina(
                        Long usuarioId,
                        Long otroUsuarioId,
                        Long antesDeId,
                        Integer limite);

        /**
         * Marca como leídos, de golpe, todos los mensajes de la
         * conversación privada con otroUsuarioId dirigidos a
         * usuarioId.
         */
        void marcarConversacionLeida(
                        Long usuarioId,
                        Long otroUsuarioId);
}