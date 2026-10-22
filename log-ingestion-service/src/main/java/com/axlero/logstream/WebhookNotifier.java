package com.axlero.logstream;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// Sends a real notification outside the dashboard when an alert
// fires, instead of only showing it in the UI.
//
// The webhook URL comes from an environment variable so it's never
// hardcoded or committed to the repo - set ALERT_WEBHOOK_URL before
// starting QueryApiServer to turn this on. If it's not set, sending
// is skipped silently (no crash, no noisy errors).
//
// The JSON body includes both "text" (Slack's field name) and
// "content" (Discord's field name) so the same webhook works with
// either one - each platform just ignores the field it doesn't use.
public class WebhookNotifier {

    private static final String WEBHOOK_URL = System.getenv("ALERT_WEBHOOK_URL");
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void send(String message) {
        if (WEBHOOK_URL == null || WEBHOOK_URL.isBlank()) {
            return;
        }
        try {
            String quotedMessage = JsonStrings.quote(message);
            String json = "{\"text\": " + quotedMessage + ", \"content\": " + quotedMessage + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // Fire-and-forget: we don't want a slow or down webhook to
            // block log ingestion or the alert-checking loop.
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        System.out.println("Webhook send failed: " + e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.out.println("Webhook send failed: " + e.getMessage());
        }
    }
}
