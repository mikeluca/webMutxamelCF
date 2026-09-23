package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.ConvocatoriaGuardarRequest;
import com.mikedev.mutxamelcf.model.ConvocatoriaResponse;

import java.util.List;

public interface ConvocatoriaService {

        ConvocatoriaResponse crear(
                        Long usuarioAppId,
                        ConvocatoriaGuardarRequest request);

        ConvocatoriaResponse obtenerPorId(
                        Long usuarioAppId,
                        Long convocatoriaId);

        List<ConvocatoriaResponse> obtenerPorEquipo(
                        Long usuarioAppId,
                        Long equipoId);

        void eliminar(
                        Long usuarioAppId,
                        Long convocatoriaId);

        ConvocatoriaResponse actualizar(
                        Long usuarioAppId,
                        Long convocatoriaId,
                        ConvocatoriaGuardarRequest request);
}