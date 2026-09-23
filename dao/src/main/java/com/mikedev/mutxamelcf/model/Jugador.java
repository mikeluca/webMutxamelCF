package com.mikedev.mutxamelcf.model;

import java.util.Date;

public class Jugador {
	private Long id;
	private String dni;
	private String nombre;
	private String apellidos;
	private Date fechaNacimiento;
	private String poblacion;
	private String nacionalidad;
	private String categoria;
	private String deporte;
	private String equipo;
	private Integer dorsal;
	private String posicion;
	private byte[] foto;

	public Jugador(Long id, String dni, String nombre, String apellidos, Date fechaNacimiento, String poblacion,
			String nacionalidad, String categoria, String deporte, String equipo, int dorsal, String posicion,
			byte[] foto) {
		super();
		this.setId(id);
		this.dni = dni;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fechaNacimiento = fechaNacimiento;
		this.poblacion = poblacion;
		this.nacionalidad = nacionalidad;
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

	public String getDni() {
		return dni;
	}

	public void setDni(String dni) {
		this.dni = dni;
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

	public Date getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(Date fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public String getPoblacion() {
		return poblacion;
	}

	public void setPoblacion(String poblacion) {
		this.poblacion = poblacion;
	}

	public String getNacionalidad() {
		return nacionalidad;
	}

	public void setNacionalidad(String nacionalidad) {
		this.nacionalidad = nacionalidad;
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
