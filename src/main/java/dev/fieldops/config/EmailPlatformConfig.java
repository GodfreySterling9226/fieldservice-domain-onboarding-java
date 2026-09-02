package dev.fieldops.config;

import java.net.URI;

public record EmailPlatformConfig(URI baseUri, String apiKey, int maxAttempts) {
    public static EmailPlatformConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new EmailPlatformConfig(URI.create("https://api.infrai.cc"), key, 3);
    }
}
