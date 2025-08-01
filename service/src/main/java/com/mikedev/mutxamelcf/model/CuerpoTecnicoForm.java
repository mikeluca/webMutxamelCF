package com.mikedev.mutxamelcf.model;

import org.springframework.web.multipart.MultipartFile;

public class CuerpoTecnicoForm {
	private String nombre;
	private String apellidos;
	private Long equipo;
	private String puesto;
	private MultipartFile foto;

	public CuerpoTecnicoForm(String nombre, String apellidos, Long equipo, String puesto, MultipartFile foto) {
		super();
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.equipo = equipo;
		this.puesto = puesto;
		this.setFoto(foto);
	}

	public CuerpoTecnicoForm() {
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

	public String getPuesto() {
		return puesto;
	}

	public void setPuesto(String puesto) {
		this.puesto = puesto;
	}

	public Long getEquipo() {
		return equipo;
	}

	public void setEquipo(Long equipo) {
		this.equipo = equipo;
	}

	public MultipartFile getFoto() {
		return foto;
	}

	public void setFoto(MultipartFile foto) {
		this.foto = foto;
	}

}
