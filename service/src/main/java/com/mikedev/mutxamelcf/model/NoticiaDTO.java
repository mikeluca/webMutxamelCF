package com.mikedev.mutxamelcf.model;

import java.util.Date;

public class NoticiaDTO {
	private int id;
	private String titulo;
	private String contenido;
	private Date fecha;
	private byte[] imagen;
	private String imagenBase64;

	public NoticiaDTO(String titulo, String contenido, Date fecha, byte[] imagen) {
		super();
		this.titulo = titulo;
		this.contenido = contenido;
		this.fecha = fecha;
		this.imagen = imagen;
	}

	public NoticiaDTO() {
		super();
	}

	public String getTitulo() {
		return titulo;
	}

	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}

	public String getContenido() {
		return contenido;
	}

	public void setContenido(String contenido) {
		this.contenido = contenido;
	}

	public Date getFecha() {
		return fecha;
	}

	public void setFecha(Date fecha) {
		this.fecha = fecha;
	}

	public byte[] getImagen() {
		return imagen;
	}

	public void setImagen(byte[] imagen) {
		this.imagen = imagen;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getImagenBase64() {
		return imagenBase64;
	}

	public void setImagenBase64(String imagenBase64) {
		this.imagenBase64 = imagenBase64;
	}

}
