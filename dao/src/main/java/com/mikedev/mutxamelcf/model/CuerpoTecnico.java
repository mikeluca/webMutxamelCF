package com.mikedev.mutxamelcf.model;

public class CuerpoTecnico {
	private Long id;
	private String nombre;
	private String apellidos;
	private String categoria;
	private String deporte;
	private String equipo;
	private String puesto;
	private byte[] foto;

	public CuerpoTecnico(Long id, String nombre, String apellidos, String categoria, String deporte, String equipo, String puesto, byte[] foto) {
		super();
		this.setId(id);
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.categoria = categoria;
		this.setDeporte(deporte);
		this.setEquipo(equipo);
		this.puesto = puesto;
		this.foto = foto;
	}

	public CuerpoTecnico() {
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

}
