package com.axlero.logstream;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

// Holds every user-defined alert rule, in memory, and saves them to a
// small file so they survive a server restart - same pattern as
// AlertHistory.java. QueryApiServer's background thread checks every
// rule here on each pass.
public class AlertRuleStore {

    private static final String FILE_NAME = "alert-rules.log";

    private final List<AlertRule> rules = new CopyOnWriteArrayList<>();

    public AlertRuleStore() {
        load();
    }

    public List<AlertRule> getRules() {
        return rules;
    }

    public synchronized AlertRule add(String service, String level, int threshold, int windowMinutes) {
        AlertRule candidate = new AlertRule("pending", service, level, threshold, windowMinutes);
        for (AlertRule existing : rules) {
            if (existing.service.equals(candidate.service)
                    && existing.level.equals(candidate.level)
                    && existing.threshold == candidate.threshold
                    && existing.windowMinutes == candidate.windowMinutes) {
                return null;
            }
        }

        AlertRule rule = new AlertRule("r" + UUID.randomUUID(),
                candidate.service, candidate.level, threshold, windowMinutes);
        rules.add(rule);
        save();
        return rule;
    }
    public boolean remove(String id) {
        boolean removed = rules.removeIf(r -> r.id.equals(id));
        if (removed) save();
        return removed;
    }

    public String toJsonArray() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(rules.get(i).toJson());
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
                AlertRule rule = AlertRule.fromCsvLine(line);
                if (rule != null) rules.add(rule);
            }
        } catch (IOException ignored) {
            // If the file is missing or unreadable, start with no rules.
        }
    }

    private void save() {
        Path target = Path.of(FILE_NAME).toAbsolutePath();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), "alert-rules-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (AlertRule rule : new ArrayList<>(rules)) {
                    writer.write(rule.toCsvLine());
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.out.println("Warning: failed to save alert rules - " + e.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The next save can safely replace a leftover temporary file.
                }
            }
        }
    }
}
