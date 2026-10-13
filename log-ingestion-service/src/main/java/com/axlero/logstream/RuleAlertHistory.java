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
              .append(", \"ruleId\": \"").append(e[1]).append("\"")
              .append(", \"service\": \"").append(e[2]).append("\"")
              .append(", \"level\": \"").append(e[3]).append("\"")
              .append(", \"threshold\": ").append(e[4])
              .append(", \"windowMinutes\": ").append(e[5])
              .append(", \"errorCount\": ").append(e[6])
              .append(", \"message\": \"").append(e[6]).append(" ").append(e[3])
              .append(" logs in ").append(e[5]).append(" min (").append(who).append(")\"}");
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
                String[] parts = line.split(",");
                if (parts.length == 7) entries.add(parts);
            }
            // File is saved oldest-first; we keep newest-first in memory.
            Collections.reverse(entries);
        } catch (IOException ignored) {
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
