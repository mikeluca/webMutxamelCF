package com.mikedev.mutxamelcf.mvc.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.path}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {

        if (FirebaseApp.getApps().isEmpty()) {

            try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(
                                GoogleCredentials.fromStream(serviceAccount))
                        .build();

                return FirebaseApp.initializeApp(options);
            }
        }

        return FirebaseApp.getInstance();
    }
}