package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Convocatoria {

    private Long id;
    private Long equipoId;
    private Long partidoId;

    /*
     * Campos NO persistidos en la tabla CONVOCATORIAS: la convocatoria ya
     * no guarda su propio rival/campo/fecha/hora, sino que siempre se
     * apoya en el PARTIDO al que apunta PARTIDO_ID (single source of
     * truth). Estos campos se rellenan en tiempo de lectura:
     *   - ConvocatoriaDaoImpl los rellena haciendo JOIN con PARTIDOS en
     *     obtenerPorId()/obtenerPorEquipo().
     *   - ConvocatoriaServiceImpl los rellena "a mano" justo despues de
     *     crear()/actualizar(), a partir del Partido ya cargado en
     *     memoria, para poder construir la respuesta y las notificaciones
     *     sin tener que volver a consultar la base de datos.
     * guardar()/actualizar() en el DAO los ignoran por completo.
     */
    private String rival;
    private String campo;
    private LocalDate fechaPartido;
    private String horaPartido;

    private String horaConvocatoria;
    private String lugarConvocatoria;

    private Long usuarioEntrenadorId;
    private LocalDateTime fechaCreacion;

    public Convocatoria() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEquipoId() {
        return equipoId;
    }

    public void setEquipoId(Long equipoId) {
        this.equipoId = equipoId;
    }

    public Long getPartidoId() {
        return partidoId;
    }

    public void setPartidoId(Long partidoId) {
        this.partidoId = partidoId;
    }

    public String getRival() {
        return rival;
    }

    public void setRival(String rival) {
        this.rival = rival;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public LocalDate getFechaPartido() {
        return fechaPartido;
    }

    public void setFechaPartido(LocalDate fechaPartido) {
        this.fechaPartido = fechaPartido;
    }

    public String getHoraPartido() {
        return horaPartido;
    }

    public void setHoraPartido(String horaPartido) {
        this.horaPartido = horaPartido;
    }

    public String getHoraConvocatoria() {
        return horaConvocatoria;
    }

    public void setHoraConvocatoria(String horaConvocatoria) {
        this.horaConvocatoria = horaConvocatoria;
    }

    public String getLugarConvocatoria() {
        return lugarConvocatoria;
    }

    public void setLugarConvocatoria(String lugarConvocatoria) {
        this.lugarConvocatoria = lugarConvocatoria;
    }

    public Long getUsuarioEntrenadorId() {
        return usuarioEntrenadorId;
    }

    public void setUsuarioEntrenadorId(Long usuarioEntrenadorId) {
        this.usuarioEntrenadorId = usuarioEntrenadorId;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
