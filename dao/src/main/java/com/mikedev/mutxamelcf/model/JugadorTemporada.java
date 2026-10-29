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
	private boolean pendienteRevision;
	private String estado = "CONFIRMADO";
	private String continuidad = "PENDIENTE";
	private LocalDateTime fechaContinuidad;
	private Long usuarioAppContinuidadId;
	private boolean equipoDefinitivo = true;
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

	/** CONFIRMADO, PRUEBA o BAJA (JUGADOR_TEMPORADA.ESTADO): la única fuente de verdad de la baja. */
	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	/** PENDIENTE, SIGUE, NO_SIGUE o DUDA: la respuesta de la familia en el borrador. */
	public String getContinuidad() {
		return continuidad;
	}

	public void setContinuidad(String continuidad) {
		this.continuidad = continuidad;
	}

	public LocalDateTime getFechaContinuidad() {
		return fechaContinuidad;
	}

	public void setFechaContinuidad(LocalDateTime fechaContinuidad) {
		this.fechaContinuidad = fechaContinuidad;
	}

	public Long getUsuarioAppContinuidadId() {
		return usuarioAppContinuidadId;
	}

	public void setUsuarioAppContinuidadId(Long usuarioAppContinuidadId) {
		this.usuarioAppContinuidadId = usuarioAppContinuidadId;
	}

	public boolean isEquipoDefinitivo() {
		return equipoDefinitivo;
	}

	public void setEquipoDefinitivo(boolean equipoDefinitivo) {
		this.equipoDefinitivo = equipoDefinitivo;
	}

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

	/** ESTADO = 'BAJA'. */
	public boolean isBaja() {
		return "BAJA".equals(estado);
	}

	/** Baja: ESTADO = 'BAJA'. Quitarla solo cambia el ESTADO si era 'BAJA' (pasa a 'CONFIRMADO'); una PRUEBA se conserva. */
	public void setBaja(boolean baja) {
		if (baja) {
			this.estado = "BAJA";
		} else if ("BAJA".equals(estado)) {
			this.estado = "CONFIRMADO";
		}
	}

	/**
	 * Marcada a revisar o, si se queda (CONFIRMADO), con la continuidad sin contestar (PENDIENTE) o dudosa (DUDA).
	 */
	public boolean requiereRevision() {
		return pendienteRevision
				|| ("CONFIRMADO".equals(estado) && ("PENDIENTE".equals(continuidad) || "DUDA".equals(continuidad)));
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
