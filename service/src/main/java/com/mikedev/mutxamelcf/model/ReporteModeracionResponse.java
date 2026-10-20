package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/** Un mensaje reportado, tal como lo ve ADMIN_APP en la cola de moderación. */
public record ReporteModeracionResponse(
        Long id,
        Long comunicacionId,
        LocalDateTime fechaReporte,
        String motivo,
        Persona reportador,
        Autor autor,
        String tituloCopia,
        String contenidoCopia,
        LocalDateTime fechaMensaje,
        String estado,
        Persona resueltoPor,
        LocalDateTime fechaResolucion,
        String notaResolucion,
        int otrosReportesDelAutor) {

    public record Persona(Long id, String nombre) {
    }

    public record Autor(Long id, String nombre, boolean activo) {
    }

    /** Cuerpo opcional de las acciones de moderación. */
    public static class Accion {

        private String nota;
        private Boolean borrarMensaje;

        public Accion() {
        }

        public Accion(String nota, Boolean borrarMensaje) {
            this.nota = nota;
            this.borrarMensaje = borrarMensaje;
        }

        public String getNota() {
            return nota;
        }

        public void setNota(String nota) {
            this.nota = nota;
        }

        public Boolean getBorrarMensaje() {
            return borrarMensaje;
        }

        public void setBorrarMensaje(Boolean borrarMensaje) {
            this.borrarMensaje = borrarMensaje;
        }
    }
}
