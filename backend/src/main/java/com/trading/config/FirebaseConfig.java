package com.trading.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-path:#{null}}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                InputStream serviceAccount = resolveCredentials();
                if (serviceAccount == null) {
                    log.warn("Firebase service account not configured. Push notifications disabled.");
                    return;
                }
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully.");
            } catch (IOException e) {
                log.error("Failed to initialize Firebase: {}", e.getMessage());
            }
        }
    }

    private InputStream resolveCredentials() throws IOException {
        if (serviceAccountPath != null) {
            return new FileInputStream(serviceAccountPath);
        }
        InputStream classPathResource = getClass().getResourceAsStream("/firebase-service-account.json");
        if (classPathResource != null) {
            return classPathResource;
        }
        return null;
    }
}
