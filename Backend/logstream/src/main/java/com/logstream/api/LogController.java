package com.logstream.api;

import com.logstream.lucene.LuceneIndexService;
import com.logstream.model.LogRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final int MAX_LIMIT = 500;

    private final LuceneIndexService indexService;

    public LogController(LuceneIndexService indexService) {
        this.indexService = indexService;
    }

    @GetMapping
    public List<LogRecord> listLogs(
            @RequestParam(defaultValue = "200") int limit) {
        return indexService.getAllLogs(safeLimit(limit));
    }

    @GetMapping("/search")
    public List<LogRecord> searchLogs(
            @RequestParam String query,
            @RequestParam(defaultValue = "200") int limit) {
        if (query.isBlank()) {
            return indexService.getAllLogs(safeLimit(limit));
        }
        return indexService.search(query, safeLimit(limit));
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}