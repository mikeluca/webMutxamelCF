package com.mikedev.mutxamelcf.dao;

import com.mikedev.mutxamelcf.model.DispositivoApp;
import java.util.List;

public interface DispositivoAppDao {

        DispositivoApp obtenerPorUsuarioYToken(
                        Long usuarioAppId,
                        String tokenFcm);

        void registrar(DispositivoApp dispositivo);

        void actualizarAcceso(
                        Long usuarioAppId,
                        String tokenFcm);

        void desactivar(
                        Long usuarioAppId,
                        String tokenFcm);

        void desactivarTokenDeOtrosUsuarios(
                        Long usuarioAppId,
                        String tokenFcm);

        List<DispositivoApp> obtenerActivosPorUsuario(Long usuarioAppId);
}