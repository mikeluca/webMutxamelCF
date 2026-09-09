package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.Comunicacion;

public interface ComunicacionService {

        Comunicacion crear(
                        Comunicacion comunicacion,
                        List<Long> equipoIds,
                        List<String> categorias,
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
}