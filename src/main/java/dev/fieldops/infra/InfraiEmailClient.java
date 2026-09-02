package dev.fieldops.infra;

import dev.fieldops.config.EmailPlatformConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient implements SendingDomainGateway {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern STATUS = Pattern.compile("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final EmailPlatformConfig config;
    private final HttpClient http;

    public InfraiEmailClient(EmailPlatformConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiEmailClient(EmailPlatformConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String verifyDomain(String domain, String idempotencyKey) {
        String body = "{\"domain\":\"" + json(domain) + "\"}";
        return require(perform("POST", "/v1/email/domain/verify", body, idempotencyKey), STATUS,
                "verification.status");
    }

    @Override
    public String sendFollowUp(String to, String subject, String html, String idempotencyKey) {
        String body = "{\"to\":\"" + json(to) + "\",\"subject\":\"" + json(subject)
                + "\",\"html\":\"" + json(html) + "\"}";
        return require(perform("POST", "/v1/email/send", body, idempotencyKey), MESSAGE_ID, "message_id");
    }

    private String perform(String method, String path, String body, String idempotencyKey) {
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Envelope envelope = decode(response.body(), response.statusCode());
                if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                    pause(retryDelayMillis(response, attempt));
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiException(envelope.errorCode(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new InfraiException("transport_status_" + response.statusCode(), response.statusCode());
                }
                return response.body();
            } catch (IOException e) {
                throw new InfraiException("transport_io", 503, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException("transport_interrupted", 503, e);
            }
        }
        throw new InfraiException("retry_exhausted", 429);
    }

    private static Envelope decode(String body, int httpStatus) {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) {
            throw new InfraiException("invalid_envelope", httpStatus);
        }
        Matcher error = ERROR_CODE.matcher(body);
        return new Envelope(Boolean.parseBoolean(ok.group(1)), error.find() ? error.group(1) : "request_rejected");
    }

    private static String require(String body, Pattern pattern, String field) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("Successful response omitted " + field);
        }
        return matcher.group(1);
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Long.parseLong(value) * 1_000L; }
                    catch (NumberFormatException ignored) { return 250L * (1L << (attempt - 1)); }
                })
                .orElse(250L * (1L << (attempt - 1)));
    }

    private static void pause(long millis) throws InterruptedException {
        Thread.sleep(Math.min(millis, 10_000L));
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Envelope(boolean ok, String errorCode) {}

    public static final class InfraiException extends RuntimeException {
        private final int httpStatus;

        InfraiException(String code, int httpStatus) {
            super(code);
            this.httpStatus = httpStatus;
        }

        InfraiException(String code, int httpStatus, Throwable cause) {
            super(code, cause);
            this.httpStatus = httpStatus;
        }

        public int httpStatus() { return httpStatus; }
    }
}
