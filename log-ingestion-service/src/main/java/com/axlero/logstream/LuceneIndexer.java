package com.axlero.logstream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This class is our "filing system". Every log that arrives gets
// saved here in a special format that can be searched instantly,
// even if there are millions of logs.
public class LuceneIndexer {

    private static final String INDEX_DIR = "lucene-index";
    private static final Pattern RESPONSE_TIME_FILTER = Pattern.compile(
            "\\bresponse_time\\s*(>=|>|<=|<|=)\\s*(\\d+)(?=\\s|$|\\))",
            Pattern.CASE_INSENSITIVE);

    private final Directory directory;
    private final Analyzer analyzer;

    public LuceneIndexer() throws Exception {
        this.directory = FSDirectory.open(Path.of(INDEX_DIR));

        Map<String, Analyzer> perField = new HashMap<>();
        perField.put("service", new KeywordAnalyzer());
        perField.put("level", new KeywordAnalyzer());
        this.analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), perField);
    }

    public void addLog(String serviceName, String level, String message, long timestamp, Long responseTimeMs) throws Exception {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            Document doc = new Document();
            doc.add(new StringField("service", serviceName, Field.Store.YES));
            doc.add(new StringField("level", level, Field.Store.YES));
            doc.add(new TextField("message", message, Field.Store.YES));
            doc.add(new LongPoint("timestamp", timestamp));
            doc.add(new StoredField("timestamp_stored", timestamp));
            if (responseTimeMs != null) {
                if (responseTimeMs < 0) throw new IllegalArgumentException("response_time_ms must not be negative");
                doc.add(new LongPoint("response_time", responseTimeMs));
                doc.add(new StoredField("response_time_stored", responseTimeMs));
            }
            writer.addDocument(doc);
        }
    }

    public List<String> search(String queryText, int maxResults) throws Exception {
        List<String> results = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("message", analyzer);
            Query query = parseSearchQuery(parser, queryText);
            TopDocs topDocs = searcher.search(query, maxResults);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                results.add(formatLine(doc));
            }
        }
        return results;
    }

    private Query parseSearchQuery(QueryParser parser, String queryText) throws Exception {
        Matcher matcher = RESPONSE_TIME_FILTER.matcher(queryText);
        if (!matcher.find()) {
            if (queryText.toLowerCase(Locale.ROOT).contains("response_time")) {
                throw new IllegalArgumentException("Use response_time followed by >, >=, <, <=, or = and a number of milliseconds");
            }
            return parser.parse(queryText);
        }
        String operator = matcher.group(1);
        long value;
        try {
            value = Long.parseLong(matcher.group(2));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("response_time value is too large", e);
        }
        if (matcher.find()) {
            throw new IllegalArgumentException("Only one response_time comparison is supported per search");
        }
        if (Pattern.compile("\\bOR\\b", Pattern.CASE_INSENSITIVE).matcher(queryText).find()) {
            throw new IllegalArgumentException("Combine response_time comparisons with other filters using AND");
        }

        Matcher location = RESPONSE_TIME_FILTER.matcher(queryText);
        location.find();
        int filterStart = location.start();
        int filterEnd = location.end();
        String before = queryText.substring(0, filterStart).replaceFirst("(?i)\\s+AND\\s*$", "").trim();
        String after = queryText.substring(filterEnd).replaceFirst("(?i)^\\s*AND\\s+", "").trim();
        String textQuery = before.isEmpty() ? after : after.isEmpty() ? before : before + " AND " + after;

        BooleanQuery.Builder combined = new BooleanQuery.Builder();
        if (!textQuery.isEmpty()) combined.add(parser.parse(textQuery), BooleanClause.Occur.MUST);
        combined.add(responseTimeQuery(operator, value), BooleanClause.Occur.MUST);
        return combined.build();
    }

    private Query responseTimeQuery(String operator, long value) {
        return switch (operator) {
            case ">" -> value == Long.MAX_VALUE
                    ? new MatchNoDocsQuery()
                    : LongPoint.newRangeQuery("response_time", value + 1, Long.MAX_VALUE);
            case ">=" -> LongPoint.newRangeQuery("response_time", value, Long.MAX_VALUE);
            case "<" -> value == 0
                    ? new MatchNoDocsQuery()
                    : LongPoint.newRangeQuery("response_time", 0, value - 1);
            case "<=" -> LongPoint.newRangeQuery("response_time", 0, value);
            case "=" -> LongPoint.newExactQuery("response_time", value);
            default -> throw new IllegalArgumentException("Unsupported response_time comparison: " + operator);
        };
    }

    public int getTotalLogs() throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        }
    }

    public Map<String, Integer> countByField(String fieldName) throws Exception {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String value = doc.get(fieldName);
                if (value != null) counts.merge(value, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) sorted.put(e.getKey(), e.getValue());
        return sorted;
    }

    public Map<Long, Integer> countByMinute() throws Exception {
        Map<Long, Integer> counts = new TreeMap<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String tsStr = doc.get("timestamp_stored");
                if (tsStr != null) {
                    long ts = Long.parseLong(tsStr);
                    long minuteBucket = (ts / 60000L) * 60000L;
                    counts.merge(minuteBucket, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    public int countRecentErrors(long withinMillis) throws Exception {
        long cutoff = System.currentTimeMillis() - withinMillis;
        int count = 0;
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String level = doc.get("level");
                String tsStr = doc.get("timestamp_stored");
                if ("ERROR".equals(level) && tsStr != null) {
                    long ts = Long.parseLong(tsStr);
                    if (ts >= cutoff) count++;
                }
            }
        }
        return count;
    }

    // ===================== NEW: for user-defined alert rules =====================

    // Same idea as countRecentErrors, but generalized: a rule can
    // restrict to one service (or "*" for any), and to any log level
    // (not just ERROR). Used by QueryApiServer's rule-checking loop.
    public int countRecentForRule(String service, String level, long withinMillis) throws Exception {
        long cutoff = System.currentTimeMillis() - withinMillis;
        int count = 0;
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String docLevel = doc.get("level");
                String docService = doc.get("service");
                String tsStr = doc.get("timestamp_stored");
                if (tsStr == null) continue;
                if (level != null && !level.equals(docLevel)) continue;
                if (service != null && !"*".equals(service) && !service.equals(docService)) continue;
                long ts = Long.parseLong(tsStr);
                if (ts >= cutoff) count++;
            }
        }
        return count;
    }

    // ===================== NEW: for Live Tail =====================

    // Returns every log with a timestamp strictly after `sinceMillis`,
    // oldest first. QueryApiServer calls this every couple of seconds
    // on the /api/stream endpoint, so the dashboard's Live Tail page
    // can show new logs as they arrive, without the person refreshing.
    //
    // We fetch matching docs with a range query, then sort them by
    // timestamp in plain Java - this avoids needing extra index fields
    // just for sorting, which would require re-indexing old data.
    public List<String> getLogsSince(long sinceMillis) throws Exception {
        List<Document> matched = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = LongPoint.newRangeQuery("timestamp", sinceMillis + 1, Long.MAX_VALUE);
            TopDocs topDocs = searcher.search(query, 200);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                matched.add(searcher.doc(sd.doc));
            }
        }
        matched.sort(Comparator.comparingLong(d -> Long.parseLong(d.get("timestamp_stored"))));

        List<String> results = new ArrayList<>();
        for (Document doc : matched) {
            results.add(formatLine(doc));
        }
        return results;
    }

    private String formatLine(Document doc) {
        String responseTime = doc.get("response_time_stored");
        return "[" + doc.get("level") + "] "
                + doc.get("service") + ": "
                + doc.get("message")
                + (responseTime == null ? "" : " (response_time: " + responseTime + " ms)")
                + " (time: " + doc.get("timestamp_stored") + ")";
    }
}
