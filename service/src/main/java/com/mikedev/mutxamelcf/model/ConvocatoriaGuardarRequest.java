package com.mikedev.mutxamelcf.model;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public class ConvocatoriaGuardarRequest {

    /*
     * La convocatoria ya no se crea con rival/campo/fecha/hora escritos a
     * mano: se crea SIEMPRE a partir de un partido ya existente. El
     * equipoId ya no se pide en la petición: se deriva del propio
     * Partido (partido.getEquipoId()), que es la fuente única de verdad,
     * evitando así que se pueda enviar un equipoId que no case con el
     * partido elegido.
     */
    @NotNull(message = "El partido es obligatorio")
    private Long partidoId;

    @NotBlank(message = "La hora de convocatoria es obligatoria")
    private String horaConvocatoria;

    @NotBlank(message = "El lugar de convocatoria es obligatorio")
    private String lugarConvocatoria;

    @NotEmpty(message = "Debes seleccionar al menos un jugador")
    private List<Long> jugadoresIds;

    /**
     * Jugadores de otros equipos invitados a esta convocatoria (docs/convocatoria-invitados.md). Con null no se toca
     * ninguno; con una lista, la convocatoria queda con exactamente esos invitados.
     */
    private List<Long> invitadosIds;

    /**
     * Equipaje con el que van vestidos al partido y el de reserva (mochila), por ID de EQUIPACIONES; null = sin indicar.
     * En el PUT solo se aplican si equipajeIndicado es true: así una versión de la app que no conoce el equipaje no lo
     * borra al editar la convocatoria.
     */
    private Long equipacionId;
    private Long equipacionMochilaId;
    private Boolean equipajeIndicado;

    public ConvocatoriaGuardarRequest() {
    }

    public Long getPartidoId() {
        return partidoId;
    }

    public void setPartidoId(Long partidoId) {
        this.partidoId = partidoId;
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

    public List<Long> getJugadoresIds() {
        return jugadoresIds;
    }

    public void setJugadoresIds(List<Long> jugadoresIds) {
        this.jugadoresIds = jugadoresIds;
    }

    public Long getEquipacionId() {
        return equipacionId;
    }

    public void setEquipacionId(Long equipacionId) {
        this.equipacionId = equipacionId;
    }

    public Long getEquipacionMochilaId() {
        return equipacionMochilaId;
    }

    public void setEquipacionMochilaId(Long equipacionMochilaId) {
        this.equipacionMochilaId = equipacionMochilaId;
    }

    public boolean isEquipajeIndicado() {
        return Boolean.TRUE.equals(equipajeIndicado);
    }

    public void setEquipajeIndicado(Boolean equipajeIndicado) {
        this.equipajeIndicado = equipajeIndicado;
    }

    public List<Long> getInvitadosIds() {
        return invitadosIds;
    }

    public void setInvitadosIds(List<Long> invitadosIds) {
        this.invitadosIds = invitadosIds;
    }
}
