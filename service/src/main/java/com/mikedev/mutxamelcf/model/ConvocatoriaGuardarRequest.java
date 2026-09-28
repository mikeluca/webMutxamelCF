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
}
