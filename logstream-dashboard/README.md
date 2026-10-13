# LogStream Dashboard (React + live backend data)

This is a working version of the design I showed you - not just a
picture, actual React code that fetches real data from your backend
and updates the page.

## What it does

- **Overview tab** - stat numbers, a log-volume line chart, logs by
  service, recent logs, and the alert status - all live from your backend
- **Logs tab** - full search: type a query, press Enter, see matching logs
- **Analytics tab** - bigger versions of the volume chart and service chart
- **Alerts tab** - shows whether an alert is currently active
- **Services tab** - a simple table of services and their log counts
- **Mobile responsive** - resize the browser narrow (or open on a phone) and
  the layout stacks into one column instead of two
- **Hover states** - tabs, log rows, the search box, and the example query
  chips all respond when you hover/click them

## Before you run this

Your backend must be running first:
1. `LogIngestionServer.java` (port 9090)
2. `BulkTestClient.java` (to have some sample logs)
3. `QueryApiServer.java` (port 8080) - use the version from the
   `final-polish-update.zip` I sent earlier, it has the `/api/timeline`
   and `/api/alerts` endpoints this dashboard needs

If `QueryApiServer` is not running, you'll see a red error message at
the top of the page instead of data - that's expected, just start it.

## How to run

```
npm install
npm run dev
```

Then open the link it shows (usually `http://localhost:5173`).

## If your backend runs on a different address

Open `src/App.jsx`, change this line near the top:
```js
const API_BASE = 'http://localhost:8080'
```

## Project structure

```
src/
  App.jsx      - all the logic: fetching data, search, the 5 tabs
  App.css      - all the styling, including mobile responsiveness
  main.jsx     - just mounts App into the page (you won't need to touch this)
```

## For the team

Show this to everyone as the reference for how the frontend should
look and behave. If you want to add a new section or page:
1. Add a new tab name to the array in the `tabs` section of `App.jsx`
2. Add a new `{activeTab === 'YourTab' && ( ... )}` block below the
   existing ones, using the same data (`aggregations`, `timeline`,
   `alert`, `logs`) that's already being fetched

## Push to GitHub

```
git add .
git commit -m "Frontend: working dashboard connected to backend APIs"
git push origin vimala
```
