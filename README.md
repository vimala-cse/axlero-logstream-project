# LogStream — Distributed Log Analytics & Alerting Platform

A self-hosted log analytics tool that ingests logs from multiple
services in real time, indexes them for instant search, visualizes
trends on a live dashboard, and flags unusual error activity — built
as part of the Axlero Solutions internship (Project 2: Observability
& Big Data).

---

## 1. The Problem

Modern systems are made up of many small services, and each one
produces its own stream of logs. Storing and searching through
terabytes of these logs using a standard relational database (like
PostgreSQL) doesn't scale — a single keyword search can mean scanning
every row. Organizations need something that can take in millions of
log lines per minute, index them instantly, and surface problems the
moment they happen — the same idea behind tools like the ELK stack
(Elasticsearch, Logstash, Kibana), built here from scratch.

## 2. Our Solution — How It Works End to End

1. **A service produces a log** (e.g. `payment-service` logs an
   `ERROR: Payment gateway timeout`).
2. That log is sent to our **gRPC ingestion server**
   (`LogIngestionServer`), which is built for fast, high-volume,
   binary log ingestion.
3. The log is immediately:
   - **Indexed into Apache Lucene** (the same search engine that
     powers Elasticsearch) so it becomes instantly searchable, and
   - **Pushed live to any open dashboard** over a WebSocket, for the
     Live Tail feature — this path skips the index entirely, so it's
     never delayed by search traffic.
4. An engineer opens the **React dashboard**, which talks to the
   backend through a REST bridge (`QueryApiServer`). From here they
   can:
   - Search logs with a simple query language, e.g.
     `level:ERROR AND service:payment-service`
   - View stat cards and charts — total logs, breakdown by service,
     breakdown by level, volume over time
   - Watch a background process check the recent error rate; if it
     crosses a threshold, an alert appears on the dashboard and is
     saved to a history log
   - Watch new logs stream onto the **Live Tail** page the instant
     they arrive, with no refresh needed

That's the whole loop: a log is born in a microservice, and within
moments it's searchable, visualized, and — if it's part of a spike —
already flagged.

## 3. Key Features

| Feature | Description |
|---|---|
| High-throughput ingestion | gRPC server built for fast, binary log ingestion from multiple services |
| Full-text & field search | Apache Lucene index; query by level, service, or free-text message |
| Live analytics | Total logs, per-service and per-level breakdowns, volume-over-time chart |
| Alerting | Background check flags when the error rate crosses a threshold |
| Alert history | Past alerts are saved and viewable, not just the current moment |
| Live Tail | Logs stream to the dashboard in real time via WebSocket, bypassing the search index |
| REST API + Postman collection | Every piece of data the dashboard shows is also available as a documented REST endpoint |

## 4. Architecture

```
 Microservices                      LogStream Backend
 (log producers)
      |
      | gRPC :9090           ┌────────────────────────┐
      ├──SendLog() ─────────▶│   LogIngestionServer     │
      |                      │                          │
      |                      │  1. writes to Lucene ────┼──────▶ ┌───────────────┐
      |                      │  2. broadcasts to  ───────┼───┐    │ Lucene index   │
      |                      │     Live Tail             │   │    │ (on disk)      │
      |                      └────────────────────────┘   │    └───────┬───────┘
      |                                                    │            │ reads
      |                                                    ▼            ▼
      |                                        ┌─────────────────┐ ┌─────────────────┐
      |                                        │ LiveTailWebSocket│ │  QueryApiServer  │
      |                                        │ Server (:8081)   │ │  REST (:8080)    │
      |                                        └────────┬────────┘ └────────┬────────┘
      |                                                  │ push logs         │ JSON
      |                                                  ▼                   ▼
      |                                        ┌─────────────────────────────────────┐
      |                                        │       React Dashboard (Vite, :5173)    │
      |                                        │  Overview · Logs · Analytics · Alerts  │
      |                                        │       · Services · Live Tail           │
      |                                        └─────────────────────────────────────┘
```

**Why does Live Tail bypass the search index?** Browsers can't speak
gRPC, so everything the dashboard needs normally goes through
`QueryApiServer`, which reads from Lucene. But Live Tail needs to feel
instant and must never be slowed down by search traffic — so instead
of polling the index, `LogIngestionServer` pushes each log straight to
connected browsers the moment it's received, over a separate
WebSocket connection. Search and Live Tail never compete with each
other.

## 5. Tech Stack

| Layer | Technology |
|---|---|
| Log ingestion | Java, gRPC, Protocol Buffers |
| Search / indexing | Apache Lucene 9.10 |
| REST bridge | Java (`com.sun.net.httpserver`) — no extra framework |
| Live Tail | Java-WebSocket |
| Frontend | React 18, Vite |
| Build tools | Maven (backend), npm (frontend) |
| API testing | Postman |
| Version control | Git / GitHub |

## 6. Project Structure

```
axlero-logstream-project/
├── log-ingestion-service/            Backend (Java)
│   ├── src/main/java/com/axlero/logstream/
│   │   ├── LogIngestionServer.java      gRPC server; also starts Live Tail
│   │   ├── LiveTailWebSocketServer.java WebSocket push server (:8081)
│   │   ├── LuceneIndexer.java           Indexing, search, aggregations
│   │   ├── QueryApiServer.java          REST bridge (:8080)
│   │   ├── AlertHistory.java            Tracks and saves past alerts
│   │   ├── AlertMonitor.java            Console alert check
│   │   ├── AggregationDemo.java         Console aggregation demo
│   │   ├── BulkTestClient.java          Sends sample logs for testing
│   │   └── SearchDemo.java              Console search demo
│   ├── src/main/proto/log_service.proto
│   └── pom.xml
│
├── logstream-dashboard/              Frontend (React)
│   ├── src/
│   │   ├── App.jsx                      All pages and data-fetching logic
│   │   ├── App.css                      Styling, responsive layout
│   │   └── main.jsx
│   ├── index.html
│   └── package.json
│
└── postman/                          Postman collection for the REST API
    └── LogStream.postman_collection.json
```

## 7. Running It — Start to Finish

### Prerequisites

- Java 17+ (21 also works)
- Maven (handled automatically by IntelliJ)
- Node.js and npm

### Step 1 — Start the backend

Open `log-ingestion-service` as a Maven project and run these three
files **in this order**, leaving each one running:

1. `LogIngestionServer.java` — starts the gRPC server (port 9090) and
   the Live Tail WebSocket server (port 8081)
2. `BulkTestClient.java` — sends 15 sample logs across 6 services and
   3 levels (run once; run again any time for more sample data)
3. `QueryApiServer.java` — starts the REST API (port 8080) —
   **keep this running**

Confirm it's working: open `http://localhost:8080/api/aggregations`
in a browser — you should see JSON data.

### Step 2 — Start the frontend

```
cd logstream-dashboard
npm install
npm run dev
```

Open the link shown (usually `http://localhost:5173`). If the backend
isn't running, you'll see a clear "could not reach the backend"
message instead of a blank page.

### Step 3 — Explore

- **Overview** — stat cards, volume chart, recent logs
- **Logs** — full search
- **Analytics** — bigger charts, including the level breakdown
- **Alerts** — current status + history
- **Services** — log counts per service
- **Live Tail** — watch logs arrive in real time (run `BulkTestClient`
  again while this tab is open to see it in action)

## 8. API Reference

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/aggregations` | Total logs, counts by service, counts by level |
| GET | `/api/search?q=<query>` | Search logs — try `level:ERROR`, `message:timeout`, `service:payment-service` |
| GET | `/api/timeline` | Logs per minute, for the volume chart |
| GET | `/api/alerts` | Whether an alert is active right now |
| GET | `/api/alerts/history` | Past alerts that have fired |
| WS | `ws://localhost:8081` | Live Tail — pushes each new log the instant it's received |

A ready-made **Postman collection** for the 5 REST endpoints is at
`postman/LogStream.postman_collection.json`. Import it into Postman
and every request is pre-filled — no typing URLs by hand. It uses a
`baseUrl` variable (default `http://localhost:8080`) so it's easy to
re-point at a deployed backend later.

## 9. Week-by-Week Development Plan

### Backend

| Week | Focus |
|---|---|
| 1 | gRPC server to receive logs; protobuf schema for a log message |
| 2 | Apache Lucene integration — parsing, analyzing, and indexing logs |
| 3 | Aggregations — counts by service/level, and volume-per-minute for the charts |
| 4 | Alerting, REST bridge for the frontend, Live Tail (WebSocket), alert history |

### Frontend

| Week | Focus |
|---|---|
| 1 | Project setup; basic search bar and log table (sample data) |
| 2 | Search bar connected to the real backend |
| 3 | Stat cards and charts connected to real data |
| 4 | Alerts page, Services page, Live Tail page, mobile-responsive layout, visual polish |

## 10. Scope Decisions — What We Simplified, and Why

Being upfront about this, since it's a deliberate choice, not an
oversight:

The project brief describes the alerting engine as running
**user-defined queries on a schedule and triggering a webhook or
email** when a threshold is breached. What we built is a working
**threshold-based alert check** — a background process that checks
the recent error count every 15 seconds and raises an alert (shown on
the dashboard, saved to history) when it crosses a fixed threshold.

What we did **not** build: letting a user define their own alert
rules (e.g. "errors > 100 in 5 minutes" as a saved, editable query),
and triggering an actual webhook or email when an alert fires. We
scoped this out to keep within the project timeline, and it's listed
below as the clear next step.

## 11. Team

| Name | Role |
|---|---|
| **Vimala** (Team Lead) | Backend (gRPC, Lucene, aggregations, alerting, Live Tail, REST bridge), Frontend dashboard, Testing |
| Doni Navaneeth | Backend |
| Shoyab Ahamed | Frontend |

## 12. Deployment

Planned on [Render](https://render.com) — backend as a Docker web
service, frontend as a static site. Docker setup is ready; this
section will be updated with live URLs once deployed.

## 13. Future Improvements

- **User-defined alert rules** — let someone configure their own
  conditions (e.g. "`count of ERROR > 100 in 5 mins` for
  `billing-api`") instead of one fixed threshold
- **Real webhook/email alerts** — actually notify someone outside the
  dashboard when an alert fires, not just show it in the UI
- Persisting logs somewhere durable across restarts (currently the
  Lucene index lives on local disk)
- Role-based access, if this were ever used by more than one team
