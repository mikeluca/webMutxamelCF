package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;

import java.util.List;

public interface UsuarioAppService {

    UsuarioApp obtenerPorEmail(String email);

    UsuarioApp obtenerPorId(int id);

    UsuarioApp obtenerPorTokenActivacion(String token);

    int crearUsuario(UsuarioApp usuario);

    String generarTokenActivacion(int usuarioId);

    void activarCuenta(String token, String password);

    LoginAppResponse login(String email, String password);

    void actualizarUltimoAcceso(int id);

    List<RolApp> obtenerRoles(int usuarioAppId);

    RolApp obtenerRolPorCodigo(String codigo);

    boolean tieneRol(int usuarioAppId, String codigoRol);

    void asignarRol(int usuarioAppId, int rolId);

    void eliminarRol(int usuarioAppId, int rolId);
}