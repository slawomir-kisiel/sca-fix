import { useEffect, useState } from 'react'
import { getRepositories, RepositorySummary } from '../repositories/api'
import { getProjects, ProjectSummary } from '../projects/api'
import { getReport, updateReportContext, ReportMetadata } from './api'

export function ReportContextPage({ reportId }: { reportId: string }) {
  const [report, setReport] = useState<ReportMetadata>()
  const [repositories, setRepositories] = useState<RepositorySummary[]>([])
  const [projects, setProjects] = useState<ProjectSummary[]>([])
  const [repositoryId, setRepositoryId] = useState('')
  const [projectId, setProjectId] = useState('')
  const [branch, setBranch] = useState('')
  const [commitSha, setCommitSha] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const selectedProject = projects.find(project => project.projectId === projectId)

  useEffect(() => { getReport(reportId).then(item => { setReport(item); setRepositoryId(item.repositoryId ?? '') }).catch(() => setError('Nie udało się pobrać raportu.')); getRepositories().then(setRepositories).catch(() => setError('Nie udało się pobrać repozytoriów.')) }, [reportId])
  useEffect(() => { if (repositoryId) getProjects(repositoryId).then(setProjects).catch(() => setError('Nie udało się pobrać projektów.')) }, [repositoryId])

  async function save() {
    if (!repositoryId || !projectId || !selectedProject) { setError('Wybierz repozytorium i projekt.'); return }
    setSaving(true); setError('')
    try { const result = await updateReportContext(reportId, { repositoryId, projectId, pomPath: selectedProject.pomPath, branch: branch || undefined, commitSha: commitSha || undefined }); window.location.hash = `#jobs/${result.job.jobId}?return=${encodeURIComponent(`#report/${reportId}`)}` } catch { setError('Nie udało się zapisać kontekstu raportu.') } finally { setSaving(false) }
  }

  if (!report) return <div className="page"><div className="state-card">Ładowanie kontekstu raportu…</div></div>
  return <div className="page"><a className="back-link" href={`#report/${reportId}`}>← Report</a><p className="eyebrow">UI-08</p><h1>Uzupełnij kontekst raportu</h1><p className="muted">{report.originalFilename} nie zawiera wystarczających informacji do powiązania z lokalnym projektem.</p><div className="workspace-form"><label>Repository<select value={repositoryId} onChange={event => { setRepositoryId(event.target.value); setProjectId('') }}><option value="">Wybierz repozytorium</option>{repositories.map(repository => <option value={repository.repositoryId} key={repository.repositoryId}>{repository.name}</option>)}</select></label><label>Project<select value={projectId} onChange={event => setProjectId(event.target.value)} disabled={!repositoryId}><option value="">Wybierz projekt</option>{projects.map(project => <option value={project.projectId} key={project.projectId}>{project.name} · {project.pomPath}</option>)}</select></label><label>pom.xml<input value={selectedProject?.pomPath ?? ''} readOnly placeholder="Zostanie ustalony z projektu" /></label><label>Branch<input value={branch} onChange={event => setBranch(event.target.value)} placeholder="np. main" /></label><label>Commit SHA<input value={commitSha} onChange={event => setCommitSha(event.target.value)} placeholder="opcjonalnie" /></label>{error && <div className="state-card error-state">{error}</div>}<button className="primary" onClick={save} disabled={saving}>{saving ? 'Zapisywanie…' : 'Zapisz i uruchom analizę Maven'}</button></div></div>
}
