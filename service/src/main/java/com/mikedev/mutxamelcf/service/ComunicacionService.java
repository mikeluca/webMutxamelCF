package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.Comunicacion;

public interface ComunicacionService {

    Comunicacion crear(
            Comunicacion comunicacion,
            List<Long> equipoIds,
            List<String> categorias,
            Long usuarioId);

    Comunicacion obtenerPorId(Long id);

    List<Comunicacion> obtenerTodas();
    
    void eliminar(
            Long id,
            Long usuarioId);

    List<Comunicacion> obtenerParaUsuario(
            Long usuarioId);
}