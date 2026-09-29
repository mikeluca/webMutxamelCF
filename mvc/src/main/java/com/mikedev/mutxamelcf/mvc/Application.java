package com.mikedev.mutxamelcf.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = "com.mikedev.mutxamelcf"
)
@EnableScheduling
// BE-02: permite que los envios de push (FcmPushServiceImpl) y el
// listener de eventos de ComunicacionServiceImpl se ejecuten fuera del
// hilo de la peticion.
@EnableAsync
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
