package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

class PublicAppVersionControllerTest {

    @Test
    void devuelveLasVersionesYLasUrlsConfiguradas() {
        PublicAppVersionController controller = new PublicAppVersionController(
                "1.0.2", "1.0.3", "1.0.4", "1.0.5", "https://play/x", "https://apple/x");

        Map<String, String> version = controller.version();

        assertThat(version).containsEntry("minimaAndroid", "1.0.2")
                .containsEntry("minimaIos", "1.0.3")
                .containsEntry("recomendadaAndroid", "1.0.4")
                .containsEntry("recomendadaIos", "1.0.5")
                .containsEntry("urlAndroid", "https://play/x")
                .containsEntry("urlIos", "https://apple/x")
                .hasSize(6);
    }

    /**
     * Con los valores del application.properties real: iOS 1.0.14 ya publicada en la App Store;
     * Android sigue en 1.0.2 porque aun no esta en produccion.
     */
    @Test
    void conLasPropiedadesDelFicheroRealPublicaIos1_0_14YAndroid1_0_2() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        // Si APP_URL_IOS estuviera definida en la maquina, ganaria sobre el valor por defecto del fichero.
        assertThat(System.getenv("APP_URL_IOS")).isNull();

        PublicAppVersionController controller = new PublicAppVersionController(
                env.getRequiredProperty("app.version.minima.android"),
                env.getRequiredProperty("app.version.minima.ios"),
                env.getRequiredProperty("app.version.recomendada.android"),
                env.getRequiredProperty("app.version.recomendada.ios"),
                env.getRequiredProperty("app.url.android"),
                env.getRequiredProperty("app.url.ios"));

        assertThat(controller.version())
                .containsEntry("minimaIos", "1.0.14")
                .containsEntry("recomendadaIos", "1.0.14")
                .containsEntry("minimaAndroid", "1.0.2")
                .containsEntry("recomendadaAndroid", "1.0.2")
                .containsEntry("urlIos", "https://apps.apple.com/es/app/mutxamel-cf/id6820570960")
                .containsEntry("urlAndroid", "https://play.google.com/store/apps/details?id=com.mutxamelcf.app");
    }
}
