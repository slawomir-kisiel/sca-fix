import { ChangeEvent, FormEvent, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

type Workspace = { workspaceId: string; repositoryPath: string; status: string; branch?: string; commitSha?: string; clean: boolean }
type WorkspaceConfiguration = {
  schemaVersion: number; analysisProjects: string[]; editableProjects: string[]; updateLevel: string
  cveOverrideUpdateLevel: string; ambiguousStrategy: string; skipLibraries: string[]; disabledStrategies: string[]
  proxyHost?: string; proxyPort?: number; mavenRepositories: string[]; ignoreLocalChanges: boolean
}
type Project = { coordinate: string; groupId: string; artifactId: string; version: string; pomPath: string; analysisSelected: boolean; editableSelected: boolean }
type MavenJob = { jobId: string; workspaceId: string; projectCoordinate: string; pomPath: string; status: string; command: string; stdout: string; stderr: string; errorMessage?: string }
type ReportSummary = { workspaceId: string; format: string; sourceFileName: string; sizeBytes: number; sha256: string; status: string; vulnerableLibraries: number; cves: number; warnings: string[]; errors: string[]; rawPath: string; canonicalPath: string }
type CorrelationJob = { jobId: string; status: string; projects: number; libraries: number; matched: number; ambiguous: number; orphan: number; warnings: string; errors: string; outputPath?: string }
type OccurrenceReview = { workspaceId: string; entries: { libraryKey: string; status: string; warnings: string[]; occurrences: { occurrenceId: string; projectId: string; pathId: string; vulnerableNodeId: string; reportedVersion: string; depth: number; previousNodeId?: string; action?: string }[] }[]; warnings: string[]; errors: string[] }
type StrategyOptions = { occurrenceId: string; libraryKey: string; projectId: string; nodeId: string; currentVersion: string; targetVersions: string[]; strategies: string[]; savedDecision: { strategy: string; targetVersion: string }[] }
type PlanningJob = { jobId: string; status: string; planned: number; skipped: number; errorMessage?: string; outputPath?: string }
type Plan = { planId: string; status?: string; updateLevel: string; items: { itemId: string; project: { groupId: string; artifactId: string; pomPath: string }; artifact: { groupId: string; artifactId: string }; currentVersion: string; targetVersion: string; strategy: string; status?: string; errorMessage?: string }[] }
type PlanDiff = { planId: string; combinedDiff: string; items: { itemId: string; pomPath: string; artifact: string; strategy: string; currentVersion: string; targetVersion: string; changed: boolean; diff: string; errorMessage?: string }[] }
type ApplyJob = { jobId: string; status: string; selected: number; applied: number; failed: number; skipped: number; errorMessage?: string }
type GitStatus = { repository: boolean; clean: boolean; commitSha?: string; branch?: string; changedFiles: string[] }
type GitCommitJob = { jobId: string; status: string; commitSha?: string; branch?: string; changedFiles: string[]; pushed: boolean; errorMessage?: string }
type HistorySnapshot = { workspaceId: string; repositoryPath: string; workspaceStatus: string; events: { eventId: string; timestamp: string; stage: string; action: string; details: string }[]; artifacts: { path: string; sizeBytes: number; modifiedAt?: string }[] }
const defaultConfiguration: WorkspaceConfiguration = {
  schemaVersion: 1, analysisProjects: ['*'], editableProjects: ['*'], updateLevel: 'MINOR', cveOverrideUpdateLevel: 'PATCH',
  ambiguousStrategy: 'SHALLOWEST', skipLibraries: [], disabledStrategies: [], proxyHost: '', proxyPort: undefined,
  mavenRepositories: [], ignoreLocalChanges: false,
}
const splitValues = (value: string) => value.split(/[\n,]/).map(item => item.trim()).filter(Boolean)
const joinValues = (value: string[]) => value.join('\n')

function App() {
  const [mode, setMode] = useState<'local' | 'git'>('local')
  const [repositoryPath, setRepositoryPath] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [clonePath, setClonePath] = useState('')
  const [branch, setBranch] = useState('')
  const [cloneJob, setCloneJob] = useState<{ jobId: string; status: string; errorMessage?: string }>()
  const [proposedPath, setProposedPath] = useState('')
  const [workspace, setWorkspace] = useState<Workspace>()
  const [configuration, setConfiguration] = useState<WorkspaceConfiguration>(defaultConfiguration)
  const [configurationLoaded, setConfigurationLoaded] = useState(false)
  const [configurationSaved, setConfigurationSaved] = useState(false)
  const [projects, setProjects] = useState<Project[]>([])
  const [projectFilter, setProjectFilter] = useState('')
  const [scopeSaved, setScopeSaved] = useState(false)
  const [mavenJobs, setMavenJobs] = useState<MavenJob[]>([])
  const [analysisRunning, setAnalysisRunning] = useState(false)
  const [reportFormat, setReportFormat] = useState('')
  const [report, setReport] = useState<ReportSummary>()
  const [correlationJob, setCorrelationJob] = useState<CorrelationJob>()
  const [occurrenceReview, setOccurrenceReview] = useState<OccurrenceReview>()
  const [strategyOptions, setStrategyOptions] = useState<StrategyOptions>()
  const [strategy, setStrategy] = useState('')
  const [targetVersion, setTargetVersion] = useState('')
  const [planningJob, setPlanningJob] = useState<PlanningJob>()
  const [plan, setPlan] = useState<Plan>()
  const [planDiff, setPlanDiff] = useState<PlanDiff>()
  const [selectedPlanItems, setSelectedPlanItems] = useState<string[]>([])
  const [applyJob, setApplyJob] = useState<ApplyJob>()
  const [gitStatus, setGitStatus] = useState<GitStatus>()
  const [commitMessage, setCommitMessage] = useState('SCA-fix-tool: apply update plan')
  const [pushAfterCommit, setPushAfterCommit] = useState(false)
  const [commitJob, setCommitJob] = useState<GitCommitJob>()
  const [restartStage, setRestartStage] = useState('CORRELATION')
  const [restartResult, setRestartResult] = useState<string>()
  const [history, setHistory] = useState<HistorySnapshot>()
  const [pendingImport, setPendingImport] = useState<{ file: File; configuration: WorkspaceConfiguration }>()
  const [error, setError] = useState('')

  async function proposeClone() {
    setError('')
    const response = await fetch('/ui-api/workspaces/git/plan', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ repositoryUrl, clonePath: clonePath || undefined, branch: branch || undefined }) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się ustalić ścieżki checkoutu.'); return }
    const plan = await response.json(); setProposedPath(plan.proposedClonePath); setClonePath(plan.proposedClonePath)
  }
  async function startClone(event: FormEvent) {
    event.preventDefault(); setError('')
    const response = await fetch('/ui-api/workspaces/git', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ repositoryUrl, clonePath: clonePath || undefined, branch: branch || undefined }) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się uruchomić klonowania.'); return }
    setCloneJob(await response.json())
  }
  async function refreshCloneJob() {
    if (!cloneJob || !['QUEUED', 'RUNNING'].includes(cloneJob.status)) return
    const response = await fetch(`/ui-api/workspaces/git/jobs/${cloneJob.jobId}`)
    if (response.ok) setCloneJob(await response.json())
  }
  useEffect(() => {
    if (!cloneJob || !['QUEUED', 'RUNNING'].includes(cloneJob.status)) return
    const timer = window.setInterval(refreshCloneJob, 2000); return () => window.clearInterval(timer)
  }, [cloneJob?.jobId, cloneJob?.status])
  async function loadConfiguration(workspaceId: string) {
    const response = await fetch(`/ui-api/workspaces/${workspaceId}/configuration`)
    if (!response.ok) { setError('Nie udało się pobrać konfiguracji workspace’u.'); return }
    setConfiguration(await response.json()); setConfigurationLoaded(true)
    const projectsResponse = await fetch(`/ui-api/workspaces/${workspaceId}/projects`)
    if (projectsResponse.ok) setProjects(await projectsResponse.json())
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    const response = await fetch('/ui-api/workspaces/local', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ repositoryPath }) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się utworzyć workspace’u.'); return }
    const created = await response.json(); setWorkspace(created); await loadConfiguration(created.workspaceId)
  }
  function updateConfiguration<K extends keyof WorkspaceConfiguration>(key: K, value: WorkspaceConfiguration[K]) {
    setConfiguration(previous => ({ ...previous, [key]: value })); setConfigurationSaved(false)
  }
  async function saveConfiguration(event: FormEvent) {
    event.preventDefault(); setError(''); if (!workspace) return
    if (configuration.proxyPort !== undefined && (configuration.proxyPort < 1 || configuration.proxyPort > 65535)) { setError('Port proxy musi mieścić się w zakresie 1–65535.'); return }
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/configuration`, { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(configuration) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się zapisać konfiguracji.'); return }
    setConfiguration(await response.json()); setConfigurationSaved(true)
  }
  function toggleProject(coordinate: string, field: 'analysisSelected' | 'editableSelected') {
    setProjects(previous => previous.map(project => project.coordinate === coordinate
      ? { ...project, [field]: !project[field] } : project)); setScopeSaved(false)
  }
  async function saveScope() {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/projects/scope`, { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ analysisProjects: projects.filter(project => project.analysisSelected).map(project => project.coordinate), editableProjects: projects.filter(project => project.editableSelected).map(project => project.coordinate) }) })
    if (!response.ok) { setError('Nie udało się zapisać zakresu projektów.'); return }
    setProjects(await response.json()); setScopeSaved(true)
  }
  async function startMavenAnalysis() {
    if (!workspace) return
    setError(''); setAnalysisRunning(true)
    const selected = projects.filter(project => project.analysisSelected).map(project => project.coordinate)
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/maven-analysis`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ projects: selected }) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się uruchomić analizy Maven.'); setAnalysisRunning(false); return }
    setMavenJobs(await response.json())
  }
  async function refreshMavenJobs() {
    const active = mavenJobs.filter(job => ['QUEUED', 'RUNNING'].includes(job.status))
    if (!active.length) { setAnalysisRunning(false); return }
    const refreshed = await Promise.all(active.map(async job => {
      const response = await fetch(`/ui-api/workspaces/${job.workspaceId}/maven-analysis/jobs/${job.jobId}`)
      return response.ok ? await response.json() as MavenJob : job
    }))
    setMavenJobs(previous => previous.map(job => refreshed.find(item => item.jobId === job.jobId) ?? job))
  }
  useEffect(() => {
    if (!analysisRunning) return
    const timer = window.setInterval(refreshMavenJobs, 2000); return () => window.clearInterval(timer)
  }, [analysisRunning, mavenJobs])
  async function importReport(event: FormEvent) {
    event.preventDefault(); if (!workspace) return
    const input = document.getElementById('report-file') as HTMLInputElement
    const file = input.files?.[0]
    if (!file) { setError('Wybierz plik raportu.'); return }
    const form = new FormData(); form.append('file', file)
    const query = reportFormat ? `?format=${encodeURIComponent(reportFormat)}` : ''
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/reports/import${query}`, { method: 'POST', body: form })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się zaimportować raportu.'); return }
    setReport(await response.json()); setError('')
  }
  async function startCorrelation() {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/correlation`, { method: 'POST' })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się uruchomić korelacji.'); return }
    setCorrelationJob(await response.json()); setError('')
  }
  async function refreshCorrelation() {
    if (!workspace || !correlationJob || !['QUEUED', 'RUNNING'].includes(correlationJob.status)) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/correlation/jobs/${correlationJob.jobId}`)
    if (response.ok) setCorrelationJob(await response.json())
  }
  async function loadOccurrenceReview() {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/occurrences`)
    if (response.ok) setOccurrenceReview(await response.json())
    else { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się pobrać occurrence.') }
  }
  async function decideOccurrence(occurrenceId: string, action: string) {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/occurrences/${occurrenceId}/decision?action=${encodeURIComponent(action)}`, { method: 'PUT' })
    if (response.ok) setOccurrenceReview(await response.json())
    else setError('Nie udało się zapisać decyzji occurrence.')
  }
  async function loadStrategyOptions(occurrenceId: string) {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/occurrences/${occurrenceId}/strategy`)
    if (!response.ok) { setError('Nie udało się pobrać dostępnych strategii.'); return }
    const options = await response.json() as StrategyOptions; setStrategyOptions(options); setStrategy(options.savedDecision[0]?.strategy ?? options.strategies[0]); setTargetVersion(options.savedDecision[0]?.targetVersion ?? options.targetVersions[1] ?? options.currentVersion)
  }
  async function saveStrategy(event: FormEvent) {
    event.preventDefault(); if (!workspace || !strategyOptions) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/occurrences/${strategyOptions.occurrenceId}/strategy`, { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ strategy, targetVersion }) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się zapisać decyzji strategii.'); return }
    setError(''); await loadStrategyOptions(strategyOptions.occurrenceId)
  }
  async function startPlanning() {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/planning`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({}) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się uruchomić planowania.'); return }
    setPlanningJob(await response.json())
  }
  async function refreshPlanning() {
    if (!workspace || !planningJob || !['QUEUED', 'PLANNING'].includes(planningJob.status)) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/planning/jobs/${planningJob.jobId}`)
    if (response.ok) setPlanningJob(await response.json())
  }
  async function loadPlan() {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans`)
    if (response.ok) setPlan(await response.json()); else setError('Nie udało się pobrać planu.')
  }
  async function updatePlanItem(itemId: string, strategy: string, targetVersion: string, status?: string) {
    if (!workspace || !plan) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans/${plan.planId}/items/${itemId}`, { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ strategy, targetVersion, status }) })
    if (response.ok) setPlan(await response.json()); else setError('Nie udało się zaktualizować PlanItem.')
  }
  async function previewPlanDiff() {
    if (!workspace || !plan) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans/${plan.planId}/diff`, { method: 'POST' })
    if (response.ok) setPlanDiff(await response.json()); else { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się przygotować podglądu diffu.') }
  }
  function togglePlanItem(itemId: string) { setSelectedPlanItems(previous => previous.includes(itemId) ? previous.filter(value => value !== itemId) : [...previous, itemId]) }
  async function applyPlan() {
    if (!workspace || !plan) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans/${plan.planId}/apply`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ itemIds: selectedPlanItems, retry: false }) })
    if (response.ok) setApplyJob(await response.json()); else { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się uruchomić wykonania planu.') }
  }
  async function refreshApplyJob() {
    if (!workspace || !applyJob || !['QUEUED', 'APPLYING'].includes(applyJob.status)) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans/apply/jobs/${applyJob.jobId}`)
    if (response.ok) { const refreshed = await response.json() as ApplyJob; setApplyJob(refreshed); if (['APPLIED', 'FAILED'].includes(refreshed.status)) await loadPlan() }
  }
  useEffect(() => { if (!applyJob || !['QUEUED', 'APPLYING'].includes(applyJob.status)) return; const timer = window.setInterval(refreshApplyJob, 2000); return () => window.clearInterval(timer) }, [applyJob?.jobId, applyJob?.status])
  async function loadGitStatus() { if (!workspace || !plan) return; const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans/${plan.planId}/git-status`); if (response.ok) setGitStatus(await response.json()); else setError('Nie udało się pobrać statusu Git.') }
  async function commitPlan() { if (!workspace || !plan) return; const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans/${plan.planId}/commit`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ message: commitMessage, push: pushAfterCommit }) }); if (response.ok) setCommitJob(await response.json()); else { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się uruchomić commita.') } }
  async function refreshCommitJob() { if (!workspace || !commitJob || !['QUEUED', 'COMMITTING'].includes(commitJob.status)) return; const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/plans/commit/jobs/${commitJob.jobId}`); if (response.ok) { const refreshed = await response.json() as GitCommitJob; setCommitJob(refreshed); if (refreshed.status === 'COMPLETED') await loadGitStatus() } }
  useEffect(() => { if (!commitJob || !['QUEUED', 'COMMITTING'].includes(commitJob.status)) return; const timer = window.setInterval(refreshCommitJob, 2000); return () => window.clearInterval(timer) }, [commitJob?.jobId, commitJob?.status])
  async function restartStageFromUi() {
    if (!workspace) return
    if (!window.confirm(`Wyniki etapu ${restartStage} i etapów zależnych zostaną usunięte. Kontynuować?`)) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/stages/${restartStage}/restart`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ confirmed: true }) })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się ponowić etapu.'); return }
    const result = await response.json(); setRestartResult(`${result.stage}: usunięto ${result.removedOutputs.length} artefaktów. Etap jest gotowy do ponownego uruchomienia.`); setPlan(undefined); setPlanDiff(undefined); setApplyJob(undefined); setCommitJob(undefined)
  }
  async function loadHistory() { if (!workspace) return; const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/history`); if (response.ok) setHistory(await response.json()); else setError('Nie udało się pobrać historii workspace’u.') }
  useEffect(() => {
    if (!planningJob || !['QUEUED', 'PLANNING'].includes(planningJob.status)) return
    const timer = window.setInterval(refreshPlanning, 2000); return () => window.clearInterval(timer)
  }, [planningJob?.jobId, planningJob?.status])
  useEffect(() => {
    if (!correlationJob || !['QUEUED', 'RUNNING'].includes(correlationJob.status)) return
    const timer = window.setInterval(refreshCorrelation, 2000); return () => window.clearInterval(timer)
  }, [correlationJob?.jobId, correlationJob?.status])
  async function exportConfiguration() {
    if (!workspace) return
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/configuration/export`)
    if (!response.ok) { setError('Nie udało się pobrać konfiguracji workspace’u.'); return }
    const blob = await response.blob(); const url = URL.createObjectURL(blob)
    const link = document.createElement('a'); link.href = url; link.download = `workspace-${workspace.workspaceId}-config.json`; link.click(); URL.revokeObjectURL(url)
  }
  async function previewImport(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    try {
      const imported = JSON.parse(await file.text()) as WorkspaceConfiguration
      if (imported.schemaVersion !== configuration.schemaVersion) { setError(`Nieobsługiwana wersja schematu: ${imported.schemaVersion ?? 'brak'}.`); return }
      setPendingImport({ file, configuration: imported }); setError('')
    } catch { setError('Plik konfiguracji nie zawiera poprawnego JSON.'); setPendingImport(undefined) }
  }
  async function applyImport() {
    if (!workspace || !pendingImport) return
    const form = new FormData(); form.append('file', pendingImport.file)
    const response = await fetch(`/ui-api/workspaces/${workspace.workspaceId}/configuration/import`, { method: 'PUT', body: form })
    if (!response.ok) { const body = await response.json().catch(() => undefined); setError(body?.message ?? 'Nie udało się zaimportować konfiguracji.'); return }
    setConfiguration(await response.json()); setPendingImport(undefined); setConfigurationSaved(true)
  }
  return <main className="shell">
    <p className="eyebrow">UI-R03</p><h1>Nowy workspace</h1><p className="muted">Wskaż lokalny checkout albo utwórz go przez klonowanie Git.</p>
    <div className="switch"><button type="button" className={mode === 'local' ? 'selected' : ''} onClick={() => setMode('local')}>Lokalny checkout</button><button type="button" className={mode === 'git' ? 'selected' : ''} onClick={() => setMode('git')}>Repozytorium Git</button></div>
    {mode === 'local' && <form onSubmit={submit} className="card form"><label>Ścieżka repozytorium<input value={repositoryPath} onChange={event => setRepositoryPath(event.target.value)} placeholder="C:/workspace/project" required /></label><button type="submit">Utwórz workspace</button></form>}
    {mode === 'git' && <form onSubmit={startClone} className="card form"><label>URL repozytorium Git<input value={repositoryUrl} onChange={event => setRepositoryUrl(event.target.value)} placeholder="https://git.example/team/project.git" required /></label><label>Branch (opcjonalnie)<input value={branch} onChange={event => setBranch(event.target.value)} placeholder="main" /></label><label>Katalog checkoutu (opcjonalnie)<input value={clonePath} onChange={event => setClonePath(event.target.value)} placeholder="./work/project" /></label><div className="actions"><button type="button" onClick={proposeClone}>Podpowiedz ścieżkę</button><button type="submit">Klonuj i utwórz workspace</button></div>{proposedPath && <p className="muted">Proponowana ścieżka: <code>{proposedPath}</code></p>}{cloneJob && <p role="status">Klonowanie: <strong>{cloneJob.status}</strong>{cloneJob.errorMessage && ` — ${cloneJob.errorMessage}`}</p>}</form>}
    {error && <p className="error" role="alert">{error}</p>}
    {workspace && <><section className="card"><h2>Workspace utworzony</h2><dl><dt>Id</dt><dd>{workspace.workspaceId}</dd><dt>Checkout</dt><dd>{workspace.repositoryPath}</dd><dt>Branch</dt><dd>{workspace.branch ?? '—'}</dd><dt>Commit</dt><dd>{workspace.commitSha ?? '—'}</dd><dt>Stan</dt><dd>{workspace.clean ? 'CLEAN' : 'LOCAL_CHANGES'}</dd></dl></section>
      {configurationLoaded && <form className="card form configuration" onSubmit={saveConfiguration}><div><h2>Konfiguracja workspace’u</h2><p className="muted">Ustawienia sterują zakresem kolejnych etapów pipeline’u. Wartości domyślne są gotowe do użycia.</p></div>
        <fieldset><legend>Zakres projektów</legend><label>Projekty analizowane <span className="help">Pełne groupId:artifactId, * oznacza wszystkie widoczne projekty.</span><textarea value={joinValues(configuration.analysisProjects)} onChange={event => updateConfiguration('analysisProjects', splitValues(event.target.value))} /></label><label>Projekty edytowalne <span className="help">Lista projektów, których POM-y mogą zostać zmodyfikowane.</span><textarea value={joinValues(configuration.editableProjects)} onChange={event => updateConfiguration('editableProjects', splitValues(event.target.value))} /></label></fieldset>
        <fieldset><legend>Wersjonowanie i strategie</legend><label>Poziom aktualizacji<select value={configuration.updateLevel} onChange={event => updateConfiguration('updateLevel', event.target.value)}><option>PATCH</option><option>MINOR</option><option>MAJOR</option></select></label><label>Poziom CVE Override<select value={configuration.cveOverrideUpdateLevel} onChange={event => updateConfiguration('cveOverrideUpdateLevel', event.target.value)}><option>PATCH</option><option>MINOR</option><option>MAJOR</option></select></label><label>Strategia niejednoznaczności<select value={configuration.ambiguousStrategy} onChange={event => updateConfiguration('ambiguousStrategy', event.target.value)}><option>SHALLOWEST</option><option>DEEPEST</option><option>ALL</option></select></label><label>Pomijane biblioteki <span className="help">Jeden wzorzec w osobnej linii.</span><textarea value={joinValues(configuration.skipLibraries)} onChange={event => updateConfiguration('skipLibraries', splitValues(event.target.value))} /></label><label>Wyłączone strategie <span className="help">Np. CVE_OVERRIDE; jedna wartość w osobnej linii.</span><textarea value={joinValues(configuration.disabledStrategies)} onChange={event => updateConfiguration('disabledStrategies', splitValues(event.target.value))} /></label></fieldset>
        <fieldset><legend>Repozytoria Maven i proxy</legend><label>Repozytoria Maven <span className="help">Adres każdego repozytorium w osobnej linii.</span><textarea value={joinValues(configuration.mavenRepositories)} onChange={event => updateConfiguration('mavenRepositories', splitValues(event.target.value))} /></label><div className="two-columns"><label>Host proxy<input value={configuration.proxyHost ?? ''} onChange={event => updateConfiguration('proxyHost', event.target.value)} placeholder="proxy.example" /></label><label>Port proxy<input type="number" value={configuration.proxyPort ?? ''} onChange={event => updateConfiguration('proxyPort', event.target.value ? Number(event.target.value) : undefined)} placeholder="8080" /></label></div></fieldset>
        <fieldset><legend>Apply</legend><label className="checkbox"><input type="checkbox" checked={configuration.ignoreLocalChanges} onChange={event => updateConfiguration('ignoreLocalChanges', event.target.checked)} /> Ignoruj lokalne zmiany (blokuje push)</label></fieldset><div className="actions"><button type="submit">Zapisz konfigurację</button><button type="button" onClick={exportConfiguration}>Pobierz config.json</button><label className="file-button">Wczytaj config.json<input type="file" accept="application/json,.json" onChange={previewImport} /></label>{configurationSaved && <span className="success" role="status">Konfiguracja zapisana.</span>}</div>
        {pendingImport && <section className="import-preview"><h3>Podgląd importu</h3><p className="muted">Plik <code>{pendingImport.file.name}</code> jest poprawny. Zostanie zastąpiona bieżąca konfiguracja workspace’u.</p><pre>{JSON.stringify(pendingImport.configuration, null, 2)}</pre><div className="actions"><button type="button" onClick={applyImport}>Zastosuj import</button><button type="button" className="secondary" onClick={() => setPendingImport(undefined)}>Anuluj</button></div></section>}
      </form>}
      {configurationLoaded && <section className="card project-scope"><div><h2>Projekty Maven</h2><p className="muted">Wybierz niezależnie projekty do analizy oraz projekty, których źródła mogą być modyfikowane.</p></div><input value={projectFilter} onChange={event => setProjectFilter(event.target.value)} placeholder="Filtruj po groupId, artifactId, wersji lub ścieżce" /><div className="project-list">{projects.filter(project => `${project.coordinate} ${project.pomPath}`.toLowerCase().includes(projectFilter.toLowerCase())).map(project => <article className="project-row" key={project.coordinate}><div><strong>{project.coordinate}</strong><span className="muted">{project.version} · {project.pomPath}</span></div><label className="checkbox"><input type="checkbox" checked={project.analysisSelected} onChange={() => toggleProject(project.coordinate, 'analysisSelected')} /> Analiza</label><label className="checkbox"><input type="checkbox" checked={project.editableSelected} onChange={() => toggleProject(project.coordinate, 'editableSelected')} /> Edycja</label></article>)}</div><div className="actions"><button type="button" onClick={saveScope}>Zapisz zakres projektów</button><button type="button" onClick={startMavenAnalysis} disabled={analysisRunning || !projects.some(project => project.analysisSelected)}>Uruchom analizę Maven</button>{scopeSaved && <span className="success" role="status">Zakres zapisany.</span>}</div></section>}
      {mavenJobs.length > 0 && <section className="card analysis-jobs"><h2>Analiza Maven</h2><div className="job-list">{mavenJobs.map(job => <article className="job-row" key={job.jobId}><div><strong>{job.projectCoordinate}</strong><span className="muted">{job.pomPath}</span></div><span className={`status status-${job.status.toLowerCase()}`}>{job.status}</span>{job.errorMessage && <p className="error">{job.errorMessage}</p>}<details><summary>Pokaż polecenie i output</summary><code>$ {job.command}</code><pre>{job.stdout}{job.stderr}</pre></details></article>)}</div></section>}
      <section className="card report-import"><h2>Raport skanowania</h2><p className="muted">Zaimportuj raport OWASP lub Checkmarx. Raport może obejmować całe repozytorium.</p><form className="actions" onSubmit={importReport}><input id="report-file" type="file" accept="application/json,.json" required /><select value={reportFormat} onChange={event => setReportFormat(event.target.value)}><option value="">Rozpoznaj format</option><option value="OWASP">OWASP</option><option value="CHECKMARX">CHECKMARX</option></select><button type="submit">Importuj raport</button></form>{report && <div className="report-summary"><strong>{report.format} · {report.status}</strong><span>{report.sourceFileName} · {report.sizeBytes} B · {report.vulnerableLibraries} bibliotek · {report.cves} CVE</span><code>SHA-256: {report.sha256}</code>{report.warnings.length > 0 && <p className="warning">Ostrzeżenia: {report.warnings.join(' · ')}</p>}{report.errors.length > 0 && <p className="error">Błędy: {report.errors.join(' · ')}</p>}</div>}</section>
      <section className="card correlation"><h2>Korelacja z grafem</h2><p className="muted">Połącz wykryte biblioteki z wynikami analizy dependency:tree.</p><button type="button" onClick={startCorrelation} disabled={!report || !mavenJobs.some(job => job.status === 'COMPLETED') || !!correlationJob && ['QUEUED', 'RUNNING'].includes(correlationJob.status)}>Uruchom korelację</button>{correlationJob && <div className="report-summary"><strong>{correlationJob.status}</strong>{correlationJob.status === 'COMPLETED' && <><span>{correlationJob.libraries} bibliotek · MATCHED: {correlationJob.matched} · AMBIGUOUS: {correlationJob.ambiguous} · ORPHAN: {correlationJob.orphan}</span><button type="button" onClick={loadOccurrenceReview}>Przejdź do przeglądu occurrence</button></>}{correlationJob.warnings && <p className="warning">{correlationJob.warnings}</p>}{correlationJob.errors && <p className="error">{correlationJob.errors}</p>}</div>}</section>
      {occurrenceReview && <section className="card occurrence-review"><h2>Przegląd occurrence</h2><p className="muted">Wybierz occurrence context albo oznacz dopasowanie do ręcznej obsługi.</p><div className="review-list">{occurrenceReview.entries.map(entry => <article className="review-entry" key={entry.libraryKey}><div className="review-heading"><strong>{entry.libraryKey}</strong><span className={`status status-${entry.status.toLowerCase()}`}>{entry.status}</span></div>{entry.occurrences.length === 0 ? <p className="warning">Brak dopasowania w grafie. Możliwa obsługa ręczna.</p> : entry.occurrences.map(occurrence => <div className="occurrence-row" key={occurrence.occurrenceId}><div><strong>{occurrence.projectId}</strong><span className="muted">ścieżka: {occurrence.pathId} · wersja: {occurrence.reportedVersion}</span></div><span className="muted">{occurrence.action ?? 'NIE WYBRANO'}</span><div className="actions"><button type="button" onClick={() => decideOccurrence(occurrence.occurrenceId, 'CONTEXT')}>Wybierz kontekst</button><button type="button" onClick={() => loadStrategyOptions(occurrence.occurrenceId)}>Wybierz punkt zmiany</button><button type="button" className="secondary" onClick={() => decideOccurrence(occurrence.occurrenceId, 'MANUAL')}>Ręczna obsługa</button><button type="button" className="secondary" onClick={() => decideOccurrence(occurrence.occurrenceId, 'SKIP')}>Pomiń</button></div></div>)}</article>)}</div></section>}
      {strategyOptions && <form className="card strategy-selection" onSubmit={saveStrategy}><h2>Decyzja zmiany</h2><p className="muted">Projekt: {strategyOptions.projectId} · artefakt: {strategyOptions.libraryKey} · węzeł: {strategyOptions.nodeId}</p><label>Strategia<select value={strategy} onChange={event => setStrategy(event.target.value)}>{strategyOptions.strategies.map(value => <option key={value}>{value}</option>)}</select></label><label>Wersja docelowa<select value={targetVersion} onChange={event => setTargetVersion(event.target.value)}>{strategyOptions.targetVersions.map(value => <option key={value}>{value}</option>)}</select></label><div className="actions"><button type="submit">Zapisz decyzję</button></div></form>}
      <section className="card planning"><h2>Automatyczne przygotowanie planu</h2><p className="muted">Utwórz PlanItem dla bibliotek, które można obsłużyć na podstawie raportu i korelacji. Źródła nie są modyfikowane.</p><button type="button" onClick={startPlanning} disabled={!correlationJob || correlationJob.status !== 'COMPLETED' || !!planningJob && ['QUEUED', 'PLANNING'].includes(planningJob.status)}>Przygotuj plan</button>{planningJob && <div className="report-summary"><strong>{planningJob.status}</strong><span>zaplanowano: {planningJob.planned} · pominięto: {planningJob.skipped}</span>{planningJob.outputPath && <><code>{planningJob.outputPath}</code><button type="button" onClick={loadPlan}>Otwórz plan</button></>}{planningJob.errorMessage && <p className="error">{planningJob.errorMessage}</p>}</div>}</section>
      {plan && <section className="card plan"><div className="plan-heading"><div><h2>Plan {plan.planId}</h2><p className="muted">Status: {plan.status ?? 'PLANNED'} · poziom aktualizacji: {plan.updateLevel} · {plan.items.length} PlanItem</p></div><div className="actions"><button type="button" onClick={previewPlanDiff}>Podgląd diffu</button><button type="button" onClick={applyPlan} disabled={!!applyJob && ['QUEUED', 'APPLYING'].includes(applyJob.status) || !plan.items.some(item => item.status !== 'APPLIED' && item.status !== 'SKIPPED')}>Zastosuj zaznaczone</button></div></div>{applyJob && <div className="report-summary"><strong>Wykonanie: {applyJob.status}</strong><span>wybrane: {applyJob.selected} · wykonane: {applyJob.applied} · błędne: {applyJob.failed} · pominięte: {applyJob.skipped}</span>{applyJob.errorMessage && <p className="error">{applyJob.errorMessage}</p>}</div>}<div className="plan-list">{plan.items.map(item => <article className="plan-item" key={item.itemId}><label className="checkbox"><input type="checkbox" checked={selectedPlanItems.includes(item.itemId)} disabled={item.status === 'APPLIED' || item.status === 'SKIPPED'} onChange={() => togglePlanItem(item.itemId)} /> Wykonaj</label><div><strong>{item.artifact.groupId}:{item.artifact.artifactId}</strong><span className="muted">{item.project.groupId}:{item.project.artifactId} · {item.project.pomPath}</span></div><label>Strategia<select value={item.strategy} onChange={event => updatePlanItem(item.itemId, event.target.value, item.targetVersion, item.status)}><option>DIRECT_DEPENDENCY</option><option>DEPENDENCY_MANAGEMENT</option><option>PROPERTY</option><option>CVE_OVERRIDE</option></select></label><label>Wersja docelowa<input value={item.targetVersion} onChange={event => updatePlanItem(item.itemId, item.strategy, event.target.value, item.status)} /></label><label>Status<select value={item.status ?? 'PLANNED'} onChange={event => updatePlanItem(item.itemId, item.strategy, item.targetVersion, event.target.value)}><option>PLANNED</option><option>SKIPPED</option></select></label>{item.errorMessage && <p className="error">{item.errorMessage}</p>}</article>)}</div>{planDiff && <section className="diff-preview"><h3>Podgląd zmian</h3><p className="muted">Podgląd jest tylko do odczytu — pliki źródłowe nie zostały zmienione.</p>{planDiff.items.map(item => <article className="diff-item" key={item.itemId}><div><strong>{item.artifact}</strong><span className="muted">{item.pomPath} · {item.strategy} · {item.currentVersion} → {item.targetVersion}</span></div>{item.errorMessage ? <p className="error">{item.errorMessage}</p> : item.changed ? <pre>{item.diff}</pre> : <p className="muted">Brak zmian.</p>}</article>)}</section>}</section>}
      {plan && <section className="card git-actions"><div className="plan-heading"><div><h2>Commit i push</h2><p className="muted">Po poprawnym apply sprawdź zmienione pliki i jawnie zatwierdź zmiany.</p></div><button type="button" onClick={loadGitStatus}>Odśwież status Git</button></div>{gitStatus && <div className="report-summary"><strong>{gitStatus.branch ?? 'HEAD'} · {gitStatus.clean ? 'CLEAN' : 'LOCAL CHANGES'}</strong><span>Commit bazowy: {gitStatus.commitSha ?? '—'}</span>{gitStatus.changedFiles.length > 0 && <ul>{gitStatus.changedFiles.map(file => <li key={file}><code>{file}</code></li>)}</ul>}</div>}<label>Komunikat commita<input value={commitMessage} onChange={event => setCommitMessage(event.target.value)} /></label><label className="checkbox"><input type="checkbox" checked={pushAfterCommit} onChange={event => setPushAfterCommit(event.target.checked)} /> Wypchnij commit do origin</label><div className="actions"><button type="button" onClick={commitPlan} disabled={!gitStatus || gitStatus.clean || !!commitJob && ['QUEUED', 'COMMITTING'].includes(commitJob.status)}>Commituj</button>{commitJob && <span className={`status status-${commitJob.status.toLowerCase()}`}>{commitJob.status}{commitJob.commitSha && ` · ${commitJob.commitSha}`}</span>}</div>{commitJob?.errorMessage && <p className="error">{commitJob.errorMessage}</p>}</section>}
      {workspace && <section className="card stage-restart"><h2>Powrót i ponowienie etapu</h2><p className="muted">Wybierz etap, którego wynik oraz zależne artefakty mają zostać unieważnione. Następnie uruchom etap ponownie odpowiednim przyciskiem.</p><div className="actions"><select value={restartStage} onChange={event => setRestartStage(event.target.value)}><option>CONFIGURATION</option><option>PROJECT_SCOPE</option><option>MAVEN_ANALYSIS</option><option>REPORT</option><option>CORRELATION</option><option>OCCURRENCES</option><option>STRATEGIES</option><option>PLAN</option><option>APPLY</option></select><button type="button" className="secondary" onClick={restartStageFromUi}>Wróć i unieważnij</button></div>{restartResult && <p className="success" role="status">{restartResult}</p>}</section>}
      {workspace && <section className="card history"><div className="plan-heading"><div><h2>Historia workspace’u</h2><p className="muted">Read-only: zdarzenia i zapisane artefakty etapów.</p></div><button type="button" onClick={loadHistory}>Pokaż historię</button></div>{history && <><h3>Zdarzenia</h3>{history.events.length === 0 ? <p className="muted">Brak zapisanych zdarzeń.</p> : <div className="history-list">{history.events.map(event => <article key={event.eventId}><strong>{event.stage} · {event.action}</strong><span className="muted">{event.timestamp}</span><code>{event.details || '—'}</code></article>)}</div>}<h3>Artefakty</h3><div className="history-artifacts">{history.artifacts.map(artifact => <div key={artifact.path}><code>{artifact.path}</code><span className="muted">{artifact.sizeBytes} B · {artifact.modifiedAt ?? '—'}</span><a href={`/ui-api/workspaces/${workspace.workspaceId}/artifacts/${artifact.path}`} download>Pobierz</a></div>)}</div></>}</section>}
    </>}
  </main>
}
createRoot(document.getElementById('root')!).render(<App />)
