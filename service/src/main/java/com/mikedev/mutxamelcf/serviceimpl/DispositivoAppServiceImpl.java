package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.model.DispositivoApp;
import com.mikedev.mutxamelcf.model.DispositivoAppRequest;
import com.mikedev.mutxamelcf.service.DispositivoAppService;

@Service
@Transactional
public class DispositivoAppServiceImpl
        implements DispositivoAppService {

    private final DispositivoAppDao dispositivoAppDao;

    public DispositivoAppServiceImpl(
            DispositivoAppDao dispositivoAppDao) {

        this.dispositivoAppDao = dispositivoAppDao;
    }

    @Override
    public void registrar(
            Long usuarioAppId,
            DispositivoAppRequest request) {

        if (usuarioAppId == null) {
            throw new IllegalArgumentException(
                    "El usuario es obligatorio");
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Los datos del dispositivo son obligatorios");
        }

        if (request.getTokenFcm() == null ||
                request.getTokenFcm().isBlank()) {

            throw new IllegalArgumentException(
                    "El token FCM es obligatorio");
        }

        String tokenFcm = request.getTokenFcm().trim();

        dispositivoAppDao.desactivarTokenDeOtrosUsuarios(
                usuarioAppId,
                tokenFcm);

        String plataforma = request.getPlataforma() == null ||
                request.getPlataforma().isBlank()
                        ? "ANDROID"
                        : request.getPlataforma()
                                .trim()
                                .toUpperCase();

        DispositivoApp existente = dispositivoAppDao.obtenerPorUsuarioYToken(
                usuarioAppId,
                tokenFcm);

        if (existente != null) {

            dispositivoAppDao.actualizarAcceso(
                    usuarioAppId,
                    tokenFcm);

            return;
        }

        DispositivoApp dispositivo = new DispositivoApp();

        dispositivo.setUsuarioAppId(usuarioAppId);
        dispositivo.setTokenFcm(tokenFcm);
        dispositivo.setPlataforma(plataforma);
        dispositivo.setActivo(1);

        LocalDateTime ahora = LocalDateTime.now();

        dispositivo.setFechaRegistro(ahora);
        dispositivo.setFechaUltimoAcceso(ahora);

        dispositivoAppDao.registrar(
                dispositivo);
    }

    @Override
    public void desactivar(
            Long usuarioAppId,
            String tokenFcm) {

        if (usuarioAppId == null ||
                tokenFcm == null ||
                tokenFcm.isBlank()) {

            return;
        }

        dispositivoAppDao.desactivar(
                usuarioAppId,
                tokenFcm.trim());
    }
}