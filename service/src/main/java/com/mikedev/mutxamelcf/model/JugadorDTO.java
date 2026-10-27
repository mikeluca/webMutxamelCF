package com.mikedev.mutxamelcf.model;

public class JugadorDTO {
	private Long id;
	private String nombre;
	private String apellidos;
	private String categoria;
	private String equipo;
	private String deporte;
	private Integer dorsal;
	private String posicion;
	private byte[] foto;
	private String fotoBase64;
	private Long equipoId;
	/** Dato de un menor: solo para el panel y el asistente; no se serializa en JSON ni va a la app. */
	private Integer anioNacimiento;
	private java.time.LocalDate fechaBaja;

	public JugadorDTO(Long id, String nombre, String apellidos, String categoria, String deporte, String equipo, int dorsal, String posicion,
			byte[] foto) {
		super();
		this.setId(id);
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.categoria = categoria;
		this.equipo = equipo;
		this.deporte = deporte;
		this.dorsal = dorsal;
		this.posicion = posicion;
		this.foto = foto;
	}

	public JugadorDTO() {
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

	public String getEquipo() {
		return equipo;
	}

	public void setEquipo(String equipo) {
		this.equipo = equipo;
	}

	public String getFotoBase64() {
		return fotoBase64;
	}

	public void setFotoBase64(String fotoBase64) {
		this.fotoBase64 = fotoBase64;
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

	public Long getEquipoId() {
		return equipoId;
	}

	public void setEquipoId(Long equipoId) {
		this.equipoId = equipoId;
	}

	@com.fasterxml.jackson.annotation.JsonIgnore
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

	public boolean isDeBaja() {
		return fechaBaja != null;
	}

}
