import { useEffect, useMemo, useState } from 'react'
import { getRepository, RepositoryDetails, ReportSummary } from './api'
import { getProjects, ProjectSummary, runBatchMavenAnalysis } from '../projects/api'

const tabs = [['overview', 'Overview'], ['branches', 'Branches'], ['projects', 'Projects'], ['reports', 'Reports'], ['graph', 'Dependency graph'], ['plans', 'Plans'], ['history', 'History']] as const
const analysisStatuses = ['REGISTERED', 'QUEUED', 'ANALYZING', 'ANALYZED', 'FAILED']

function formatBytes(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(value?: string) { return value ? new Date(value).toLocaleString('pl-PL') : '—' }
function isActive(project: ProjectSummary) { return project.mavenAnalysisStatus === 'QUEUED' || project.mavenAnalysisStatus === 'ANALYZING' }
function duration(project: ProjectSummary) {
  if (!project.mavenAnalysisStartedAt) return ''
  const end = project.mavenAnalysisFinishedAt ? new Date(project.mavenAnalysisFinishedAt).getTime() : Date.now()
  return `${Math.max(0, Math.floor((end - new Date(project.mavenAnalysisStartedAt).getTime()) / 1000))} s`
}

export function RepositoryDetailsPage({ repositoryId }: { repositoryId: string }) {
  const [details, setDetails] = useState<RepositoryDetails>()
  const [error, setError] = useState(false)
  const [projects, setProjects] = useState<ProjectSummary[]>()
  const [projectsError, setProjectsError] = useState(false)
  const [tab, setTab] = useState('overview')
  const [filter, setFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<string[]>([])
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [running, setRunning] = useState(false)
  const [analysisError, setAnalysisError] = useState('')

  useEffect(() => { getRepository(repositoryId).then(setDetails).catch(() => setError(true)) }, [repositoryId])

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined
    const load = () => getProjects(repositoryId).then(items => {
      if (cancelled) return
      setProjects(items)
      if (items.some(isActive)) timer = window.setTimeout(load, 5000)
    }).catch(() => { if (!cancelled) setProjectsError(true) })
    load()
    return () => { cancelled = true; if (timer) window.clearTimeout(timer) }
  }, [repositoryId])

  const visibleProjects = useMemo(() => {
    const query = filter.trim().toLocaleLowerCase()
    if (!projects) return []
    return projects.filter(project => {
      const analysisStatus = project.mavenAnalysisStatus ?? 'REGISTERED'
      const matchesStatus = statusFilter.length === 0 || statusFilter.includes(analysisStatus)
      const matchesText = [project.name, project.groupId, project.artifactId, project.version, project.pomPath]
        .some(value => value?.toLocaleLowerCase().includes(query))
      return matchesStatus && matchesText
    })
  }, [projects, filter, statusFilter])
  const allVisibleSelected = visibleProjects.length > 0 && visibleProjects.every(project => selected.has(project.projectId))

  if (error) return <div className="page"><div className="state-card error-state"><strong>Nie udało się pobrać repozytorium.</strong><a className="button" href="#repositories">Wróć do listy</a></div></div>
  if (!details) return <div className="page"><div className="state-card">Ładowanie repozytorium…</div></div>

  const { summary } = details
  const collection = tab === 'branches' ? details.branches : tab === 'projects' ? details.projects : tab === 'reports' ? details.reports : tab === 'plans' ? details.plans : []
  function toggleVisible() {
    setSelected(current => {
      const next = new Set(current)
      if (allVisibleSelected) visibleProjects.forEach(project => next.delete(project.projectId))
      else visibleProjects.forEach(project => next.add(project.projectId))
      return next
    })
  }
  async function runAnalysis() {
    setRunning(true); setAnalysisError('')
    try { await runBatchMavenAnalysis(repositoryId, [...selected]); setSelected(new Set()); const refreshed = await getProjects(repositoryId); setProjects(refreshed) }
    catch { setAnalysisError('Nie udało się uruchomić analizy Maven.') }
    finally { setRunning(false) }
  }

  return <div className="page">
    <a className="back-link" href="#repositories">← Repositories</a>
    <div className="page-heading"><div><p className="eyebrow">UI-04</p><h1>{summary.name}</h1><p className="mono muted">{summary.clonePath}</p></div><span className={`status-pill status-${summary.status.toLowerCase()}`}>{summary.status}</span></div>
    <div className="repository-meta"><span><b>URL</b>{summary.repositoryUrl ?? 'lokalny checkout'}</span><span><b>Branch</b>{summary.defaultBranch ?? '—'}</span><span><b>Commit</b><code>{summary.baseCommitSha ?? '—'}</code></span></div>
    <div className="tabs" role="tablist">{tabs.map(([key, label]) => <button className={tab === key ? 'tab active' : 'tab'} role="tab" aria-selected={tab === key} onClick={() => setTab(key)} key={key}>{label}</button>)}</div>
    {tab === 'overview' && <div className="detail-grid"><article><span className="label">Projects</span><strong>{summary.projectCount}</strong><a className="list-link" href={`#projects/${summary.repositoryId}`}>Detect projects →</a></article><article><span className="label">Reports</span><strong>{summary.reportCount}</strong></article><article><span className="label">Active plans</span><strong>{summary.activePlanCount}</strong></article></div>}
    {tab === 'projects' && <div><div className="section-heading"><h2>Registered projects</h2><a className="button primary" href={`#projects/${summary.repositoryId}`}>Detect projects</a></div>{projectsError && <div className="state-card error-state">Nie udało się pobrać listy projektów.</div>}{analysisError && <div className="state-card error-state">{analysisError}</div>}{projects && projects.length > 0 && <div className="project-toolbar"><input value={filter} onChange={event => setFilter(event.target.value)} placeholder="Filtruj po nazwie, grupie, wersji lub folderze…" /><details className="status-filter"><summary>Status{statusFilter.length ? ` (${statusFilter.length})` : ''}</summary><div className="status-filter-menu">{analysisStatuses.map(status => <label key={status}><input type="checkbox" checked={statusFilter.includes(status)} onChange={event => setStatusFilter(current => event.target.checked ? [...current, status] : current.filter(item => item !== status))} /> {status}</label>)}</div></details><label><input type="checkbox" checked={allVisibleSelected} onChange={toggleVisible} /> Zaznacz odfiltrowane ({visibleProjects.length})</label><button className="primary" disabled={!selected.size || running} onClick={runAnalysis}>{running ? 'Uruchamianie…' : `Uruchom analizę Maven (${selected.size})`}</button></div>}{!projects && !projectsError && <div className="state-card">Ładowanie projektów…</div>}{projects && projects.length === 0 && <div className="state-card">Brak zarejestrowanych projektów.</div>}{projects && projects.length > 0 && visibleProjects.length === 0 && <div className="state-card">Brak projektów pasujących do filtra.</div>}{visibleProjects.length > 0 && <div className="project-table">{visibleProjects.map(project => <div className="project-row" key={project.projectId}><input type="checkbox" checked={selected.has(project.projectId)} onChange={event => setSelected(current => { const next = new Set(current); event.target.checked ? next.add(project.projectId) : next.delete(project.projectId); return next })} /><span><a className="list-link" href={`#graph/${project.projectId}`}>{project.name}</a><small>{project.pomPath}</small><small>{project.groupId ?? 'brak groupId'}:{project.artifactId ?? 'brak artifactId'} · {project.version ?? 'brak version'} · {project.packaging ?? 'jar'}</small></span><span className="project-analysis-status"><a className="list-link" href={project.mavenAnalysisJobId ? `#jobs/${project.mavenAnalysisJobId}?return=repository/${repositoryId}` : undefined}>{(project.mavenAnalysisStatus ?? 'REGISTERED') === 'ANALYZING' ? `ANALYZING… ${duration(project)}` : project.mavenAnalysisStatus ?? 'REGISTERED'}</a><small>{project.mavenAnalysisStatus === 'ANALYZED' || project.mavenAnalysisStatus === 'FAILED' ? formatDate(project.mavenAnalysisFinishedAt) : ''}</small></span></div>)}</div>}</div>}
    {tab === 'reports' && <div><div className="section-heading"><h2>Security reports</h2><a className="button primary" href={`#reports/import?repositoryId=${encodeURIComponent(summary.repositoryId)}`}>Import report</a></div>{details.reports.length === 0 && <div className="state-card">Brak raportów powiązanych z repozytorium.</div>}{details.reports.length > 0 && <div className="report-table">{details.reports.map((report: ReportSummary) => <a className="report-row report-link-row" href={`#report/${report.reportId}`} key={report.reportId}><span className="report-main"><strong>{report.originalFilename}</strong><small>{report.format ?? report.contentType ?? 'Nieznany format'} · {formatDate(report.importedAt)}</small><code>{report.reportId}</code></span><span className="report-meta"><small>{formatBytes(report.sizeBytes)}</small><small>{report.sha256}</small></span><em className={`status-pill status-${report.status.toLowerCase()}`}>{report.status}</em></a>)}</div>}</div>}
    {tab === 'graph' && <div className="state-card">Graf zależności będzie dostępny po analizie Maven. <a href="#jobs">Otwórz joby →</a></div>}
    {tab === 'history' && <div className="state-card">Brak zapisanej historii operacji.</div>}
    {collection.length === 0 && !['overview', 'graph', 'history', 'projects', 'reports'].includes(tab) && <div className="state-card">Brak danych w tej zakładce.</div>}
    {collection.length > 0 && !['reports'].includes(tab) && <pre className="json-preview">{JSON.stringify(collection, null, 2)}</pre>}
  </div>
}
