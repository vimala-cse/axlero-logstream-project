import { useEffect, useState } from 'react'

// Change this if your backend (QueryApiServer) runs somewhere else.
const API_BASE = 'http://localhost:8080'

function timeAgoLabel(epochMillis) {
  const d = new Date(epochMillis)
  return d.toLocaleTimeString()
}

export default function App() {
  const [activeTab, setActiveTab] = useState('Overview')

  const [aggregations, setAggregations] = useState(null)
  const [timeline, setTimeline] = useState([])
  const [alert, setAlert] = useState(null)
  const [logs, setLogs] = useState([])

  const [query, setQuery] = useState('level:ERROR')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Loads everything the dashboard needs. Called once when the page
  // opens, and again whenever the user runs a new search.
  async function loadAll(searchQuery) {
    setLoading(true)
    setError(null)
    try {
      const [aggRes, timelineRes, alertRes, searchRes] = await Promise.all([
        fetch(`${API_BASE}/api/aggregations`),
        fetch(`${API_BASE}/api/timeline`),
        fetch(`${API_BASE}/api/alerts`),
        fetch(`${API_BASE}/api/search?q=${encodeURIComponent(searchQuery)}`),
      ])

      if (!aggRes.ok || !timelineRes.ok || !alertRes.ok || !searchRes.ok) {
        throw new Error('Backend did not return a valid response')
      }

      setAggregations(await aggRes.json())
      setTimeline(await timelineRes.json())
      setAlert(await alertRes.json())
      setLogs(await searchRes.json())
    } catch (err) {
      setError(
        'Could not reach the backend. Is QueryApiServer running on localhost:8080?'
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll(query)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleSearchKeyDown(e) {
    if (e.key === 'Enter') {
      loadAll(query)
    }
  }

  function levelClass(level) {
    if (level === 'ERROR') return 'err'
    if (level === 'WARN') return 'warn'
    return 'info'
  }

  // Turns a raw search result string like:
  // "[ERROR] payment-service: Payment gateway timeout (time: 123456)"
  // into { level, service, message, time }
  function parseLogLine(line) {
    const match = line.match(/^\[(\w+)\]\s+([\w-]+):\s+(.*?)\s+\(time:\s+(\d+)\)$/)
    if (!match) return { level: 'INFO', service: '-', message: line, time: null }
    const [, level, service, message, time] = match
    return { level, service, message, time: Number(time) }
  }

  const maxServiceCount = aggregations
    ? Math.max(...Object.values(aggregations.byService), 1)
    : 1

  const maxTimelineCount =
    timeline.length > 0 ? Math.max(...timeline.map((t) => t.count), 1) : 1

  return (
    <div className="app">
      <div className="topbar">
        <div className="brand">LogStream</div>
        <div className={`status ${alert?.active ? 'err' : 'ok'}`}>
          <span className="dot" />
          {loading
            ? 'Loading...'
            : alert
            ? alert.message
            : 'Status unknown'}
        </div>
      </div>

      <div className="tabs">
        {['Overview', 'Logs', 'Analytics', 'Alerts', 'Services'].map((tab) => (
          <div
            key={tab}
            className={`tab ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {tab}
          </div>
        ))}
      </div>

      {error && <div className="error-banner">{error}</div>}

      {(activeTab === 'Overview' || activeTab === 'Logs') && (
        <div className="hero">
          <div className="hero-label">Search your logs</div>
          <div className="query-box">
            <span className="prompt">&gt;</span>
            <input
              className="query-input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleSearchKeyDown}
              placeholder="level:ERROR"
            />
          </div>
          <div className="query-hint">
            try{' '}
            <span className="mono clickable" onClick={() => { setQuery('message:timeout'); loadAll('message:timeout') }}>
              message:timeout
            </span>
            <i>·</i>
            <span className="mono clickable" onClick={() => { setQuery('service:payment-service'); loadAll('service:payment-service') }}>
              service:payment-service
            </span>
            <i>·</i>
            <span className="mono clickable" onClick={() => { setQuery('level:WARN'); loadAll('level:WARN') }}>
              level:WARN
            </span>
          </div>
        </div>
      )}

      {activeTab === 'Overview' && aggregations && (
        <>
          <div className="stat-strip">
            <div className="stat">
              <span className="stat-num mono">{aggregations.totalLogs}</span>
              <span className="stat-word">total logs</span>
            </div>
            <div className="stat">
              <span className="stat-num info mono">
                {aggregations.byLevel.INFO || 0}
              </span>
              <span className="stat-word">info</span>
            </div>
            <div className="stat">
              <span className="stat-num warn mono">
                {aggregations.byLevel.WARN || 0}
              </span>
              <span className="stat-word">warnings</span>
            </div>
            <div className="stat">
              <span className="stat-num err mono">
                {aggregations.byLevel.ERROR || 0}
              </span>
              <span className="stat-word">errors</span>
            </div>
          </div>

          <div className="cols">
            <div className="col-left">
              <div className="section-title">Log volume, recent minutes</div>
              <Sparkline timeline={timeline} max={maxTimelineCount} />

              <div className="section-title">Recent logs</div>
              <LogList logs={logs} levelClass={levelClass} parseLogLine={parseLogLine} />
            </div>

            <div className="col-right">
              <div className="section-title">Logs by service</div>
              <ServiceBars byService={aggregations.byService} max={maxServiceCount} />

              <div className="alert-box">
                <div className={`alert-line ${alert?.active ? 'err' : 'ok'}`}>
                  <span className="dot" />
                  {alert?.active ? 'Alert active' : 'No active alerts'}
                </div>
                {alert?.active && (
                  <div className="alert-sub">
                    {alert.errorCount} ERROR logs in the last {alert.windowMinutes} minutes -
                    threshold is {alert.threshold}
                  </div>
                )}
              </div>
            </div>
          </div>
        </>
      )}

      {activeTab === 'Logs' && (
        <div className="section-title">
          {logs.length} result{logs.length === 1 ? '' : 's'} for{' '}
          <span className="mono">{query}</span>
        </div>
      )}
      {activeTab === 'Logs' && (
        <LogList logs={logs} levelClass={levelClass} parseLogLine={parseLogLine} wide />
      )}

      {activeTab === 'Analytics' && aggregations && (
        <div className="cols">
          <div className="col-left">
            <div className="section-title">Log volume, recent minutes</div>
            <Sparkline timeline={timeline} max={maxTimelineCount} big />
          </div>
          <div className="col-right">
            <div className="section-title">Logs by service</div>
            <ServiceBars byService={aggregations.byService} max={maxServiceCount} />
          </div>
        </div>
      )}

      {activeTab === 'Alerts' && (
        <div className="alert-box wide">
          <div className={`alert-line ${alert?.active ? 'err' : 'ok'}`}>
            <span className="dot" />
            {alert?.active ? 'Alert active' : 'No active alerts'}
          </div>
          {alert?.active ? (
            <div className="alert-sub">
              {alert.errorCount} ERROR logs in the last {alert.windowMinutes} minutes -
              threshold is {alert.threshold}
            </div>
          ) : (
            <div className="alert-sub">Error rate is within the normal range.</div>
          )}
        </div>
      )}

      {activeTab === 'Services' && aggregations && (
        <table className="service-table">
          <thead>
            <tr>
              <th>Service</th>
              <th>Log count</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(aggregations.byService).map(([name, count]) => (
              <tr key={name}>
                <td>{name}</td>
                <td className="mono">{count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function Sparkline({ timeline, max, big }) {
  if (!timeline || timeline.length === 0) {
    return <div className="empty-note">No timeline data yet - send some logs first.</div>
  }
  const width = 680
  const height = big ? 160 : 70
  const step = width / Math.max(timeline.length - 1, 1)
  const points = timeline
    .map((t, i) => {
      const x = i * step
      const y = height - (t.count / max) * (height - 10) - 5
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')

  return (
    <div className="spark-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} preserveAspectRatio="none">
        <polyline fill="none" stroke="#0B7285" strokeWidth="1.75" points={points} />
      </svg>
    </div>
  )
}

function ServiceBars({ byService, max }) {
  const entries = Object.entries(byService)
  if (entries.length === 0) {
    return <div className="empty-note">No service data yet.</div>
  }
  return (
    <div>
      {entries.map(([name, count]) => (
        <div className="svc-bar-row" key={name}>
          <span className="svc-name">{name}</span>
          <div className="svc-track">
            <div className="svc-fill" style={{ width: `${(count / max) * 100}%` }} />
          </div>
          <span className="svc-num mono">{count}</span>
        </div>
      ))}
    </div>
  )
}

function LogList({ logs, levelClass, parseLogLine, wide }) {
  if (!logs || logs.length === 0) {
    return <div className="empty-note">No logs match this search.</div>
  }
  return (
    <div className={`log-list ${wide ? 'wide' : ''}`}>
      {logs.map((line, i) => {
        const parsed = parseLogLine(line)
        return (
          <div className="log-row mono" key={i}>
            <span className={`lv ${levelClass(parsed.level)}`}>
              <span className="lv-dot" />
              {parsed.level}
            </span>
            <span className="svc">{parsed.service}</span>
            <span className="msg">{parsed.message}</span>
            <span className="time">
              {parsed.time ? timeAgoLabel(parsed.time) : ''}
            </span>
          </div>
        )
      })}
    </div>
  )
}
