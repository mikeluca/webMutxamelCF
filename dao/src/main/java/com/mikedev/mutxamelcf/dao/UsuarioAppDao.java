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

    /**
     * Anonimiza la cuenta (borrado a petición del titular): sustituye
     * el email, vacía la contraseña y los tokens, desactiva la cuenta y
     * fija FECHA_ELIMINACION. La fila se conserva porque otras tablas
     * la referencian por clave foránea. También vacía NOMBRE y APELLIDOS.
     */
    void anonimizar(int id, String emailAnonimo);

    /**
     * Fija el nombre propio de la cuenta (el que se usa cuando no tiene
     * ficha vinculada). Admite null para vaciarlo.
     */
    void actualizarNombre(int id, String nombre, String apellidos);

    void eliminar(int id);

    void actualizarUltimoAcceso(int id);

    /**
     * Fija un nuevo código/token de activación y reinicia el
     * contador de intentos fallidos a 0.
     */
    void actualizarTokenActivacion(int id, String tokenHash, Timestamp expiracion);

    /**
     * Consume atómicamente un intento de activación: incrementa
     * INTENTOS_ACTIVACION en 1 SOLO SI la cuenta sigue siendo
     * candidata a activarse (inactiva, con token pendiente, por
     * debajo del máximo de intentos y sin haber caducado). Devuelve
     * el número de filas afectadas (0 o 1).
     *
     * Al comprobar la condición y escribir en la misma sentencia SQL,
     * dos peticiones concurrentes para la misma cuenta no pueden leer
     * el mismo contador "antiguo" y colarse ambas por debajo del
     * límite (SEC-02).
     */
    int consumirIntentoActivacion(int id, int maxIntentos);
}