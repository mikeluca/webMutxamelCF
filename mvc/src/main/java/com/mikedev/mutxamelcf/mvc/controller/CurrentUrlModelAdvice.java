package com.mikedev.mutxamelcf.mvc.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expone la URI actual de la peticion como atributo de modelo "currentUri"
 * para todas las vistas servidas por MainController. Lo usa el selector de
 * idioma de cabecera.html para recargar la pagina en la que esta el usuario
 * anadiendo "?lang=es" o "?lang=ca", en vez de llevarle siempre a /index.
 *
 * Se hace con un @ModelAttribute de @ControllerAdvice (y no con el objeto
 * de utilidad "#request" de Thymeleaf) porque esta version de Thymeleaf ya
 * no expone #request/#session/#servletContext/#response por defecto.
 */
@ControllerAdvice(assignableTypes = MainController.class)
public class CurrentUrlModelAdvice {

	@ModelAttribute("currentUri")
	public String currentUri(HttpServletRequest request) {
		return request.getRequestURI();
	}

}
