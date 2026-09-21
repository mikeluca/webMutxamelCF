package com.mikedev.mutxamelcf.model;

import java.util.Date;

public class Noticia {
	private int id;
	private String titulo;
	private String contenido;
	private Date fecha;
	private byte[] imagen;

	public Noticia(String titulo, String contenido, Date fecha, byte[] imagen) {
		super();
		this.titulo = titulo;
		this.contenido = contenido;
		this.fecha = fecha;
		this.imagen = imagen;
	}

	public Noticia() {
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

}
