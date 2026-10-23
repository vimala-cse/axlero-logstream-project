package com.axlero.logstream;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Records every time a user-defined alert rule has fired - separate
// from AlertHistory.java, which only tracks the one built-in global
// alert. Same append-and-save-to-file pattern.
public class RuleAlertHistory {

    private static final String FILE_NAME = "rule-alert-history.log";
    private static final int MAX_ENTRIES = 50;

    // Each entry: [time, ruleId, service, level, threshold, windowMinutes, errorCount]
    private final List<String[]> entries = new ArrayList<>();

    public RuleAlertHistory() {
        load();
    }

    public synchronized void record(AlertRule rule, int errorCount) {
        entries.add(0, new String[]{
                String.valueOf(System.currentTimeMillis()),
                rule.id, rule.service, rule.level,
                String.valueOf(rule.threshold), String.valueOf(rule.windowMinutes),
                String.valueOf(errorCount)
        });
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        save();
    }

    public synchronized String toJsonArray() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(", ");
            String[] e = entries.get(i);
            String who = "*".equals(e[2]) ? "any service" : e[2];
            sb.append("{\"time\": ").append(e[0])
              .append(", \"ruleId\": ").append(JsonStrings.quote(e[1]))
              .append(", \"service\": ").append(JsonStrings.quote(e[2]))
              .append(", \"level\": ").append(JsonStrings.quote(e[3]))
              .append(", \"threshold\": ").append(e[4])
              .append(", \"windowMinutes\": ").append(e[5])
              .append(", \"errorCount\": ").append(e[6])
              .append(", \"message\": ").append(JsonStrings.quote(
                      e[6] + " " + e[3] + " logs in " + e[5] + " min (" + who + ")"))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private void load() {
        Path path = Path.of(FILE_NAME);
        if (!Files.exists(path)) return;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.split(",", -1);
                if (parts.length == 7 && isValidEntry(parts)) entries.add(parts);
            }
            // File is saved oldest-first; we keep newest-first in memory.
            Collections.reverse(entries);
        } catch (IOException ignored) {
        }
    }

    private boolean isValidEntry(String[] parts) {
        try {
            long time = Long.parseLong(parts[0]);
            int threshold = Integer.parseInt(parts[4]);
            int windowMinutes = Integer.parseInt(parts[5]);
            int errorCount = Integer.parseInt(parts[6]);
            return time >= 0 && threshold >= 1 && windowMinutes >= 1
                    && windowMinutes <= 10_080 && errorCount >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    private void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(FILE_NAME), StandardCharsets.UTF_8)) {
            List<String[]> oldestFirst = new ArrayList<>(entries);
            Collections.reverse(oldestFirst);
            for (String[] e : oldestFirst) {
                writer.write(String.join(",", e));
                writer.newLine();
            }
        } catch (IOException ignored) {
        }
    }
}
