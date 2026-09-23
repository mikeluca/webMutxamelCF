package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.EntrenamientoGuardarRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoResponse;
import java.util.List;

public interface EntrenamientoService {

        EntrenamientoResponse crear(
                        Long usuarioAppId,
                        EntrenamientoGuardarRequest request);

        EntrenamientoResponse actualizar(
                        Long usuarioAppId,
                        Long entrenamientoId,
                        EntrenamientoGuardarRequest request);

        EntrenamientoResponse obtenerPorId(
                        Long usuarioAppId,
                        Long entrenamientoId);

        List<EntrenamientoResponse> obtenerPorEquipo(
                        Long usuarioAppId,
                        Long equipoId);
}