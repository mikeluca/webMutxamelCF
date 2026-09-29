package com.mikedev.mutxamelcf.mvc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * N-06 (2.ª auditoría): JacksonConfig declaraba su propio
 * {@code new ObjectMapper()} a mano, que no desactivaba
 * FAIL_ON_UNKNOWN_PROPERTIES (Boot sí lo desactiva en el suyo). Mientras
 * WebMvcConfigurationSupport estuvo activo (antes de BE-05) ese bean no
 * llegaba a usarlo la API; al reactivar la autoconfiguración de Boot sí
 * pasó a usarlo, y cualquier campo JSON desconocido empezó a dar 400.
 * JacksonConfig se eliminó a favor de las propiedades
 * {@code spring.jackson.*} de application.properties. Estos tests fijan
 * el contrato para detectar si alguien vuelve a declarar un
 * {@code @Bean ObjectMapper} manual sin ese cuidado.
 */
@JsonTest
class JacksonContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    public static class Simple {
        public String nombre;
    }

    @Test
    void ignoraCamposDesconocidosEnLugarDeFallar() throws Exception {
        Simple resultado = objectMapper.readValue(
                "{\"nombre\":\"Ana\",\"campoQueNoExisteEnElDto\":123}", Simple.class);

        assertThat(resultado.nombre).isEqualTo("Ana");
    }

    @Test
    void serializaFechasComoTextoIsoNoComoNumeroEpoch() throws Exception {
        String json = objectMapper.writeValueAsString(new Date(0));

        assertThat(json).startsWith("\"");
    }
}
