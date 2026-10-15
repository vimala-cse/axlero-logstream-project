# LogStream — Distributed Log Analytics & Alerting Platform

A local prototype for log analytics. It accepts logs over gRPC, indexes
them with Apache Lucene, and displays search results, basic analytics,
alerts, and a live stream in a React dashboard. This project was built
as part of the Axlero Solutions internship (Project 2: Observability
& Big Data).

---

## 1. The Problem

Modern systems are made up of many small services, and each one
produces its own stream of logs. Storing and searching large volumes
of logs efficiently can be challenging.
This project explores a small part of the observability workflow with
gRPC ingestion, Lucene indexing, a dashboard, and alert checks. It is a
learning prototype, not a production-scale replacement for platforms
such as the ELK stack (Elasticsearch, Logstash, Kibana).

## 2. Our Solution — How It Works End to End

1. **A service produces a log** (e.g. `payment-service` logs an
   `ERROR: Payment gateway timeout`).
2. That log is sent to our **gRPC ingestion server**
   (`LogIngestionServer`) using Protocol Buffers.
3. The log is immediately:
   - **Indexed into Apache Lucene** so it can be searched, and
   - **Sent to connected Live Tail clients** over a separate WebSocket
     connection. This stream does not read from the search index.
4. An engineer opens the **React dashboard**, which talks to the
   backend through a REST bridge (`QueryApiServer`). From here they
   can:
   - Search logs with a simple query language, e.g.
     `level:ERROR AND service:payment-service`
   - View stat cards and charts — total logs, breakdown by service,
     breakdown by level, volume over time
   - View the built-in alert status; a background check records a history
     entry when the error count crosses its fixed threshold
   - **Define their own alert rules** — any service (or all of them),
     any log level, any threshold and time window — instead of relying
     on just the one fixed rule
   - Get an optional **Slack or Discord webhook notification** when a
     custom rule changes from inactive to active
   - Watch new logs stream onto the **Live Tail** page while connected,
     with no manual refresh needed

That's the whole loop: a log is sent by a service, indexed, and made
searchable in the dashboard. Configured rules check recent logs and can
send a webhook notification when a rule becomes active.

## 3. Key Features

| Feature | Description |
|---|---|
| gRPC ingestion | Receives log entries from clients over gRPC using Protocol Buffers |
| Full-text & field search | Apache Lucene index; query by level, service, or free-text message |
| Live analytics | Total logs, per-service and per-level breakdowns, volume-over-time chart |
| Built-in alert | Background check flags when the global error rate crosses a fixed threshold |
| **User-defined alert rules** | Configure service and level filters with a count threshold and time window; rules are checked every 15 seconds |
| **Optional webhook alerts** | A Slack/Discord webhook notification is sent when a configured rule becomes active; set `ALERT_WEBHOOK_URL` to enable it |
| Alert history | Past alerts (both built-in and rule-based) are saved and viewable, not just the current moment |
| Live Tail | Newly ingested logs are pushed to connected clients over WebSocket; there is no historical backfill |
| REST API + Postman collection | REST endpoints support the dashboard; the collection contains five core GET requests |
### Current implementation compared with the project brief

The brief describes a target system. The current repository implements a smaller prototype, so these brief requirements are not yet supported or performance-verified:

- The gRPC `LogEntry` contains service, level, message, and timestamp fields. It has no structured `response_time` field, so a filter such as `response_time > 1000` is not currently supported.
- Search returns at most 50 results per request. The repository has no benchmark demonstrating ingestion of millions of lines per minute or millisecond query latency.
- Custom alerts filter by service and level, then compare a count with a threshold over a time window. They do not run arbitrary user-defined query expressions. The background worker checks every 15 seconds.
- Optional Slack/Discord webhook notifications are implemented. Email notifications are not.
- The dashboard uses React and Vite, but the project does not include ECharts; the charts are implemented in the dashboard code.

## 4. Architecture

```
 Microservices                      LogStream Backend
 (log producers)
      |
      | gRPC :9090           ┌─────────────────────────┐
      ├──SendLog() ─────────▶│   LogIngestionServer      │
      |                      │                           │
      |                      │  1. writes to Lucene ─────┼──────▶ ┌────────────────┐
      |                      │  2. broadcasts to  ───────┼───┐    │ Lucene index    │
      |                      │     Live Tail              │   │    │ (on disk)       │
      |                      └─────────────────────────┘   │    └────────┬────────┘
      |                                                     │             │ reads
      |                                                     ▼             ▼
      |                                        ┌──────────────────┐ ┌───────────────────┐
      |                                        │ LiveTailWebSocket │ │   QueryApiServer    │
      |                                        │ Server (:8081)    │ │   REST (:8080)      │
      |                                        └────────┬─────────┘ └──────────┬──────────┘
      |                                                  │ push logs           │ JSON
      |                                                  ▼                     ▼
      |                                        ┌───────────────────────────────────────┐
      |                                        │       React Dashboard (Vite, :5173)      │
      |                                        │  Overview · Logs · Analytics · Alerts    │
      |                                        │       · Services · Live Tail             │
      |                                        └───────────────────────────────────────┘
      |                                                                      ▲
      |                                                                      │ reads alert rules
      |                                        ┌───────────────────┐         │ via REST
      |                                        │ AlertRuleStore /   │◀───────┘
      |                                        │ RuleAlertHistory   │
      |                                        │ (checked every 15s │
      |                                        │  against Lucene)   │
      |                                        └─────────┬─────────┘
      |                                                   │ on "fine → alert"
      |                                                   ▼
      |                                        ┌───────────────────┐
      |                                        │  WebhookNotifier    │────────▶  Slack / Discord
      |                                        └───────────────────┘
```

**Why does Live Tail bypass the search index?** Browsers can't speak
gRPC, so dashboard queries go through `QueryApiServer`, which reads
from Lucene. Live Tail uses a separate WebSocket connection:
`LogIngestionServer` sends each newly ingested log directly to connected
browsers. It shows logs received while the client is connected and does
not backfill previously indexed logs.

**How alert rules differ from the built-in alert:** the built-in alert
(`AlertHistory`) is one fixed, global rule and can't be changed without
editing code. User-defined rules (`AlertRuleStore`) are created from
the dashboard itself, each scoped to a service and level with its own
threshold and window, and are checked by a background thread every 15
seconds. When a rule changes from inactive to active, it is recorded in
`RuleAlertHistory`; `WebhookNotifier` sends a notification if
`ALERT_WEBHOOK_URL` is set. These checks run in `QueryApiServer`.

## 5. Tech Stack

| Layer | Technology |
|---|---|
| Log ingestion | Java, gRPC, Protocol Buffers |
| Search / indexing | Apache Lucene 9.10 |
| REST bridge | Java (`com.sun.net.httpserver`) — no extra framework |
| Live Tail | Java-WebSocket |
| Webhook notifications | `java.net.http.HttpClient` (built into Java 11+) — no extra dependency |
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
│   │   ├── LuceneIndexer.java           Indexing, search, aggregations, rule checks
│   │   ├── QueryApiServer.java          REST bridge (:8080) + alert-rules endpoints
│   │   ├── AlertHistory.java            Tracks and saves past built-in alerts
│   │   ├── AlertRule.java               One user-defined alert rule (service/level/threshold/window)
│   │   ├── AlertRuleStore.java          Saves, loads and manages all user-defined rules
│   │   ├── RuleAlertHistory.java        Tracks and saves past rule-based alerts
│   │   ├── WebhookNotifier.java         Sends a Slack/Discord webhook when a rule fires
│   │   ├── AlertMonitor.java            Console alert check
│   │   ├── AggregationDemo.java         Console aggregation demo
│   │   ├── BulkTestClient.java          Sends sample logs for testing
│   │   └── SearchDemo.java              Console search demo
│   ├── src/main/proto/log_service.proto
│   └── pom.xml
│
├── logstream-dashboard/              Frontend (React)
│   ├── src/
│   │   ├── App.jsx                      All pages and data-fetching logic, incl. alert rules UI
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
- Maven 3.6+ (or an IDE with Maven support)
- Node.js and npm

### Step 1 — Start the backend

Open `log-ingestion-service` as a Maven project in your IDE. Run these
classes in order. Keep the ingestion and REST servers running:

1. `LogIngestionServer.java` — starts the gRPC server (port 9090) and
   the Live Tail WebSocket server (port 8081)
2. `BulkTestClient.java` — sends 15 sample logs across 6 services and
   3 levels, then exits (run again to add more sample data)
3. `QueryApiServer.java` — starts the REST API (default port 8080) and
   background alert checks — **keep this running**

Confirm it's working: open `http://localhost:8080/api/aggregations`
in a browser. The response should contain JSON counts. `QueryApiServer`
uses port 8080 by default; set the `PORT` environment variable to use
a different port.

**Optional — enable real webhook alerts:** before running
`QueryApiServer`, set an environment variable
`ALERT_WEBHOOK_URL` to a Slack or Discord incoming-webhook URL (Run → Edit Configurations → Environment Variables in IntelliJ). If
it isn't set, webhook notifications are skipped; rule evaluation and
in-dashboard history still work.

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
- **Alerts** — built-in alert status + history, plus your own alert
  rules (create, remove) and their own history
- **Services** — log counts per service
- **Live Tail** — watch logs arrive in real time (run `BulkTestClient`
  again while this tab is open to see it in action)

## 8. API Reference

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/aggregations` | Total logs, counts by service, counts by level |
| GET | `/api/search?q=<query>` | Search logs — try `level:ERROR`, `message:timeout`, `service:payment-service` |
| GET | `/api/timeline` | Logs per minute, for the volume chart |
| GET | `/api/alerts` | Whether the built-in global alert is active right now |
| GET | `/api/alerts/history` | Past built-in alerts that have fired |
| GET | `/api/alert-rules` | List every user-defined alert rule |
| POST | `/api/alert-rules` | Create a rule — JSON body example: `{"service":"*","level":"ERROR","threshold":5,"windowMinutes":5}` (`service` and `level` default to `*` and `ERROR` if omitted) |
| DELETE | `/api/alert-rules?id=<id>` | Delete a rule |
| GET | `/api/alert-rules/history` | Past alerts fired by user-defined rules |
| WS | `ws://localhost:8081` | Live Tail — pushes each new log the instant it's received |

A ready-made **Postman collection** is at
`postman/LogStream.postman_collection.json`. It includes five core GET
requests (aggregations, search, timeline, current alert, and alert
history). The alert-rule endpoints are documented above but are not
included in that collection. It uses `baseUrl` (default
`http://localhost:8080`).

## 9. Week-by-Week Development Plan

### Backend

| Week | Focus |
|---|---|
| 1 | gRPC server to receive logs; protobuf schema for a log message |
| 2 | Apache Lucene integration — parsing, analyzing, and indexing logs |
| 3 | Aggregations — counts by service/level, and volume-per-minute for the charts |
| 4 | Alerting, REST bridge for the frontend, Live Tail (WebSocket), alert history |
| 5 | User-defined alert rules + real webhook notifications (Slack/Discord) |

### Frontend

| Week | Focus |
|---|---|
| 1 | Project setup; basic search bar and log table (sample data) |
| 2 | Search bar connected to the real backend |
| 3 | Stat cards and charts connected to real data |
| 4 | Alerts page, Services page, Live Tail page, mobile-responsive layout, visual polish |
| 5 | Alert rules UI — create/list/delete rules, rule alert history |

## 10. Team

| Name | Role |
|---|---|
| **Vimala** (Team Lead) | Backend (gRPC, Lucene, aggregations, alerting, alert rules, webhook notifications, Live Tail, REST bridge), Frontend dashboard, Testing |
| Doni Navaneeth Reddy | Backend |
| Shoyab Ahamad R | Frontend |
| Samata Pradip Chougule | Testing |

## 11. Future Improvements

- Persisting logs somewhere durable across restarts (the Lucene index and
  alert history/rule files are stored locally, relative to the process
  working directory)
- Role-based access, if this were ever used by more than one team
