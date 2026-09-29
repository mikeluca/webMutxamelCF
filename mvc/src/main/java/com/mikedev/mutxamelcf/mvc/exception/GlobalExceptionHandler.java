package com.mikedev.mutxamelcf.mvc.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/*
 * BE-06: limitado con "annotations = RestController.class" para que solo
 * se aplique a los @RestController de la API (app y /api/public/**). Sin
 * esto, un error no controlado en un @Controller de Thymeleaf (panel de
 * admin, web pública) también pasaba por aquí y el navegador recibía un
 * cuerpo JSON en vez de una página de error.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, String>> manejarPeticionInvalida(Exception exception) {
        logger.debug("Inicio manejarPeticionInvalida: tipo={}", exception.getClass().getSimpleName());
        logger.warn("Peticion invalida: {}", exception.getMessage());
        logger.debug("Fin manejarPeticionInvalida: estado={}", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(Map.of("error", "Los datos enviados no son validos."));
    }

    /*
     * BE-04: sin este handler, una SecurityException lanzada por un
     * service (comprobaciones de permiso propias de la app, no de Spring
     * Security) caía en manejarErrorNoControlado() y se devolvía 500 en
     * vez de 403, y además se registraba como error en los logs.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> manejarAccesoDenegado(SecurityException exception) {
        logger.debug("Inicio manejarAccesoDenegado");
        logger.warn("Acceso denegado: {}", exception.getMessage());
        logger.debug("Fin manejarAccesoDenegado: estado={}", HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", exception.getMessage() != null ? exception.getMessage() : "Acceso denegado."));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> manejarNoEncontrado(NoSuchElementException exception) {
        logger.debug("Inicio manejarNoEncontrado");
        logger.warn("Recurso no encontrado: {}", exception.getMessage());
        logger.debug("Fin manejarNoEncontrado: estado={}", HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "El recurso solicitado no existe."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidacionInvalida(MethodArgumentNotValidException exception) {
        logger.debug("Inicio manejarValidacionInvalida");
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        logger.warn("Validacion de la peticion fallida: {}", errores);
        logger.debug("Fin manejarValidacionInvalida: estado={}", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(errores);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> manejarIntegridadDatos(DataIntegrityViolationException exception) {
        logger.debug("Inicio manejarIntegridadDatos");
        logger.warn("Operacion rechazada por integridad de datos: {}", exception.getMostSpecificCause().getMessage());
        logger.debug("Fin manejarIntegridadDatos: estado={}", HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "No se puede completar la operacion porque existen datos relacionados o duplicados."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> manejarAccesoDatos(DataAccessException exception) {
        logger.debug("Inicio manejarAccesoDatos");
        logger.error("Error de acceso a base de datos: {}", exception.getMessage(), exception);
        logger.debug("Fin manejarAccesoDatos: estado={}", HttpStatus.SERVICE_UNAVAILABLE.value());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "No se ha podido acceder a los datos. Intentalo de nuevo mas tarde."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> manejarErrorNoControlado(Exception exception) {
        logger.debug("Inicio manejarErrorNoControlado: tipo={}", exception.getClass().getSimpleName());
        logger.error("Error no controlado en la aplicacion: {}", exception.getMessage(), exception);
        logger.debug("Fin manejarErrorNoControlado: estado={}", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Ha ocurrido un error inesperado. Intentalo de nuevo."));
    }
}
