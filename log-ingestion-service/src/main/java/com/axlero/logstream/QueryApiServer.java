package com.axlero.logstream;

import org.apache.lucene.queryparser.classic.ParseException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This is the BRIDGE between our gRPC backend and the React frontend.
//
// Endpoints:
//   GET  /api/aggregations       -> Overview page: stat cards + service/level charts
//   GET  /api/search?q=...       -> Logs page: search / filter logs
//   GET  /api/timeline           -> Analytics page: "Log Volume" line chart
//   GET  /api/alerts             -> Alerts page: whether the built-in alert is active right now
//   GET  /api/alerts/history     -> Alerts page: past built-in alerts that have fired
//   GET  /api/alert-rules        -> Alerts page: every user-defined alert rule
//   POST /api/alert-rules        -> Alerts page: create a new alert rule
//   DELETE /api/alert-rules?id=  -> Alerts page: delete an alert rule
//   GET  /api/alert-rules/history -> Alerts page: past user-defined rule alerts
//
// NOTE: Live Tail does NOT go through this server anymore. It connects
// directly to LiveTailWebSocketServer on port 8081, which pushes new
// logs the instant they're ingested, bypassing Lucene entirely - see
// LogIngestionServer.java and LiveTailWebSocketServer.java.
public class QueryApiServer {

    private static final int ERROR_THRESHOLD = 3;
    private static final int WINDOW_MINUTES = 5;

    public static void main(String[] args) throws Exception {
        LuceneIndexer indexer = new LuceneIndexer();
        AlertHistory alertHistory = new AlertHistory();

        // NEW: user-defined alert rules + their own firing history, and
        // a webhook notifier for real alerts outside the dashboard.
        AlertRuleStore ruleStore = new AlertRuleStore();
        RuleAlertHistory ruleHistory = new RuleAlertHistory();

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // A thread pool so multiple requests can be handled at once,
        // rather than one at a time on a single thread.
        server.setExecutor(Executors.newFixedThreadPool(10));

        // Checks every 15 seconds whether an alert has just started
        // firing, and records it if so. Runs for as long as the server
        // runs, in the background.
        Thread historyThread = new Thread(() -> {
            while (true) {
                try {
                    int recentErrors = indexer.countRecentErrors(WINDOW_MINUTES * 60_000L);
                    alertHistory.checkAndRecord(recentErrors, ERROR_THRESHOLD);
                    Thread.sleep(15_000);
                } catch (Exception e) {
                    // Keep the loop alive even if one check fails.
                }
            }
        });
        historyThread.setDaemon(true);
        historyThread.start();

        // NEW: checks every user-defined rule every 15 seconds. The
        // moment a rule crosses from "fine" to "alert", we record it
        // in rule-alert history AND send a webhook notification -
        // this is what makes it a REAL alert, not just a dashboard banner.
        Thread ruleThread = new Thread(() -> {
            while (true) {
                try {
                    for (AlertRule rule : ruleStore.getRules()) {
                        int count = indexer.countRecentForRule(rule.service, rule.level, rule.windowMinutes * 60_000L);
                        boolean activeNow = count > rule.threshold;
                        if (activeNow && !rule.wasActive) {
                            ruleHistory.record(rule, count);
                            WebhookNotifier.send("LogStream alert: " + rule.describe() + " - " + count + " matched");
                        }
                        rule.wasActive = activeNow;
                    }
                    Thread.sleep(15_000);
                } catch (Exception e) {
                    // Keep the loop alive even if one check fails.
                }
            }
        });
        ruleThread.setDaemon(true);
        ruleThread.start();

        server.createContext("/api/search", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            int statusCode = 200;
            try {
                String query = getQueryParam(exchange, "q");
                List<String> results = indexer.search(query == null ? "*:*" : query, 50);
                json = toJsonArray(results);
            } catch (IllegalArgumentException | ParseException e) {
                statusCode = 400;
                json = jsonError(e);
            } catch (Exception e) {
                json = jsonError(e);
            }
            sendJson(exchange, json, statusCode);
        });

        server.createContext("/api/aggregations", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            try {
                int total = indexer.getTotalLogs();
                Map<String, Integer> byService = indexer.countByField("service");
                Map<String, Integer> byLevel = indexer.countByField("level");
                json = "{\"totalLogs\": " + total
                        + ", \"byService\": " + toJsonObject(byService)
                        + ", \"byLevel\": " + toJsonObject(byLevel) + "}";
            } catch (Exception e) {
                json = jsonError(e);
            }
            sendJson(exchange, json);
        });

        server.createContext("/api/timeline", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            try {
                Map<Long, Integer> byMinute = indexer.countByMinute();
                StringBuilder sb = new StringBuilder("[");
                int i = 0;
                for (Map.Entry<Long, Integer> entry : byMinute.entrySet()) {
                    if (i++ > 0) sb.append(", ");
                    sb.append("{\"time\": ").append(entry.getKey())
                            .append(", \"count\": ").append(entry.getValue()).append("}");
                }
                sb.append("]");
                json = sb.toString();
            } catch (Exception e) {
                json = jsonError(e);
            }
            sendJson(exchange, json);
        });

        server.createContext("/api/alerts", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            try {
                int recentErrors = indexer.countRecentErrors(WINDOW_MINUTES * 60_000L);
                boolean active = recentErrors > ERROR_THRESHOLD;
                json = "{\"active\": " + active
                        + ", \"errorCount\": " + recentErrors
                        + ", \"threshold\": " + ERROR_THRESHOLD
                        + ", \"windowMinutes\": " + WINDOW_MINUTES
                        + ", \"message\": \"" + (active
                        ? recentErrors + " ERROR logs in the last " + WINDOW_MINUTES + " minutes"
                        : "No active alerts") + "\"}";
            } catch (Exception e) {
                json = jsonError(e);
            }
            sendJson(exchange, json);
        });

        server.createContext("/api/alerts/history", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            sendJson(exchange, alertHistory.toJsonArray());
        });

        // NEW: list / create / delete user-defined alert rules.
        server.createContext("/api/alert-rules", exchange -> {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if (method.equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            int statusCode = 200;
            try {
                if (method.equals("GET")) {
                    json = ruleStore.toJsonArray();
                } else if (method.equals("POST")) {
                    String body = readBody(exchange);
                    String service = extractJsonString(body, "service");
                    String level = extractJsonString(body, "level");
                    Integer threshold = extractJsonInt(body, "threshold");
                    Integer windowMinutes = extractJsonInt(body, "windowMinutes");
                    String validationError = validateAlertRule(body, service, level, threshold, windowMinutes);
                    if (validationError != null) {
                        statusCode = 400;
                        json = "{\"error\": " + jsonString(validationError) + "}";
                    } else {
                        AlertRule created = ruleStore.add(service, level, threshold, windowMinutes);
                        json = created.toJson();
                    }
                } else if (method.equals("DELETE")) {
                    String id = getQueryParam(exchange, "id");
                    boolean removed = id != null && ruleStore.remove(id);
                    json = "{\"removed\": " + removed + "}";
                } else {
                    statusCode = 405;
                    json = "{\"error\": \"Unsupported method\"}";
                }
            } catch (Exception e) {
                json = jsonError(e);
            }
            sendJson(exchange, json, statusCode);
        });

        // NEW: history of user-defined rule alerts that have fired.
        server.createContext("/api/alert-rules/history", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            sendJson(exchange, ruleHistory.toJsonArray());
        });

        server.start();
        System.out.println("Query API running on port " + port);
    }

    private static String getQueryParam(HttpExchange exchange, String name) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // NEW: reads the request body (used by POST /api/alert-rules). No
    // JSON library in this project, so the frontend sends simple flat
    // JSON and we pull fields out with small regexes below.
    private static String readBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String extractJsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static Integer extractJsonInt(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)(?=\\s*[,}])").matcher(json);
        if (!m.find()) return null;
        try {
            return Integer.valueOf(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String validateAlertRule(String json, String service, String level,
                                            Integer threshold, Integer windowMinutes) {
        if (threshold == null || windowMinutes == null) {
            return "threshold and windowMinutes must be integers";
        }
        if (threshold < 1) {
            return "threshold must be at least 1";
        }
        if (windowMinutes < 1 || windowMinutes > 10_080) {
            return "windowMinutes must be between 1 and 10080";
        }
        if (hasJsonField(json, "service") && service == null) {
            return "service must be a string";
        }
        if (service != null && !service.isBlank() && !"*".equals(service)
                && !service.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")) {
            return "service must use letters, numbers, dots, underscores, or hyphens";
        }
        if (hasJsonField(json, "level") && level == null) {
            return "level must be a string";
        }
        if (level != null && !level.isBlank()
                && !level.matches("[A-Za-z][A-Za-z0-9_-]{0,31}")) {
            return "level must start with a letter and contain only letters, numbers, underscores, or hyphens";
        }
        return null;
    }

    private static boolean hasJsonField(String json, String key) {
        return Pattern.compile("\"" + key + "\"\\s*:").matcher(json).find();
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        sendJson(exchange, json, 200);
    }

    private static void sendJson(HttpExchange exchange, String json, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsonString(items.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonObject(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append(jsonString(entry.getKey())).append(": ").append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    private static String jsonError(Exception error) {
        String message = error.getMessage();
        return "{\"error\": " + jsonString(message == null ? "Unexpected server error" : message) + "}";
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.append('"').toString();
    }
}
