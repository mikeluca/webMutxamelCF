package com.mikedev.mutxamelcf.mvc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Un archivo mayor que el límite de subida (6 MB) falla antes de llegar al controlador. En "Fondos de carteles" se vuelve
 * a la pantalla con un mensaje claro; en el resto, el 413 de siempre.
 */
@ControllerAdvice
public class SubidaDemasiadoGrandeAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object demasiadoGrande(HttpServletRequest request, RedirectAttributes redirect) {

        if (request.getRequestURI().startsWith("/admin/fondos-carteles")) {
            redirect.addFlashAttribute("error", "La imagen pesa más de 5 MB");
            return "redirect:/admin/fondos-carteles";
        }

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
    }
}
