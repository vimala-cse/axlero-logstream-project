# LogStream Dashboard

The LogStream dashboard is a React 18 application built with Vite. It reads log data from the Java REST API and shows new incoming logs through a WebSocket connection.

## Features

- **Overview** — log totals, counts by level and service, timeline, recent logs, and current built-in alert status.
- **Logs** — search indexed logs with Lucene query syntax, including numeric `response_time` filters in milliseconds.
- **Analytics** — charts for log volume, services, and levels.
- **Alerts** — built-in alert status and history, custom rules, and rule alert history.
- **Services** — log counts grouped by service.
- **Live Tail** — new logs pushed to the browser while the page is connected. Older logs are not backfilled into this view.

## Requirements

- Node.js and npm
- The LogStream backend running locally:
  - `LogIngestionServer.java` provides gRPC on port `9090` and WebSocket on port `8081`.
  - `QueryApiServer.java` provides the REST API on port `8080` by default.

To populate the dashboard, run `BulkTestClient.java` after starting `LogIngestionServer.java`. Then start `QueryApiServer.java`. Keep both servers running while using the dashboard.

## Start the dashboard

From this directory, run:

```bash
npm install
npm run dev
```

Open the local URL printed by Vite, usually `http://localhost:5173`.

To build the static frontend bundle, run:

```bash
npm run build
```

## Backend addresses

The dashboard defaults to the local backend addresses. To use a different backend, create a `.env.local` file in this directory:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=ws://localhost:8081
```

Change the values to match your REST API and Live Tail WebSocket hosts. Restart the Vite development server after editing the file. The backend REST server must allow the dashboard origin through CORS. Do not put secrets in Vite environment variables because values prefixed with `VITE_` are included in the browser bundle.

## Project files

```text
src/
├── App.jsx      Dashboard pages, data fetching, and WebSocket handling
├── App.css      Layout and responsive styles
└── main.jsx     React entry point
```

## Notes

- If the REST backend is unavailable, the dashboard displays a connection error.
- Webhook notifications are configured on the backend with `ALERT_WEBHOOK_URL`; they do not require the dashboard to remain open.
- Alert rules currently filter by service and log level with a count threshold and time window. They are not arbitrary query expressions. Log search also supports one numeric `response_time` comparison per query, for example `service:billing-api AND response_time > 1000`. Values are milliseconds.