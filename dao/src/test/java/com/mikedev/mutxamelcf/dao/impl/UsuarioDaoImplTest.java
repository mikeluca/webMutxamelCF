package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Usuario;

import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuarioDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioDevuelveElUsuarioConSuHashCuandoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        Usuario esperado = new Usuario();
        esperado.setUsuario("admin");
        esperado.setPassword("$2a$10$hashDeEjemplo");
        esperado.setRol("SUPER");

        when(jdbcTemplate.queryForObject(
                any(String.class),
                any(RowMapper.class),
                eq("admin")))
                .thenReturn(esperado);

        UsuarioDaoImpl dao = new UsuarioDaoImpl(jdbcTemplate);

        Usuario resultado = dao.obtenerPorUsuario("admin");

        assertEquals("admin", resultado.getUsuario());
        assertEquals("$2a$10$hashDeEjemplo", resultado.getPassword());
        assertEquals("SUPER", resultado.getRol());
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        when(jdbcTemplate.queryForObject(
                any(String.class),
                any(RowMapper.class),
                eq("no_existe")))
                .thenThrow(new EmptyResultDataAccessException(1));

        UsuarioDaoImpl dao = new UsuarioDaoImpl(jdbcTemplate);

        assertNull(dao.obtenerPorUsuario("no_existe"));
    }
}
