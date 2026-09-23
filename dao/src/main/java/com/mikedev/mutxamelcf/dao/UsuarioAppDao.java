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

    /**
     * Fija un nuevo código/token de activación y reinicia el
     * contador de intentos fallidos a 0.
     */
    void actualizarTokenActivacion(int id, String tokenHash, Timestamp expiracion);

    /**
     * Incrementa en 1 el contador de intentos fallidos de activación.
     */
    void incrementarIntentosActivacion(int id);

    /**
     * Invalida el código/token de activación pendiente (por ejemplo,
     * al agotar el número máximo de intentos), sin desactivar ni
     * tocar el resto de la cuenta. Requiere generar uno nuevo.
     */
    void invalidarTokenActivacion(int id);
}