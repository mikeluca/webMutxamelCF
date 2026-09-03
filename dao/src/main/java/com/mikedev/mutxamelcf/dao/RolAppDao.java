package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.RolApp;

public interface RolAppDao {

    RolApp obtenerPorCodigo(String codigo);

    RolApp obtenerPorId(int id);

    List<RolApp> obtenerTodos();

    List<RolApp> obtenerPorUsuario(int usuarioAppId);

    void asignarRol(
            int usuarioAppId,
            int rolId);

    void eliminarRol(
            int usuarioAppId,
            int rolId);
}