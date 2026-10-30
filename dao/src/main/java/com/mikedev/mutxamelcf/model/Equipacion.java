package com.mikedev.mutxamelcf.model;

/**
 * Un equipaje del club ("Primera", "Segunda", "Entrenamiento"...), que se elige en la convocatoria. CAMISETA, PANTALON
 * y MEDIAS son el color en texto; COLOR_HEX ('#RRGGBB') es la muestra de color de la app y del panel.
 */
public class Equipacion {

	private Long id;
	private String nombre;
	private String camiseta;
	private String pantalon;
	private String medias;
	private String colorHex;
	private int orden;
	private boolean activa = true;

	/** Cuántas convocatorias lo usan (principal o mochila). Solo lo rellena el listado del panel. */
	private int convocatoriasUsadas;

	public Equipacion() {
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

	public String getCamiseta() {
		return camiseta;
	}

	public void setCamiseta(String camiseta) {
		this.camiseta = camiseta;
	}

	public String getPantalon() {
		return pantalon;
	}

	public void setPantalon(String pantalon) {
		this.pantalon = pantalon;
	}

	public String getMedias() {
		return medias;
	}

	public void setMedias(String medias) {
		this.medias = medias;
	}

	public String getColorHex() {
		return colorHex;
	}

	public void setColorHex(String colorHex) {
		this.colorHex = colorHex;
	}

	public int getOrden() {
		return orden;
	}

	public void setOrden(int orden) {
		this.orden = orden;
	}

	public boolean isActiva() {
		return activa;
	}

	public void setActiva(boolean activa) {
		this.activa = activa;
	}

	public int getConvocatoriasUsadas() {
		return convocatoriasUsadas;
	}

	public void setConvocatoriasUsadas(int convocatoriasUsadas) {
		this.convocatoriasUsadas = convocatoriasUsadas;
	}
}
