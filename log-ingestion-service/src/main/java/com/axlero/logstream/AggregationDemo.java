package com.axlero.logstream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

// Run this AFTER you've sent some logs (TestClient or BulkTestClient).
// This is the "Week 3" proof: it counts up the logs already indexed
// and prints a simple text bar chart in the console, then saves the
// same numbers as a JSON file. This JSON is what the React dashboard
// will read later, so what's on screen matches what we calculate here.
public class AggregationDemo {
    public static void main(String[] args) throws Exception {
        LuceneIndexer indexer = new LuceneIndexer();

        int total = indexer.getTotalLogs();
        System.out.println("Total logs indexed: " + total);

        System.out.println();
        System.out.println("=== Logs by service ===");
        Map<String, Integer> byService = indexer.countByField("service");
        printBarChart(byService);

        System.out.println();
        System.out.println("=== Logs by level ===");
        Map<String, Integer> byLevel = indexer.countByField("level");
        printBarChart(byLevel);

        String json = toJson(total, byService, byLevel);
        Files.writeString(Path.of("aggregation-output.json"), json);

        System.out.println();
        System.out.println("Saved chart data to aggregation-output.json");
    }

    // Prints something like:
    // payment-service    3    ***
    // auth-service        2    **
    private static void printBarChart(Map<String, Integer> counts) {
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String bar = "*".repeat(entry.getValue());
            System.out.printf("%-20s %-4d %s%n", entry.getKey(), entry.getValue(), bar);
        }
    }

    private static String toJson(int total, Map<String, Integer> byService, Map<String, Integer> byLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"totalLogs\": ").append(total).append(",\n");
        sb.append("  \"byService\": ").append(mapToJson(byService)).append(",\n");
        sb.append("  \"byLevel\": ").append(mapToJson(byLevel)).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String mapToJson(Map<String, Integer> map) {
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
