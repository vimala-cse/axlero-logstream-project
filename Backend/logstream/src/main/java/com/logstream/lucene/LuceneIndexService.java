package com.logstream.lucene;

import com.logstream.exception.LogStreamException;
import com.logstream.model.LogRecord;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LuceneIndexService implements Closeable {

    private final Directory directory;
    private final StandardAnalyzer analyzer;
    private final IndexWriter writer;

    private static final int COMMIT_BATCH_SIZE = 1000;

    private int pendingDocuments = 0;

    public LuceneIndexService(String indexPath) {

        try {

            directory = FSDirectory.open(Path.of(indexPath));

            analyzer = new StandardAnalyzer();

            IndexWriterConfig config =
                    new IndexWriterConfig(analyzer);

            config.setOpenMode(
                    IndexWriterConfig.OpenMode.CREATE_OR_APPEND
            );

            writer = new IndexWriter(directory, config);

            System.out.println(
                    "Lucene index location: " + indexPath
            );

        } catch (IOException e) {

            throw new LogStreamException(
                    "Unable to create Lucene index",
                    e
            );
        }
    }

    public synchronized void index(LogRecord record) {

        if (record == null) {

            throw new LogStreamException(
                    "Log record cannot be null"
            );
        }

        try {

            writer.addDocument(createDocument(record));

            pendingDocuments++;

            if (pendingDocuments >= COMMIT_BATCH_SIZE) {

                writer.commit();

                pendingDocuments = 0;

                System.out.println(
                        "Lucene batch committed"
                );
            }

        } catch (IOException e) {

            throw new LogStreamException(
                    "Unable to index log",
                    e
            );
        }
    }

    public synchronized void indexBatch(
            List<LogRecord> records) {

        if (records == null || records.isEmpty()) {
            return;
        }

        try {

            for (LogRecord record : records) {

                if (record != null) {

                    writer.addDocument(
                            createDocument(record)
                    );
                }
            }

            writer.commit();

            pendingDocuments = 0;

        } catch (IOException e) {

            throw new LogStreamException(
                    "Unable to index log batch",
                    e
            );
        }
    }

    private Document createDocument(LogRecord record) {

        Document document = new Document();

        document.add(
                new StringField(
                        "timestamp",
                        record.getTimestamp(),
                        org.apache.lucene.document.Field.Store.YES
                )
        );

        document.add(
                new StringField(
                        "level",
                        record.getLevel(),
                        org.apache.lucene.document.Field.Store.YES
                )
        );

        document.add(
                new StringField(
                        "service",
                        record.getService(),
                        org.apache.lucene.document.Field.Store.YES
                )
        );

        document.add(
                new TextField(
                        "message",
                        record.getMessage(),
                        org.apache.lucene.document.Field.Store.YES
                )
        );

        document.add(
                new StringField(
                        "traceId",
                        record.getTraceId(),
                        org.apache.lucene.document.Field.Store.YES
                )
        );

        document.add(
                new LongPoint(
                        "response_time",
                        record.getResponseTime()
                )
        );

        document.add(
                new StoredField(
                        "response_time",
                        record.getResponseTime()
                )
        );

        return document;
    }

    public synchronized List<LogRecord> search(
            String searchText,
            int limit) {

        if (searchText == null || searchText.isBlank()) {

            return List.of();
        }

        int safeLimit =
                Math.max(1, Math.min(limit, 100));

        try {

            writer.commit();

            pendingDocuments = 0;

            try (DirectoryReader reader =
                         DirectoryReader.open(writer)) {

                IndexSearcher searcher =
                        new IndexSearcher(reader);

                Query query =
                        buildQuery(searchText);

                long start =
                        System.nanoTime();

                var topDocs =
                        searcher.search(
                                query,
                                safeLimit
                        );

                long end =
                        System.nanoTime();

                double milliseconds =
                        (end - start) / 1_000_000.0;

                System.out.println(
                        "SEARCH -> "
                        + searchText
                        + " | RESULTS = "
                        + topDocs.scoreDocs.length
                        + " | TIME = "
                        + String.format(
                                "%.3f ms",
                                milliseconds
                        )
                );

                List<LogRecord> results =
                        new ArrayList<>();

                for (ScoreDoc scoreDoc :
                        topDocs.scoreDocs) {

                    Document document =
                            searcher.doc(scoreDoc.doc);

                    results.add(
                            LogRecord.builder()
                                    .timestamp(
                                            document.get(
                                                    "timestamp"
                                            )
                                    )
                                    .level(
                                            document.get(
                                                    "level"
                                            )
                                    )
                                    .service(
                                            document.get(
                                                    "service"
                                            )
                                    )
                                    .message(
                                            document.get(
                                                    "message"
                                            )
                                    )
                                    .traceId(
                                            document.get(
                                                    "traceId"
                                            )
                                    )
                                    .responseTime(
                                            document.getField(
                                                    "response_time"
                                            ).numericValue()
                                                    .longValue()
                                    )
                                    .build()
                    );
                }

                return results;
            }

        } catch (Exception e) {

            throw new LogStreamException(
                    "Lucene search failed",
                    e
            );
        }
    }

    private Query buildQuery(String searchText) {

        String[] clauses =
                searchText.split("(?i)\\s+AND\\s+");

        BooleanQuery.Builder builder =
                new BooleanQuery.Builder();

        for (String clause : clauses) {

            clause = clause.trim();

            if (clause.isBlank()) {
                continue;
            }

            if (clause.matches(
                    "(?i)response_time\\s*>\\s*\\d+")) {

                String number =
                        clause.replaceAll(
                                "(?i)response_time\\s*>\\s*",
                                ""
                        );

                long value =
                        Long.parseLong(number);

                Query query =
                        LongPoint.newRangeQuery(
                                "response_time",
                                value + 1,
                                Long.MAX_VALUE
                        );

                builder.add(
                        query,
                        BooleanClause.Occur.MUST
                );

            } else if (clause.matches(
                    "(?i)response_time\\s*>=\\s*\\d+")) {

                String number =
                        clause.replaceAll(
                                "(?i)response_time\\s*>=\\s*",
                                ""
                        );

                long value =
                        Long.parseLong(number);

                Query query =
                        LongPoint.newRangeQuery(
                                "response_time",
                                value,
                                Long.MAX_VALUE
                        );

                builder.add(
                        query,
                        BooleanClause.Occur.MUST
                );

            } else if (clause.contains(":")) {

                String[] parts =
                        clause.split(":", 2);

                String field =
                        parts[0].trim();

                String value =
                        parts[1].trim();

                if (field.equals("level")
                        || field.equals("service")
                        || field.equals("traceId")) {

                    builder.add(
                            new TermQuery(
                                    new Term(
                                            field,
                                            value
                                    )
                            ),
                            BooleanClause.Occur.MUST
                    );

                } else if (field.equals("message")) {

                    builder.add(
                            new TermQuery(
                                    new Term(
                                            "message",
                                            value.toLowerCase()
                                    )
                            ),
                            BooleanClause.Occur.MUST
                    );

                } else {

                    builder.add(
                            new TermQuery(
                                    new Term(
                                            "message",
                                            clause
                                    )
                            ),
                            BooleanClause.Occur.MUST
                    );
                }

            } else {

                builder.add(
                        new TermQuery(
                                new Term(
                                        "message",
                                        clause.toLowerCase()
                                )
                        ),
                        BooleanClause.Occur.MUST
                );
            }
        }

        Query query = builder.build();

        return builder.build();
    }

    public synchronized long getDocumentCount() {

        try {

            writer.commit();

            try (DirectoryReader reader =
                         DirectoryReader.open(writer)) {

                return reader.numDocs();
            }

        } catch (IOException e) {

            throw new LogStreamException(
                    "Unable to count Lucene documents",
                    e
            );
        }
    }

    @Override
    public void close() {

        try {

            writer.commit();

            writer.close();

            analyzer.close();

            directory.close();

            System.out.println(
                    "Lucene resources closed"
            );

        } catch (IOException e) {

            throw new LogStreamException(
                    "Unable to close Lucene",
                    e
            );
        }
    }
}