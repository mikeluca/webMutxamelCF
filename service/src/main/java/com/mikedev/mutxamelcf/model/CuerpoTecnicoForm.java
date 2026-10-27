package com.mikedev.mutxamelcf.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public class CuerpoTecnicoForm {
	private Long id;
	private String nombre;
	private String apellidos;
	/** Los equipos de la ficha (puede no haber ninguno). */
	private List<Long> equipoIds = new ArrayList<>();
	/**
	 * "Puesto en este equipo", en el mismo orden que {@link #equipoIds}. Vacío = el puesto general.
	 */
	private List<String> puestosEquipo = new ArrayList<>();
	/** El puesto general de la ficha: vale para todos sus equipos. */
	private String puesto;
	private MultipartFile foto;

	public CuerpoTecnicoForm() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public List<Long> getEquipoIds() {
		return equipoIds;
	}

	public void setEquipoIds(List<Long> equipoIds) {
		this.equipoIds = equipoIds == null ? new ArrayList<>() : equipoIds;
	}

	public List<String> getPuestosEquipo() {
		return puestosEquipo;
	}

	public void setPuestosEquipo(List<String> puestosEquipo) {
		this.puestosEquipo = puestosEquipo == null ? new ArrayList<>() : puestosEquipo;
	}

	public MultipartFile getFoto() {
		return foto;
	}

	public void setFoto(MultipartFile foto) {
		this.foto = foto;
	}

}
