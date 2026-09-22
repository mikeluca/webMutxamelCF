package com.mikedev.mutxamelcf.model;

import org.springframework.web.multipart.MultipartFile;

public class NoticiaForm {
	private Long id;
	private String titulo;
	private String contenido;
	private MultipartFile imagen;

	public NoticiaForm(Long id, String titulo, String contenido, MultipartFile imagen) {
		super();
		this.id = id;
		this.titulo = titulo;
		this.contenido = contenido;
		this.imagen = imagen;
	}

	public NoticiaForm() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public MultipartFile getImagen() {
		return imagen;
	}

	public void setImagen(MultipartFile imagen) {
		this.imagen = imagen;
	}

}
