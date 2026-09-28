package com.logstream.analytics;

import com.logstream.lucene.LuceneIndexService;
import com.logstream.model.LogRecord;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final LuceneIndexService indexService;

    public AnalyticsService(LuceneIndexService indexService) {
        this.indexService = indexService;
    }

    /*
     * WEEK 3
     * Runs every 10 seconds so that the result
     * can be seen immediately during the project demo.
     */
    @Scheduled(fixedRate = 10000)
    public void showAnalytics() {

        List<LogRecord> logs = indexService.getAllLogs(100000);

        if (logs.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("==============================================");
        System.out.println("              WEEK 3 - ANALYTICS");
        System.out.println("==============================================");

        System.out.println("Total Logs       : " + logs.size());

        int errorCount = 0;
        int warnCount = 0;
        int infoCount = 0;

        long totalResponseTime = 0;

        Map<String, Integer> serviceCounts = new HashMap<>();

        for (LogRecord log : logs) {

            // Level statistics
            if ("ERROR".equalsIgnoreCase(log.getLevel())) {
                errorCount++;
            } else if ("WARN".equalsIgnoreCase(log.getLevel())) {
                warnCount++;
            } else if ("INFO".equalsIgnoreCase(log.getLevel())) {
                infoCount++;
            }

            // Response time
            totalResponseTime += log.getResponseTime();

            // Service statistics
            serviceCounts.merge(
                    log.getService(),
                    1,
                    Integer::sum
            );
        }

        double averageResponseTime = 0;

        if (!logs.isEmpty()) {
            averageResponseTime =
                    (double) totalResponseTime / logs.size();
        }

        System.out.println("----------------------------------------------");
        System.out.println("LOG LEVEL STATISTICS");
        System.out.println("----------------------------------------------");
        System.out.println("ERROR            : " + errorCount);
        System.out.println("WARN             : " + warnCount);
        System.out.println("INFO             : " + infoCount);

        System.out.println("----------------------------------------------");
        System.out.println("SERVICE STATISTICS");
        System.out.println("----------------------------------------------");

        for (Map.Entry<String, Integer> entry :
                serviceCounts.entrySet()) {

            System.out.println(
                    entry.getKey() + " : " + entry.getValue()
            );
        }

        System.out.println("----------------------------------------------");
        System.out.println(
                "Average Response Time : "
                        + String.format("%.2f", averageResponseTime)
                        + " ms"
        );

        showTimeSeries(logs);

        System.out.println("==============================================");
    }

    /*
     * WEEK 3
     * Simple time-series information for the
     * last five minutes.
     */
    private void showTimeSeries(List<LogRecord> logs) {

        Map<String, Integer> minuteCounts = new HashMap<>();

        Instant now = Instant.now();

        for (LogRecord log : logs) {

            try {

                Instant logTime =
                        Instant.parse(log.getTimestamp());

                long minutes =
                        Duration.between(logTime, now).toMinutes();

                if (minutes >= 0 && minutes < 5) {

                    String key =
                            minutes + " minute(s) ago";

                    minuteCounts.merge(
                            key,
                            1,
                            Integer::sum
                    );
                }

            } catch (Exception e) {
                // Ignore invalid timestamp for time-series calculation
            }
        }

        System.out.println("----------------------------------------------");
        System.out.println("TIME-SERIES - LAST 5 MINUTES");
        System.out.println("----------------------------------------------");

        if (minuteCounts.isEmpty()) {

            System.out.println(
                    "No logs available in the last 5 minutes."
            );

        } else {

            for (Map.Entry<String, Integer> entry :
                    minuteCounts.entrySet()) {

                System.out.println(
                        entry.getKey()
                                + " : "
                                + entry.getValue()
                                + " logs"
                );
            }
        }
    }

    /*
     * WEEK 4
     *
     * Checks ERROR logs generated during the
     * last five minutes.
     *
     * Runs every 10 seconds for demonstration.
     */
    @Scheduled(fixedRate = 10000)
    public void checkAlerts() {

        List<LogRecord> logs =
                indexService.getAllLogs(100000);

        if (logs.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        int recentErrors = 0;

        for (LogRecord log : logs) {

            try {

                Instant logTime =
                        Instant.parse(log.getTimestamp());

                long seconds =
                        Duration.between(logTime, now).getSeconds();

                if (seconds >= 0
                        && seconds <= 300
                        && "ERROR".equalsIgnoreCase(
                                log.getLevel())) {

                    recentErrors++;
                }

            } catch (Exception e) {
                // Ignore invalid timestamps
            }
        }

        System.out.println();
        System.out.println("==============================================");
        System.out.println("              WEEK 4 - ALERTING");
        System.out.println("==============================================");

        System.out.println(
                "ERROR logs in last 5 minutes : "
                        + recentErrors
        );

        if (recentErrors > 100) {

            System.out.println(
                    "ALERT: ERROR count exceeded 100 in 5 minutes!"
            );

        } else {

            System.out.println(
                    "Alert Status : No alert"
            );
        }

        System.out.println("==============================================");
    }
}