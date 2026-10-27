package com.mikedev.mutxamelcf.model;

public class Jugador {
	private Long id;
	private String nombre;
	private String apellidos;
	private String categoria;
	private String deporte;
	private String equipo;
	/** JUGADORES.EQUIPO_ID: el enlace con EQUIPO (nulo si el jugador no tiene equipo o está de baja). */
	private Long equipoId;
	private Integer dorsal;
	private String posicion;
	private byte[] foto;
	/** Dato de un menor: solo lo usan el panel y el asistente de temporada, nunca la app. */
	private Integer anioNacimiento;
	/** Nula mientras el jugador forma parte de la plantilla actual. */
	private java.time.LocalDate fechaBaja;

	public Jugador(Long id, String nombre, String apellidos, String categoria, String deporte, String equipo, int dorsal, String posicion,
			byte[] foto) {
		super();
		this.setId(id);
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.categoria = categoria;
		this.setDeporte(deporte);
		this.equipo = equipo;
		this.dorsal = dorsal;
		this.posicion = posicion;
		this.foto = foto;
	}

	public Jugador() {
		super();
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

	public String getPosicion() {
		return posicion;
	}

	public void setPosicion(String posicion) {
		this.posicion = posicion;
	}

	public byte[] getFoto() {
		return foto;
	}

	public void setFoto(byte[] foto) {
		this.foto = foto;
	}

	public Long getEquipoId() {
		return equipoId;
	}

	public void setEquipoId(Long equipoId) {
		this.equipoId = equipoId;
	}

	public String getEquipo() {
		return equipo;
	}

	public void setEquipo(String equipo) {
		this.equipo = equipo;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getAnioNacimiento() {
		return anioNacimiento;
	}

	public void setAnioNacimiento(Integer anioNacimiento) {
		this.anioNacimiento = anioNacimiento;
	}

	public java.time.LocalDate getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(java.time.LocalDate fechaBaja) {
		this.fechaBaja = fechaBaja;
	}

	public boolean estaDeBaja() {
		return fechaBaja != null;
	}

}
