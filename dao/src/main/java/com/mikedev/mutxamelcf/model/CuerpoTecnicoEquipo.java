package com.mikedev.mutxamelcf.model;

/**
 * Un equipo de una ficha de cuerpo técnico (fila de CUERPO_TECNICO_EQUIPO). {@code puesto} es el puesto en ESE equipo
 * y es nulo en el caso habitual: vale el general de la ficha. {@code puestoMostrado} es
 * COALESCE(CTE.PUESTO, CT.PUESTO).
 */
public class CuerpoTecnicoEquipo {

	private Long equipoId;
	private String equipoNombre;
	private String categoria;
	private String deporte;
	private String puesto;
	private String puestoMostrado;

	public CuerpoTecnicoEquipo() {
		super();
	}

	public CuerpoTecnicoEquipo(Long equipoId, String puesto) {
		super();
		this.equipoId = equipoId;
		this.puesto = puesto;
	}

	public Long getEquipoId() {
		return equipoId;
	}

	public void setEquipoId(Long equipoId) {
		this.equipoId = equipoId;
	}

	public String getEquipoNombre() {
		return equipoNombre;
	}

	public void setEquipoNombre(String equipoNombre) {
		this.equipoNombre = equipoNombre;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

	public String getPuesto() {
		return puesto;
	}

	public void setPuesto(String puesto) {
		this.puesto = puesto;
	}

	public String getPuestoMostrado() {
		return puestoMostrado;
	}

	public void setPuestoMostrado(String puestoMostrado) {
		this.puestoMostrado = puestoMostrado;
	}
}
