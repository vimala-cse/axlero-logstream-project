package com.axlero.logstream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

// This is the BRIDGE between our gRPC backend and the React frontend.
//
// Endpoints:
//   GET /api/aggregations    -> Overview page: stat cards + service/level charts
//   GET /api/search?q=...    -> Logs page: search / filter logs
//   GET /api/timeline        -> Analytics page: "Log Volume" line chart
//   GET /api/alerts          -> Alerts page: whether an alert is active right now
//   GET /api/alerts/history  -> Alerts page: past alerts that have fired
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

        server.createContext("/api/search", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            try {
                String query = getQueryParam(exchange, "q");
                List<String> results = indexer.search(query == null ? "*:*" : query, 50);
                json = toJsonArray(results);
            } catch (Exception e) {
                json = "{\"error\": \"" + e.getMessage() + "\"}";
            }
            sendJson(exchange, json);
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
                json = "{\"error\": \"" + e.getMessage() + "\"}";
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
                json = "{\"error\": \"" + e.getMessage() + "\"}";
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
                json = "{\"error\": \"" + e.getMessage() + "\"}";
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

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonObject(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }
}
