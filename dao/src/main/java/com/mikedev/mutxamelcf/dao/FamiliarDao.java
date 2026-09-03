package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Familiar;

public interface FamiliarDao {

    boolean guardarFamiliar(Familiar familiar);

    Familiar obtenerPorId(Long id);

    List<Familiar> obtenerTodos();

    void eliminar(Long id);
}