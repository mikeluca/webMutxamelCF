package com.mikedev.mutxamelcf.model;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

public class ResultadoDTO {
	private String categoria;
	private String equipo;
	private String rival;
	private String resultado;
	private String tipo;
	@DateTimeFormat(pattern = "dd/MM/yyyy")
	private Date dia;
	private String diaFormateado;
	private String hora;
	private String campo;

	public ResultadoDTO() {
		super();
	}

	public ResultadoDTO(String categoria, String equipo, String rival, String resultado, Date dia, String hora,
			String campo) {
		super();
		this.categoria = categoria;
		this.setEquipo(equipo);
		this.rival = rival;
		this.resultado = resultado;
		this.dia = dia;
		this.hora = hora;
		this.campo = campo;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
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

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public Date getDia() {
		return dia;
	}

	public void setDia(Date dia) {
		this.dia = dia;
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

	public String getEquipo() {
		return equipo;
	}

	public void setEquipo(String equipo) {
		this.equipo = equipo;
	}

	public String getDiaFormateado() {
		return diaFormateado;
	}

	public void setDiaFormateado(String diaFormateado) {
		this.diaFormateado = diaFormateado;
	}

}
