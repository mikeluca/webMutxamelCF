package com.mikedev.mutxamelcf.model;

/**
 * Petición de SUPER para cambiar el nombre propio de una cuenta de la
 * app. Solo tiene efecto visible en cuentas sin ficha vinculada; la
 * validación (nombre obligatorio o no) depende de eso y se hace en el
 * servicio.
 */
public class ActualizarNombreUsuarioAppRequest {

    private String nombre;

    private String apellidos;

    public ActualizarNombreUsuarioAppRequest() {
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }
}
