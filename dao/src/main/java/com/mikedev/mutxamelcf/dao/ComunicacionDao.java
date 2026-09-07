package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Comunicacion;

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
        
}