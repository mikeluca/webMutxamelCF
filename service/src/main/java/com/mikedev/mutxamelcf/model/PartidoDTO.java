package com.mikedev.mutxamelcf.model;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

public class PartidoDTO {

	private Long id;
	private Long equipoId;
	private String equipo;
	private String categoria;
	private String deporte;
	private String rival;
	private String resultado;
	@DateTimeFormat(pattern = "dd/MM/yyyy")
	private Date dia;
	private String diaFormateado;
	private String hora;
	private String campo;

	public PartidoDTO() {
		super();
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

	public String getEquipo() {
		return equipo;
	}

	public void setEquipo(String equipo) {
		this.equipo = equipo;
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

	public String getRival() {
		return rival;
	}

	public void setRival(String rival) {
		this.rival = rival;
	}

	public String getResultado() {
		return resultado;
	}

	public void setResultado(String resultado) {
		this.resultado = resultado;
	}

	public Date getDia() {
		return dia;
	}

	public void setDia(Date dia) {
		this.dia = dia;
	}

	public String getDiaFormateado() {
		return diaFormateado;
	}

	public void setDiaFormateado(String diaFormateado) {
		this.diaFormateado = diaFormateado;
	}

	public String getHora() {
		return hora;
	}

	public void setHora(String hora) {
		this.hora = hora;
	}

	public String getCampo() {
		return campo;
	}

	public void setCampo(String campo) {
		this.campo = campo;
	}

}
