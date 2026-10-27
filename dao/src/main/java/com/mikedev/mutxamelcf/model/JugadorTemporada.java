package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Plantilla de un jugador en una temporada. En la temporada activa refleja lo que hay en JUGADORES; en una que
 * todavía no lo está es el borrador del asistente "Nueva temporada". EQUIPO_NOMBRE y CATEGORIA son una copia
 * para que el historial sobreviva a un cambio de nombre del equipo.
 */
public class JugadorTemporada {

	private Long jugadorId;
	private Long temporadaId;
	private Long equipoId;
	private String equipoNombre;
	private String categoria;
	private Integer dorsal;
	private boolean baja;
	private boolean pendienteRevision;
	private String nota;
	private LocalDateTime fechaCreacion;
	private LocalDateTime fechaActualizacion;

	// Solo en las lecturas que unen JUGADORES / TEMPORADAS (no se guardan).
	private String jugadorNombre;
	private String jugadorApellidos;
	private Integer anioNacimiento;
	private Long equipoActualId;
	private String equipoActualNombre;
	private String temporadaNombre;
	private boolean jugadorDeBaja;

	public boolean isJugadorDeBaja() {
		return jugadorDeBaja;
	}

	public void setJugadorDeBaja(boolean jugadorDeBaja) {
		this.jugadorDeBaja = jugadorDeBaja;
	}

	public Long getJugadorId() {
		return jugadorId;
	}

	public void setJugadorId(Long jugadorId) {
		this.jugadorId = jugadorId;
	}

	public Long getTemporadaId() {
		return temporadaId;
	}

	public void setTemporadaId(Long temporadaId) {
		this.temporadaId = temporadaId;
	}

	public Long getEquipoId() {
		return equipoId;
	}

	public void setEquipoId(Long equipoId) {
		this.equipoId = equipoId;
	}

	public String getEquipoNombre() {
		return equipoNombre;
	}

	public void setEquipoNombre(String equipoNombre) {
		this.equipoNombre = equipoNombre;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public Integer getDorsal() {
		return dorsal;
	}

	public void setDorsal(Integer dorsal) {
		this.dorsal = dorsal;
	}

	public boolean isBaja() {
		return baja;
	}

	public void setBaja(boolean baja) {
		this.baja = baja;
	}

	public boolean isPendienteRevision() {
		return pendienteRevision;
	}

	public void setPendienteRevision(boolean pendienteRevision) {
		this.pendienteRevision = pendienteRevision;
	}

	public String getNota() {
		return nota;
	}

	public void setNota(String nota) {
		this.nota = nota;
	}

	public LocalDateTime getFechaCreacion() {
		return fechaCreacion;
	}

	public void setFechaCreacion(LocalDateTime fechaCreacion) {
		this.fechaCreacion = fechaCreacion;
	}

	public LocalDateTime getFechaActualizacion() {
		return fechaActualizacion;
	}

	public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
		this.fechaActualizacion = fechaActualizacion;
	}

	public String getJugadorNombre() {
		return jugadorNombre;
	}

	public void setJugadorNombre(String jugadorNombre) {
		this.jugadorNombre = jugadorNombre;
	}

	public String getJugadorApellidos() {
		return jugadorApellidos;
	}

	public void setJugadorApellidos(String jugadorApellidos) {
		this.jugadorApellidos = jugadorApellidos;
	}

	public Integer getAnioNacimiento() {
		return anioNacimiento;
	}

	public void setAnioNacimiento(Integer anioNacimiento) {
		this.anioNacimiento = anioNacimiento;
	}

	public Long getEquipoActualId() {
		return equipoActualId;
	}

	public void setEquipoActualId(Long equipoActualId) {
		this.equipoActualId = equipoActualId;
	}

	public String getEquipoActualNombre() {
		return equipoActualNombre;
	}

	public void setEquipoActualNombre(String equipoActualNombre) {
		this.equipoActualNombre = equipoActualNombre;
	}

	public String getTemporadaNombre() {
		return temporadaNombre;
	}

	public void setTemporadaNombre(String temporadaNombre) {
		this.temporadaNombre = temporadaNombre;
	}
}
