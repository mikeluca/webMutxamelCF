package com.mikedev.mutxamelcf.mvc.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void manejarPeticionInvalidaDevuelve400ParaIllegalArgument() {
        ResponseEntity<Map<String, String>> response = handler
                .manejarPeticionInvalida(new IllegalArgumentException("dato invalido"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void manejarPeticionInvalidaDevuelve400ParaTipoIncorrecto() {
        MethodArgumentTypeMismatchException excepcion = mock(MethodArgumentTypeMismatchException.class);

        ResponseEntity<Map<String, String>> response = handler.manejarPeticionInvalida(excepcion);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void manejarValidacionInvalidaDevuelveLosErroresPorCampo() {
        MethodArgumentNotValidException excepcion = mock(MethodArgumentNotValidException.class);

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objetivo");
        bindingResult.addError(new FieldError("objetivo", "nombre", "El nombre es obligatorio"));

        when(excepcion.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, String>> response = handler.manejarValidacionInvalida(excepcion);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("nombre", "El nombre es obligatorio");
    }

    @Test
    void manejarIntegridadDatosDevuelve409() {
        DataIntegrityViolationException excepcion = new DataIntegrityViolationException("violacion",
                new RuntimeException("causa"));

        ResponseEntity<Map<String, String>> response = handler.manejarIntegridadDatos(excepcion);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void manejarAccesoDatosDevuelve503() {
        DataAccessResourceFailureException excepcion = new DataAccessResourceFailureException("bd caida");

        ResponseEntity<Map<String, String>> response = handler.manejarAccesoDatos(excepcion);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void manejarErrorNoControladoDevuelve500() {
        ResponseEntity<Map<String, String>> response = handler
                .manejarErrorNoControlado(new RuntimeException("fallo inesperado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
