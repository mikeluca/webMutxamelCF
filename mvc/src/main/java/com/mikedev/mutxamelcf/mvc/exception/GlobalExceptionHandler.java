package com.mikedev.mutxamelcf.mvc.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, String>> manejarPeticionInvalida(Exception exception) {
        logger.debug("Inicio manejarPeticionInvalida: tipo={}", exception.getClass().getSimpleName());
        logger.warn("Peticion invalida: {}", exception.getMessage());
        logger.debug("Fin manejarPeticionInvalida: estado={}", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(Map.of("error", "Los datos enviados no son validos."));
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
