import { useEffect, useState } from 'react'
import { getPlanPreparation, getReport, getVulnerableArtifacts, PlanPreparationRun, ReportMetadata, startPlanPreparation, VulnerableArtifactGroup } from './api'
import { getActivePlan } from '../plans/api'

function groupLabel(group: VulnerableArtifactGroup) { return !group.groupId || !group.artifactId ? 'Nieznany artefakt' : `${group.groupId}:${group.artifactId}:${group.version ?? 'brak wersji'}` }
function severityClass(severity?: string) { return `cvss-chip cvss-${(severity ?? 'unknown').toLowerCase()}` }
function findingLabel(finding: VulnerableArtifactGroup['findings'][number]) { return finding.cve ?? finding.externalId ?? 'Bez identyfikatora' }
function roundedCvss(value?: number) { return value == null ? undefined : value.toFixed(1) }

export function ReportDetailsPage({ reportId }: { reportId: string }) {
  const [report, setReport] = useState<ReportMetadata>()
  const [groups, setGroups] = useState<VulnerableArtifactGroup[]>([])
  const [query, setQuery] = useState('')
  const [severity, setSeverity] = useState('')
  const [planningFilter, setPlanningFilter] = useState<'all' | 'planned' | 'to-plan'>('to-plan')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [correlating, setCorrelating] = useState(false)
  const [activePlan, setActivePlan] = useState<{ planId: string; repositoryId?: string; status: string } | null>(null)
  const [preparationRun, setPreparationRun] = useState<PlanPreparationRun>()
  const [preparationStatus, setPreparationStatus] = useState('')
  const [preparationError, setPreparationError] = useState(false)

  async function loadGroups(value = query) {
    setLoading(true)
    try { setGroups(await getVulnerableArtifacts(reportId, value, severity || undefined, planningFilter === 'all' ? undefined : planningFilter === 'planned')); setError(false) }
    catch { setError(true) } finally { setLoading(false) }
  }
  useEffect(() => {
    setLoading(true)
    Promise.all([getReport(reportId), getVulnerableArtifacts(reportId, undefined, severity || undefined, planningFilter === 'all' ? undefined : planningFilter === 'planned'), getActivePlan()])
      .then(([loadedReport, loadedGroups, loadedPlan]) => { setReport(loadedReport); setGroups(loadedGroups); setActivePlan(loadedPlan); setError(false) })
      .catch(() => setError(true)).finally(() => setLoading(false))
  }, [reportId, severity, planningFilter])
  useEffect(() => {
    const timeout = window.setTimeout(() => { if (report) void loadGroups(query) }, 180)
    return () => window.clearTimeout(timeout)
  }, [query, severity, planningFilter])
  useEffect(() => {
    if (!preparationRun || preparationRun.status === 'COMPLETED' || !preparationRun.items.some(item => item.status === 'QUEUED' || item.status === 'PLANNING')) return
    const timer = window.setInterval(() => { void getPlanPreparation(reportId, preparationRun.runId).then(setPreparationRun).catch(() => setPreparationError(true)) }, 5000)
    return () => window.clearInterval(timer)
  }, [preparationRun, reportId])
  async function correlate() {
    setCorrelating(true); setError(false)
    try { await fetch(`/api/reports/${reportId}/correlation`, { method: 'POST' }); setReport(await getReport(reportId)); await loadGroups() }
    catch { setError(true) } finally { setCorrelating(false) }
  }
  async function preparePlan() {
    setPreparationError(false)
    try { setPreparationRun(await startPlanPreparation(reportId, query, severity || undefined, planningFilter === 'all' ? undefined : planningFilter === 'planned')); setPreparationStatus('') } catch { setPreparationError(true) }
  }
  function closePreparation() { setPreparationRun(undefined); void loadGroups() }
  function handleGroupAction(group: VulnerableArtifactGroup) {
    const planItem = group.planItem
    const findingId = planItem?.findingId ?? group.maxCvssFindingId ?? group.findings[0]?.findingId
    if (!findingId) return
    const params = new URLSearchParams()
    if (planItem) { params.set('occurrenceId', planItem.occurrenceId); params.set('planId', planItem.planId); params.set('itemId', planItem.itemId); params.set('targetProjectId', planItem.targetProjectId); params.set('changePointTreeNodeId', planItem.changePointTreeNodeId); params.set('targetArtifactVersionId', planItem.targetArtifactVersionId); params.set('strategy', planItem.strategy) }
    window.location.hash = `#strategy/${findingId}${params.size ? `?${params}` : ''}`
  }
  function cyclePlanningFilter() { setPlanningFilter(current => current === 'to-plan' ? 'planned' : current === 'planned' ? 'all' : 'to-plan') }
  const planningFilterLabel = planningFilter === 'to-plan' ? 'To plan' : planningFilter === 'planned' ? 'Planned' : 'All'
  const findingCount = groups.reduce((count, group) => count + group.findingCount, 0)
  const canPrepare = !!activePlan && activePlan.repositoryId === report?.repositoryId && activePlan.status === 'PLANNED' && groups.some(group => !group.planItem)
  if (error) return <div className="page"><div className="state-card error-state">Nie udało się pobrać raportu lub findings.</div></div>
  if (report?.status === 'REQUIRES_REGISTRATION') return <div className="page"><a className="back-link" href={report.repositoryId ? `#repository/${report.repositoryId}` : '#reports'}>← Reports</a><p className="eyebrow">UI-09</p><h1>{report.originalFilename}</h1><div className="state-card"><strong>Raport wymaga uzupełnienia kontekstu.</strong><span>Wybierz repozytorium i projekt Maven, aby uruchomić analizę zależności.</span><a className="button primary" href={`#report-context/${report.reportId}`}>Uzupełnij kontekst</a></div></div>
  if (!report) return <div className="page"><div className="state-card">Ładowanie raportu…</div></div>
  return <div className="page">
    <a className="back-link" href={report.repositoryId ? `#repository/${report.repositoryId}` : '#reports'}>← Reports</a><p className="eyebrow">UI-09</p>
    <div className="page-heading"><div><h1>{report.originalFilename}</h1><p className="muted">{report.source ?? 'UNKNOWN'} · {report.format ?? 'UNKNOWN'} · {report.importedAt}</p></div><div className="page-actions"><span className="status-pill status-ready">{report.status}</span><button className="secondary" onClick={preparePlan} disabled={!canPrepare}>Przygotuj plan zmiany</button><button className="primary" onClick={correlate} disabled={correlating}>{correlating ? 'Korelacja…' : 'Uruchom korelację'}</button></div></div>
    <div className="repository-meta"><span><b>Report ID</b><code>{report.reportId}</code></span><span><b>SHA-256</b><code>{report.sha256}</code></span><span><b>Grupy</b>{groups.length}</span><span><b>Findings</b>{findingCount}</span></div>
    <div className="section-heading report-findings-heading"><div><h2>Podatne biblioteki</h2><p className="muted">Grupowanie po groupId, artifactId i wykrytej wersji.</p></div><div className="filter-bar report-search"><input aria-label="Filtruj findings" value={query} onChange={event => setQuery(event.target.value)} placeholder="Filtruj po CVE, groupId, artifactId lub wersji…" /><select aria-label="Filtruj po severity" value={severity} onChange={event => setSeverity(event.target.value)}><option value="">Wszystkie severity</option><option value="CRITICAL">Critical</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option><option value="LOW">Low</option></select><button className="planning-toggle-button" onClick={cyclePlanningFilter} aria-label="Zmień filtr planowania">{planningFilterLabel}</button></div></div>
    {loading ? <div className="state-card">Ładowanie findings…</div> : groups.length === 0 ? <div className="state-card">Brak findings spełniających kryteria.</div> : <div className="report-groups">{groups.map(group => <section className="report-group" key={`${group.groupId ?? 'unknown'}:${group.artifactId ?? 'unknown'}:${group.version ?? 'unknown'}`}><div className="report-group-heading"><strong>{groupLabel(group)}</strong><div className="report-group-summary"><span>{group.occurrenceCount} occurrences</span><span>{group.findingCount} CVE</span>{group.maxCvssScore != null && <span className={severityClass(group.maxCvssSeverity)}>{roundedCvss(group.maxCvssScore)}</span>}{group.planItem && <span className="plan-item-indicator">PlanItem: {group.planItem.status}</span>}<button className="button secondary handle-button" onClick={() => handleGroupAction(group)}>{group.planItem ? 'Edytuj plan' : 'Obsłuż'}</button></div></div><div className="report-group-findings">{group.findings.map(finding => <a className={severityClass(finding.severity) + ' report-cve-chip'} href={`#finding/${finding.findingId}`} key={finding.findingId}><span>{findingLabel(finding)}</span>{finding.cvssScore != null && <small>{roundedCvss(finding.cvssScore)}</small>}</a>)}</div></section>)}</div>}
    {preparationRun && <div className="modal-backdrop"><section className="preparation-modal"><div className="section-heading"><div><h2>Przygotowanie planu zmiany</h2><p className="muted">Run {preparationRun.runId} · {preparationRun.status === 'COMPLETED' ? 'Zakończono' : 'Przetwarzanie'}</p></div><button className="button secondary" onClick={closePreparation}>Zamknij</button></div>{preparationRun.status === 'COMPLETED' && <div className="preparation-summary">Zaplanowano: {preparationRun.items.filter(item => item.status === 'PLANNED').length} · Pominięto: {preparationRun.items.filter(item => item.status === 'SKIPPED').length} · Błędy: {preparationRun.items.filter(item => item.status === 'FAILED').length}</div>}<div className="preparation-toolbar"><strong>{preparationRun.items.filter(item => !preparationStatus || item.status === preparationStatus).length} / {preparationRun.items.length}</strong><select value={preparationStatus} onChange={event => setPreparationStatus(event.target.value)}><option value="">Wszystkie statusy</option><option value="QUEUED">QUEUED</option><option value="PLANNING">PLANNING...</option><option value="PLANNED">PLANNED</option><option value="SKIPPED">SKIPPED</option><option value="FAILED">FAILED</option></select></div>{preparationError && <div className="state-card error-state">Nie udało się pobrać statusu planowania.</div>}<div className="preparation-items">{preparationRun.items.filter(item => !preparationStatus || item.status === preparationStatus).map(item => <div className="preparation-item" key={item.itemId}><code>{item.groupId}:{item.artifactId}:{item.version}</code><span className={`preparation-status preparation-${item.status.toLowerCase()}`}>{item.status === 'PLANNING' ? 'PLANNING...' : item.status}</span>{item.jobId && <a className="list-link" href={`#jobs/${item.jobId}?return=${encodeURIComponent(`#report/${reportId}`)}`}>Job</a>}{item.message && <small>{item.message}</small>}</div>)}</div></section></div>}
  </div>
}
