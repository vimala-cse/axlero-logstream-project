import { useState } from 'react'

// Week 1 goal: just the SHAPE of the dashboard - a search bar and a
// place to show results. We are NOT connecting this to the real
// backend yet - that happens in Week 2, once the search API exists.
// For now, we show some sample (fake) log data so the layout can be
// seen and tested.

const sampleLogs = [
  { id: 1, level: 'ERROR', service: 'payment-service', message: 'Connection timeout', time: '10:32:01' },
  { id: 2, level: 'INFO', service: 'auth-service', message: 'User login successful', time: '10:32:05' },
  { id: 3, level: 'ERROR', service: 'billing-api', message: 'Database query failed', time: '10:33:12' },
  { id: 4, level: 'WARN', service: 'inventory-service', message: 'Stock running low', time: '10:34:47' },
]

function App() {
  // This holds whatever the user has typed into the search box.
  const [searchText, setSearchText] = useState('')

  function handleSearch() {
    // Week 1: just a placeholder. Week 2 will replace this with a
    // real call to our backend's search API.
    alert('Search feature coming in Week 2! You typed: ' + searchText)
  }

  return (
    <div className="page">
      <header className="header">
        <h1>LogStream</h1>
        <p>Search and monitor application logs</p>
      </header>

      <div className="search-bar">
        <input
          type="text"
          placeholder='e.g. level:ERROR AND service:payment'
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
        <button onClick={handleSearch}>Search</button>
      </div>

      <table className="results-table">
        <thead>
          <tr>
            <th>Time</th>
            <th>Level</th>
            <th>Service</th>
            <th>Message</th>
          </tr>
        </thead>
        <tbody>
          {sampleLogs.map((log) => (
            <tr key={log.id} className={log.level === 'ERROR' ? 'row-error' : ''}>
              <td>{log.time}</td>
              <td>{log.level}</td>
              <td>{log.service}</td>
              <td>{log.message}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <p className="note">
        Note: this table is showing sample data for now. Real search will
        be connected once the Week 2 backend search API is ready.
      </p>
    </div>
  )
}

export default App
