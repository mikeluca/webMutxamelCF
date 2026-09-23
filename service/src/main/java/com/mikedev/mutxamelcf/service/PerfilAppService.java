package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.PerfilAppResponse;

public interface PerfilAppService {

    PerfilAppResponse obtenerPerfil(int usuarioAppId);
}