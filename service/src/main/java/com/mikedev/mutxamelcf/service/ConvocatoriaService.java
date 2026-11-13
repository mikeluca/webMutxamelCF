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

        /**
         * Avisa "Convocatoria anulada" a los convocados del propio equipo (con el mismo texto y las mismas reglas que
         * {@link #eliminar}: nunca a quien tenga la ausencia justificada; los invitados los avisa InvitadosService) sin
         * borrar nada ni comprobar permisos. Es para el borrado de un partido, que va después y borra la convocatoria.
         * Se llama antes de borrar: las justificaciones y las filas de convocados tienen que seguir ahí.
         */
        void avisarAnulada(Long convocatoriaId);

        ConvocatoriaResponse actualizar(
                        Long usuarioAppId,
                        Long convocatoriaId,
                        ConvocatoriaGuardarRequest request);
}