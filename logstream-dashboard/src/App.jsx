import { useEffect, useId, useRef, useState } from 'react'

// Change these if your backend runs somewhere else.
const API_BASE = 'http://localhost:8080'   // QueryApiServer (REST)
const WS_BASE = 'ws://localhost:8081'      // LiveTailWebSocketServer (Live Tail)

function timeAgoLabel(epochMillis) {
  const d = new Date(epochMillis)
  return d.toLocaleTimeString()
}

// Small inline icons - no extra npm package needed. Each one just
// draws simple shapes with the current text color, so it automatically
// matches whatever color the tab/label around it already has.
function Icon({ name, size = 15 }) {
  const p = {
    width: size, height: size, viewBox: '0 0 24 24', fill: 'none',
    stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round',
  }
  switch (name) {
    case 'overview':
      return <svg {...p}><rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /></svg>
    case 'logs':
      return <svg {...p}><line x1="4" y1="6" x2="20" y2="6" /><line x1="4" y1="12" x2="20" y2="12" /><line x1="4" y1="18" x2="14" y2="18" /></svg>
    case 'analytics':
      return <svg {...p}><line x1="4" y1="20" x2="20" y2="20" /><rect x="6" y="10" width="3" height="8" /><rect x="11" y="6" width="3" height="12" /><rect x="16" y="13" width="3" height="5" /></svg>
    case 'alerts':
      return <svg {...p}><path d="M12 3a5 5 0 0 0-5 5v3.3L5 15h14l-2-3.7V8a5 5 0 0 0-5-5z" /><path d="M9.5 18a2.5 2.5 0 0 0 5 0" /></svg>
    case 'services':
      return <svg {...p}><rect x="3" y="4" width="18" height="6" rx="1" /><rect x="3" y="14" width="18" height="6" rx="1" /><circle cx="7" cy="7" r="0.6" fill="currentColor" /><circle cx="7" cy="17" r="0.6" fill="currentColor" /></svg>
    case 'live':
      return <svg {...p}><circle cx="12" cy="12" r="2.6" /><path d="M8.3 8.3a5.4 5.4 0 0 0 0 7.4" /><path d="M15.7 8.3a5.4 5.4 0 0 1 0 7.4" /></svg>
    case 'search':
      return <svg {...p}><circle cx="11" cy="11" r="7" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
    default:
      return null
  }
}

const TAB_ICON = {
  Overview: 'overview',
  Logs: 'logs',
  Analytics: 'analytics',
  Alerts: 'alerts',
  Services: 'services',
  'Live Tail': 'live',
}

export default function App() {
  const [activeTab, setActiveTab] = useState('Overview')

  const [aggregations, setAggregations] = useState(null)
  const [timeline, setTimeline] = useState([])
  const [alert, setAlert] = useState(null)
  const [alertHistory, setAlertHistory] = useState([])
  const [logs, setLogs] = useState([])

  const [liveLogs, setLiveLogs] = useState([])
  const [liveConnected, setLiveConnected] = useState(false)
  const wsRef = useRef(null)

  const [query, setQuery] = useState('level:ERROR')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  async function loadAll(searchQuery) {
    setLoading(true)
    setError(null)
    try {
      const [aggRes, timelineRes, alertRes, historyRes, searchRes] = await Promise.all([
        fetch(`${API_BASE}/api/aggregations`),
        fetch(`${API_BASE}/api/timeline`),
        fetch(`${API_BASE}/api/alerts`),
        fetch(`${API_BASE}/api/alerts/history`),
        fetch(`${API_BASE}/api/search?q=${encodeURIComponent(searchQuery)}`),
      ])

      if (!aggRes.ok || !timelineRes.ok || !alertRes.ok || !historyRes.ok || !searchRes.ok) {
        throw new Error('Backend did not return a valid response')
      }

      setAggregations(await aggRes.json())
      setTimeline(await timelineRes.json())
      setAlert(await alertRes.json())
      setAlertHistory(await historyRes.json())
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

  // Live Tail: connects directly to LiveTailWebSocketServer (port 8081),
  // NOT to QueryApiServer. Only while this tab is open - always closed
  // when leaving the tab, so the connection doesn't stay open forever
  // in the background.
  useEffect(() => {
    if (activeTab !== 'Live Tail') {
      return
    }

    setLiveLogs([])
    const ws = new WebSocket(WS_BASE)
    wsRef.current = ws

    ws.onopen = () => setLiveConnected(true)
    ws.onmessage = (event) => {
      setLiveLogs((prev) => [event.data, ...prev].slice(0, 100))
    }
    ws.onerror = () => setLiveConnected(false)
    ws.onclose = () => setLiveConnected(false)

    return () => {
      ws.close()
      setLiveConnected(false)
    }
  }, [activeTab])

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
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-text">LogStream</span>
        </div>
        <div className={`status ${alert?.active ? 'err' : 'ok'}`}>
          <span className="dot" />
          {loading ? 'Loading...' : alert ? alert.message : 'Status unknown'}
        </div>
      </div>

      <div className="tabs">
        {['Overview', 'Logs', 'Analytics', 'Alerts', 'Services', 'Live Tail'].map((tab) => (
          <div
            key={tab}
            className={`tab ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            <Icon name={TAB_ICON[tab]} size={14} />
            <span>{tab}</span>
          </div>
        ))}
      </div>

      {error && activeTab !== 'Live Tail' && <div className="error-banner">{error}</div>}

      {(activeTab === 'Overview' || activeTab === 'Logs') && (
        <div className="hero">
          <div className="hero-label">Search your logs</div>
          <div className="query-box">
            <span className="search-icon"><Icon name="search" size={14} /></span>
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
            <span className="hint-label">try</span>
            <span className="hint-chip mono" onClick={() => { setQuery('message:timeout'); loadAll('message:timeout') }}>
              message:timeout
            </span>
            <span className="hint-chip mono" onClick={() => { setQuery('service:payment-service'); loadAll('service:payment-service') }}>
              service:payment-service
            </span>
            <span className="hint-chip mono" onClick={() => { setQuery('level:WARN'); loadAll('level:WARN') }}>
              level:WARN
            </span>
          </div>
        </div>
      )}

      {activeTab === 'Overview' && aggregations && (
        <>
          <div className="stat-strip">
            <div className="stat">
              <span className="stat-num total mono">{aggregations.totalLogs}</span>
              <span className="stat-word">total logs</span>
            </div>
            <div className="stat">
              <span className="stat-num info mono">{aggregations.byLevel.INFO || 0}</span>
              <span className="stat-word">info</span>
            </div>
            <div className="stat">
              <span className="stat-num warn mono">{aggregations.byLevel.WARN || 0}</span>
              <span className="stat-word">warnings</span>
            </div>
            <div className="stat">
              <span className="stat-num err mono">{aggregations.byLevel.ERROR || 0}</span>
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
          {logs.length} result{logs.length === 1 ? '' : 's'} for <span className="mono">{query}</span>
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

            <div className="section-title" style={{ marginTop: 26 }}>Logs by level</div>
            <LevelBars byLevel={aggregations.byLevel} />
          </div>
        </div>
      )}

      {activeTab === 'Alerts' && (
        <div>
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

          <div className="section-title" style={{ marginTop: 22 }}>Alert history</div>
          {alertHistory.length === 0 ? (
            <div className="empty-note">No alerts have fired yet.</div>
          ) : (
            <table className="service-table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Error count</th>
                </tr>
              </thead>
              <tbody>
                {alertHistory.map((entry, i) => (
                  <tr key={i}>
                    <td className="mono">{timeAgoLabel(entry.time)}</td>
                    <td className="mono">{entry.errorCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
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

      {activeTab === 'Live Tail' && (
        <div>
          <div className="section-title">
            <span className={`live-dot ${liveConnected ? 'on' : 'off'}`} />
            {liveConnected ? 'Live - streaming new logs as they arrive' : 'Connecting...'}
          </div>
          {liveLogs.length === 0 ? (
            <div className="empty-note">
              Waiting for new logs. Run BulkTestClient again to see them appear here instantly.
            </div>
          ) : (
            <LogList logs={liveLogs} levelClass={levelClass} parseLogLine={parseLogLine} wide />
          )}
        </div>
      )}
    </div>
  )
}

function Sparkline({ timeline, max, big }) {
  const gradientId = useId()
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
  const areaPoints = `0,${height} ${points} ${width},${height}`

  return (
    <div className="spark-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} preserveAspectRatio="none">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#0B7285" stopOpacity="0.22" />
            <stop offset="100%" stopColor="#0B7285" stopOpacity="0" />
          </linearGradient>
        </defs>
        <polygon points={areaPoints} fill={`url(#${gradientId})`} />
        <polyline fill="none" stroke="#0B7285" strokeWidth="1.75" points={points} />
      </svg>
    </div>
  )
}

function LevelBars({ byLevel }) {
  const order = ['ERROR', 'WARN', 'INFO']
  const colors = { ERROR: '#b3261e', WARN: '#9c6b12', INFO: '#46618c' }
  const total = order.reduce((sum, level) => sum + (byLevel[level] || 0), 0) || 1

  return (
    <div>
      <div className="stack-bar">
        {order.map((level) =>
          byLevel[level] ? (
            <div
              key={level}
              className="stack-seg"
              style={{ width: `${(byLevel[level] / total) * 100}%`, background: colors[level] }}
            />
          ) : null
        )}
      </div>
      <div>
        {order.map((level) =>
          byLevel[level] ? (
            <div className="legend-item" key={level}>
              <span className="dot2" style={{ background: colors[level] }} />
              {level} - {byLevel[level]} ({Math.round((byLevel[level] / total) * 100)}%)
            </div>
          ) : null
        )}
      </div>
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
            <span className="time">{parsed.time ? timeAgoLabel(parsed.time) : ''}</span>
          </div>
        )
      })}
    </div>
  )
}
