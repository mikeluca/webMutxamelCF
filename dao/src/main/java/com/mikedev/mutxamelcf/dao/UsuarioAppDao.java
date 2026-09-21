package com.mikedev.mutxamelcf.dao;

import java.sql.Timestamp;
import java.util.List;

import com.mikedev.mutxamelcf.model.UsuarioApp;

public interface UsuarioAppDao {

    UsuarioApp obtenerPorEmail(String email);

    UsuarioApp obtenerPorId(int id);

    UsuarioApp obtenerPorTokenActivacion(String tokenActivacion);

    List<UsuarioApp> listarTodos();

    int guardar(UsuarioApp usuario);

    void actualizarPassword(int id, String passwordHash);

    void activarUsuario(int id);

    void desactivarUsuario(int id);

    void eliminar(int id);

    void actualizarUltimoAcceso(int id);

    void actualizarTokenActivacion(int id, String tokenHash, Timestamp expiracion);
}