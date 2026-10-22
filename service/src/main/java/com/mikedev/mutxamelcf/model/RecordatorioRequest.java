package com.mikedev.mutxamelcf.model;

/** Qué recordar al entrenador: la convocatoria o el resultado de un partido. */
public class RecordatorioRequest {

    private Long partidoId;

    /** "CONVOCATORIA" o "RESULTADO". */
    private String tipo;

    public RecordatorioRequest() {
    }

    public Long getPartidoId() {
        return partidoId;
    }

    public void setPartidoId(Long partidoId) {
        this.partidoId = partidoId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
