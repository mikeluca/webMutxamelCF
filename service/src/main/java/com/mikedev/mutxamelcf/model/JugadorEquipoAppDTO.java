package com.mikedev.mutxamelcf.model;

/**
 * SEC-10: proyección mínima de JugadorDTO para GET
 * /api/app/equipos/jugadores. JugadorDTO completo incluye DNI, fecha de
 * nacimiento, nacionalidad, población y la foto duplicada (byte[] foto +
 * fotoBase64); la app de gestión de entrenadores no usa esos datos, así
 * que no hay motivo para exponerlos a través de la API.
 */
public class JugadorEquipoAppDTO {

    private Long id;
    private String nombre;
    private String apellidos;
    private Integer dorsal;
    private String posicion;
    private String fotoBase64;

    public JugadorEquipoAppDTO() {
    }

    public JugadorEquipoAppDTO(Long id, String nombre, String apellidos, Integer dorsal, String posicion,
            String fotoBase64) {
        this.id = id;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.dorsal = dorsal;
        this.posicion = posicion;
        this.fotoBase64 = fotoBase64;
    }

    public static JugadorEquipoAppDTO desde(JugadorDTO jugador) {
        if (jugador == null) {
            return null;
        }
        return new JugadorEquipoAppDTO(
                jugador.getId(),
                jugador.getNombre(),
                jugador.getApellidos(),
                jugador.getDorsal(),
                jugador.getPosicion(),
                jugador.getFotoBase64());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getDorsal() {
        return dorsal;
    }

    public void setDorsal(Integer dorsal) {
        this.dorsal = dorsal;
    }

    public String getPosicion() {
        return posicion;
    }

    public void setPosicion(String posicion) {
        this.posicion = posicion;
    }

    public String getFotoBase64() {
        return fotoBase64;
    }

    public void setFotoBase64(String fotoBase64) {
        this.fotoBase64 = fotoBase64;
    }
}
