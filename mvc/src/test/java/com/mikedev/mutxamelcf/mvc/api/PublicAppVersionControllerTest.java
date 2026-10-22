package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

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
}
