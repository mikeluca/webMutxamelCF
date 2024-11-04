package com.mikedev.mutxamelcf.model;

public class EquipoDTO {

	private Long id;
	private String categoria;
	private String grupo;
	private String orden;
	private String nombre;
	private String deporte;

	public EquipoDTO() {
		super();
	}

	public EquipoDTO(Long id, String categoria, String grupo, String orden, String nombre, String deporte) {
		super();
		this.id = id;
		this.categoria = categoria;
		this.grupo = grupo;
		this.orden = orden;
		this.nombre = nombre;
		this.deporte = deporte;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getGrupo() {
		return grupo;
	}

	public void setGrupo(String grupo) {
		this.grupo = grupo;
	}

	public String getOrden() {
		return orden;
	}

	public void setOrden(String orden) {
		this.orden = orden;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

}
