package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.FamiliarContactoDTO;

public interface FamiliarService {

    boolean guardarFamiliar(FamiliarDTO familiar);

    FamiliarDTO obtenerFamiliarPorId(Long id);

    List<FamiliarDTO> obtenerTodos();

    void eliminarFamiliar(Long id);

    FamiliarDTO obtenerFamiliarPrincipalDeJugador(Long jugadorId);

    List<FamiliarContactoDTO> obtenerFamiliaresPorJugador(Long jugadorId);
}