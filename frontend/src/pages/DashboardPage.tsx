import { useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { DashboardSummary, getDashboard, HistoryEntry } from '../api/client'
import { RepositoryList } from '../features/repositories/RepositoryList'

const date = (value: string) => new Date(value).toLocaleString('pl-PL')
const href = (entry: HistoryEntry) => entry.targetHash ?? (entry.entryType === 'JOB' ? `#jobs/${entry.jobId}` : entry.entryType === 'PLAN' ? `#plans/${entry.planId}` : `#report/${entry.reportId}`)

export function DashboardPage() {
  const [data, setData] = useState<DashboardSummary>(); const [error, setError] = useState(false)
  async function load() { try { setData(await getDashboard()); setError(false) } catch { setError(true) } }
  useEffect(() => { load() }, [])
  if (error) return <div className="page"><p className="eyebrow">UI-00</p><h1>Dashboard</h1><div className="state-card error-state">Nie udało się pobrać podsumowania dashboardu.<button onClick={load}>Spróbuj ponownie</button></div></div>
  if (!data) return <div className="page"><p className="eyebrow">UI-00</p><h1>Dashboard</h1><div className="state-card">Ładowanie dashboardu…</div></div>
  return <div className="page"><div className="page-heading"><div><p className="eyebrow">UI-00</p><h1>Dashboard</h1><p className="muted">Bieżący stan workspace i ostatnia aktywność.</p></div><button className="button" onClick={load} disabled={false} aria-label="Odśwież dashboard" title="Odśwież dashboard"><RefreshCw size={16} /> Odśwież</button></div><div className="cards"><article><span className="label">Repositories</span><strong>{data.repositories}</strong><span className="muted">Zarejestrowane repozytoria</span></article><article><span className="label">Open findings</span><strong>{data.openFindings}</strong><span className="muted">Podatności wymagające obsługi</span></article><article><span className="label">Active jobs</span><strong>{data.activeJobs}</strong><span className="muted">QUEUED lub RUNNING</span></article><article><span className="label">Planned plans</span><strong>{data.plannedPlans}</strong><span className="muted">Plany w przygotowaniu</span></article></div><section className="dashboard-section"><div className="section-heading"><h2>Ostatnia aktywność</h2><a className="list-link" href="#history">Historia →</a></div>{data.recentActivity.length === 0 ? <div className="state-card">Brak zapisanej aktywności.</div> : <div className="activity-list">{data.recentActivity.map(entry => <a className="activity-row" href={href(entry)} key={`${entry.entryType}-${entry.entryId}`}><span><strong>{entry.entryType} · {entry.status}</strong><small>{entry.jobType ?? entry.taskId ?? entry.message ?? entry.entryId}</small></span><time>{date(entry.occurredAt)}</time></a>)}</div>}</section><section className="dashboard-section"><div className="section-heading"><h2>Repositories</h2><a className="list-link" href="#repositories">Zobacz wszystkie →</a></div><RepositoryList compact /></section></div>
}
