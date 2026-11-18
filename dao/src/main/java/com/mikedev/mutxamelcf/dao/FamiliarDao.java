package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Familiar;

public interface FamiliarDao {

    boolean guardarFamiliar(Familiar familiar);

    Familiar obtenerPorId(Long id);

    List<Familiar> obtenerTodos();

    void eliminar(Long id);

    List<Familiar> obtenerPorJugador(Long jugadorId);

    /**
     * La ficha de familiar con ese email (sin distinguir mayúsculas) o, si no hay, con ese teléfono (solo dígitos y +;
     * un teléfono vacío no busca). La de menor id si hay varias; null si no hay ninguna.
     */
    Familiar obtenerPorEmailOTelefono(String email, String telefono);
}