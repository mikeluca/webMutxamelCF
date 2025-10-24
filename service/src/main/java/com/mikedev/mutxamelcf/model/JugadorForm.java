package com.mikedev.mutxamelcf.model;

import org.springframework.web.multipart.MultipartFile;

public class JugadorForm {
	private Long id;
	private String nombre;
	private String apellidos;
	private Long equipo;
	private Integer dorsal;
	private String posicion;
	private MultipartFile foto;

	public JugadorForm(Long id, String nombre, String apellidos, Long equipo, int dorsal, String posicion, MultipartFile foto) {
		super();
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.equipo = equipo;
		this.dorsal = dorsal;
		this.posicion = posicion;
		this.setFoto(foto);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public JugadorForm() {
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
