package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.FcmTestRequest;
import com.mikedev.mutxamelcf.service.FcmPushService;

@RestController
@RequestMapping("/api/app/fcm-test")
public class FcmTestController {

    private final FcmPushService fcmPushService;

    public FcmTestController(
            FcmPushService fcmPushService) {
        this.fcmPushService = fcmPushService;
    }

    @PostMapping
    public ResponseEntity<Void> enviar(
            @RequestBody FcmTestRequest request) {

        fcmPushService.enviarNotificacion(
                request.getTokenFcm(),
                request.getTitulo(),
                request.getMensaje());

        return ResponseEntity.ok().build();
    }
}