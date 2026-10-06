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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This is the BRIDGE between our gRPC backend and the React frontend.
//
// Endpoints:
//   GET /api/aggregations  -> Overview page: stat cards + service/level charts
//   GET /api/search?q=...  -> Logs page: search / filter logs
//   GET /api/timeline      -> Analytics page: "Log Volume" line chart
//   GET /api/alerts        -> Alerts page: whether an alert is active
//   GET /api/stream        -> Live Tail page: a live, ongoing stream of new logs
public class QueryApiServer {

    private static final int ERROR_THRESHOLD = 3;
    private static final int WINDOW_MINUTES = 5;
    private static final Pattern TIME_PATTERN = Pattern.compile("\\(time: (\\d+)\\)");

    public static void main(String[] args) throws Exception {
        LuceneIndexer indexer = new LuceneIndexer();

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // IMPORTANT: /api/stream below holds its connection open for a
        // long time (that's how Live Tail works). The default HttpServer
        // executor handles requests one at a time on a single thread, so
        // without a thread pool here, one open Live Tail tab would freeze
        // every other request (search, aggregations, everything). A pool
        // lets each request run on its own thread.
        server.setExecutor(Executors.newFixedThreadPool(10));

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

        // NEW: Live Tail. This uses Server-Sent Events (SSE) - a simple,
        // one-way "push" channel built into every browser (the EventSource
        // API). We keep this HTTP response open, and every 2 seconds we
        // check the index for anything newer than the last log we sent,
        // and push it down the same open connection. No manual refresh
        // needed on the frontend - new logs just appear.
        server.createContext("/api/stream", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            long lastSeen = System.currentTimeMillis();
            try {
                while (true) {
                    List<String> newLogs = indexer.getLogsSince(lastSeen);
                    for (String line : newLogs) {
                        os.write(("data: " + line + "\n\n").getBytes(StandardCharsets.UTF_8));
                        Matcher m = TIME_PATTERN.matcher(line);
                        if (m.find()) {
                            long t = Long.parseLong(m.group(1));
                            if (t > lastSeen) lastSeen = t;
                        }
                    }
                    os.flush();
                    Thread.sleep(2000);
                }
            } catch (IOException closed) {
                // The browser closed the tab or navigated away - this is
                // the normal way this loop ends, nothing to worry about.
            } finally {
                try { os.close(); } catch (IOException ignored) { }
            }
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
