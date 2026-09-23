package com.mikedev.mutxamelcf.mvc.controller;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

/**
 * Utilidades compartidas por los controladores de administración
 * (logos de patrocinadores mostrados en todas las pantallas del
 * panel, y validación de imágenes subidas en los formularios).
 */
final class AdminViewSupport {

	private AdminViewSupport() {
	}

	static final List<String> PATROCINADORES = List.of("patrocinador1.jpg", "patrocinador2.jpg",
			"patrocinador3.jpg", "patrocinador4.jpg", "patrocinador5.jpg", "patrocinador6.jpg",
			"patrocinador7.jpg", "patrocinador8.jpg");

	private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");

	static final long TAMANO_MAXIMO_FOTO = 5 * 1024 * 1024; // 5 MB

	// Valida que el fichero subido sea una imagen de un tipo permitido y no
	// supere el tamaño máximo indicado. Devuelve un mensaje de error o null si
	// es válido.
	static String validarImagen(MultipartFile archivo, long tamanoMaximo) {
		if (archivo.getSize() > tamanoMaximo) {
			return "La imagen excede el tamaño máximo permitido (" + (tamanoMaximo / (1024 * 1024)) + " MB).";
		}
		String contentType = archivo.getContentType();
		if (contentType == null || !TIPOS_IMAGEN_PERMITIDOS.contains(contentType.toLowerCase(Locale.ROOT))) {
			return "El archivo debe ser una imagen JPEG, PNG o WEBP.";
		}
		return null;
	}
}
