package com.mikedev.mutxamelcf.dao;

import com.mikedev.mutxamelcf.model.DispositivoApp;
import java.util.List;

public interface DispositivoAppDao {

        DispositivoApp obtenerPorUsuarioYToken(
                        Long usuarioAppId,
                        String tokenFcm);

        void registrar(DispositivoApp dispositivo);

        /** Reactiva el dispositivo y anota el último acceso; versionApp null no toca la versión guardada. */
        void actualizarAcceso(
                        Long usuarioAppId,
                        String tokenFcm,
                        String versionApp);

        void desactivar(
                        Long usuarioAppId,
                        String tokenFcm);

        void desactivarTokenDeOtrosUsuarios(
                        Long usuarioAppId,
                        String tokenFcm);

        List<DispositivoApp> obtenerActivosPorUsuario(Long usuarioAppId);

        void eliminarTodosDeUsuario(Long usuarioAppId);
}