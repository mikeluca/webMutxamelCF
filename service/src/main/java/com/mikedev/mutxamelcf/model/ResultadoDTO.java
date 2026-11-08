package com.mikedev.mutxamelcf.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

public class ResultadoDTO {
	private String categoria;
	private Long equipoId;
	private String equipo;
	private String rival;
	private String resultado;
	private String tipo;
	@DateTimeFormat(pattern = "dd/MM/yyyy")
	private Date dia;
	private String diaFormateado;
	private String hora;
	private String campo;
	/** true si el Mutxamel juega como local (se muestra a la izquierda). */
	private boolean local = true;
	/** true si el equipo tiene un descanso registrado este fin de semana y ningún partido. */
	private boolean descansa;
	/** true si el último partido ya se jugó (o ha empezado hace rato) y aún no tiene resultado ni goles. */
	private boolean pendienteResultado;
	/** true desde el inicio retransmitido (evento INICIO no anulado) hasta el final. */
	private boolean enJuego;
	/**
	 * PRIMERA_PARTE, DESCANSO, SEGUNDA_PARTE o FINALIZADO; null si el partido no se ha retransmitido. Con el partido
	 * en juego, {@code resultado} sigue vacío: el marcador va en golesFavor y golesContra.
	 */
	private String fase;
	/** Marcador calculado con los eventos de la retransmisión; null si no se ha retransmitido. */
	private Integer golesFavor;
	private Integer golesContra;
	/**
	 * Goles del Mutxamel en orden, con el nombre de retransmisión y agrupados ("Pepe (2)"). Solo con el partido
	 * retransmitido y FINALIZADO; vacía en otro caso.
	 */
	private List<String> goleadores = new ArrayList<>();

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

	public Long getEquipoId() {
		return equipoId;
	}

	public void setEquipoId(Long equipoId) {
		this.equipoId = equipoId;
	}

	public String getDiaFormateado() {
		return diaFormateado;
	}

	public void setDiaFormateado(String diaFormateado) {
		this.diaFormateado = diaFormateado;
	}

	public boolean isDescansa() {
		return descansa;
	}

	public void setDescansa(boolean descansa) {
		this.descansa = descansa;
	}


	public boolean isPendienteResultado() {
		return pendienteResultado;
	}

	public void setPendienteResultado(boolean pendienteResultado) {
		this.pendienteResultado = pendienteResultado;
	}

	public boolean isEnJuego() {
		return enJuego;
	}

	public void setEnJuego(boolean enJuego) {
		this.enJuego = enJuego;
	}

	public String getFase() {
		return fase;
	}

	public void setFase(String fase) {
		this.fase = fase;
	}

	public Integer getGolesFavor() {
		return golesFavor;
	}

	public void setGolesFavor(Integer golesFavor) {
		this.golesFavor = golesFavor;
	}

	public Integer getGolesContra() {
		return golesContra;
	}

	public void setGolesContra(Integer golesContra) {
		this.golesContra = golesContra;
	}

	public List<String> getGoleadores() {
		return goleadores;
	}

	public void setGoleadores(List<String> goleadores) {
		this.goleadores = goleadores;
	}

	public boolean isLocal() {
		return local;
	}

	public void setLocal(boolean local) {
		this.local = local;
	}

}
