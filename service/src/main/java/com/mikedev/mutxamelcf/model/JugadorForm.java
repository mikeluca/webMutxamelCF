package com.mikedev.mutxamelcf.model;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class JugadorForm {

	private static final String PATRON_NOMBRE = "^[\\p{L} .'-]+$";

	private Long id;

	@Pattern(regexp = PATRON_NOMBRE, message = "El nombre solo puede contener letras y espacios")
	@Size(max = 100, message = "El nombre es demasiado largo")
	private String nombre;

	@Pattern(regexp = PATRON_NOMBRE, message = "Los apellidos solo pueden contener letras y espacios")
	@Size(max = 100, message = "Los apellidos son demasiado largos")
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
