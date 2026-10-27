package com.mikedev.mutxamelcf.model;

public class CuerpoTecnicoDTO {
	private Long id;
	private String nombre;
	private String apellidos;
	private String categoria;
	private String deporte;
	private String puesto;
	private String equipo;
	private byte[] foto;
	private String fotoBase64;
	/** Equipos de la ficha; el puesto general es {@link #puesto}. */
	private java.util.List<CuerpoTecnicoEquipoDTO> equipos = new java.util.ArrayList<>();

	public CuerpoTecnicoDTO(Long id, String nombre, String apellidos, String categoria, String deporte, String equipo, String puesto,
			byte[] foto) {
		super();
		this.setId(id);
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.categoria = categoria;
		this.deporte = deporte;
		this.setEquipo(equipo);
		this.puesto = puesto;
		this.foto = foto;
	}

	public CuerpoTecnicoDTO() {
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

	public String getPuesto() {
		return puesto;
	}

	public void setPuesto(String puesto) {
		this.puesto = puesto;
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

	public java.util.List<CuerpoTecnicoEquipoDTO> getEquipos() {
		return equipos;
	}

	public void setEquipos(java.util.List<CuerpoTecnicoEquipoDTO> equipos) {
		this.equipos = equipos == null ? new java.util.ArrayList<>() : equipos;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
