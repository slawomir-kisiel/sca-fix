import { useEffect, useState } from 'react'
import { getRepositories, RepositorySummary } from './api'

export function RepositoryList({ compact = false }: { compact?: boolean }) {
  const [repositories, setRepositories] = useState<RepositorySummary[]>()
  const [error, setError] = useState(false)

  function load() {
    setError(false)
    setRepositories(undefined)
    getRepositories().then(setRepositories).catch(() => setError(true))
  }

  useEffect(load, [])

  if (error) return <div className="state-card error-state"><strong>Nie udało się pobrać repozytoriów.</strong><button onClick={load}>Spróbuj ponownie</button></div>
  if (!repositories) return <div className="state-card">Ładowanie repozytoriów…</div>
  if (repositories.length === 0) return <div className="state-card"><strong>Brak repozytoriów</strong><span className="muted">Zarejestruj lokalny checkout albo repozytorium Git.</span><a className="button primary" href="#register-repository">Zarejestruj repozytorium</a></div>

  const visible = compact ? repositories.slice(0, 5) : repositories
  return <div className="repository-list">
    {visible.map((repository) => <a className="repository-row" href={`#repository/${repository.repositoryId}`} key={repository.repositoryId}>
      <span className="repository-icon">◈</span>
      <span className="repository-main"><strong>{repository.name}</strong><small>{repository.clonePath}</small></span>
      <span className={`status-pill status-${repository.status.toLowerCase()}`}>{repository.status}</span>
      <span className="repository-counts">{repository.projectCount} projects · {repository.reportCount} reports</span>
    </a>)}
    {compact && repositories.length > visible.length && <a className="list-link" href="#repositories">Zobacz wszystkie repozytoria →</a>}
  </div>
}
