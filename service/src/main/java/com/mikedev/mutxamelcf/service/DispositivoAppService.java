package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.DispositivoAppRequest;

public interface DispositivoAppService {

    void registrar(
            Long usuarioAppId,
            DispositivoAppRequest request);

    void desactivar(
            Long usuarioAppId,
            String tokenFcm);
}