import { useEffect, useState } from 'react'
import { activatePlan, applyPlan, commitPlan, createPlan, deletePlan, deletePlanItem, getPlan, getPlans, PlanDetails, PlanSummary, revertPlan } from './api'
import { getRepositories, RepositorySummary } from '../repositories/api'

export function PlansPage({ planId }: { planId?: string }) {
  const [plans, setPlans] = useState<PlanSummary[]>([])
  const [repositories, setRepositories] = useState<RepositorySummary[]>([])
  const [createRepositoryId, setCreateRepositoryId] = useState('')
  const [details, setDetails] = useState<PlanDetails>()
  const [error, setError] = useState('')

  async function load() {
    try {
      const [list, repoList] = await Promise.all([getPlans(), getRepositories()])
      setPlans(list); setRepositories(repoList)
      setCreateRepositoryId(current => current || list.find(item => item.active)?.repositoryId || repoList[0]?.repositoryId || '')
      const selected = planId ?? list.find(item => item.active)?.planId ?? list[0]?.planId
      if (selected) setDetails(await getPlan(selected))
    } catch { setError('Nie udało się pobrać planów aktualizacji.') }
  }
  useEffect(() => { void load() }, [planId])

  async function newPlan() {
    const name = window.prompt('Nazwa planu', 'Plan aktualizacji')
    if (!name || !createRepositoryId) return
    try { await createPlan(name, createRepositoryId); window.dispatchEvent(new Event('update-plan-changed')); await load() }
    catch { setError('Nie udało się utworzyć planu.') }
  }
  async function select(plan: PlanSummary) {
    try {
      if (plan.status === 'PLANNED') { await activatePlan(plan.planId); window.dispatchEvent(new Event('update-plan-changed')) }
      window.location.hash = `#plans/${plan.planId}`; setDetails(await getPlan(plan.planId))
    } catch { setError('Nie udało się otworzyć planu.') }
  }
  async function editItem(item: PlanDetails['items'][number]) {
    if (!details || details.plan.status !== 'PLANNED') return
    try {
      if (!details.plan.active) { await activatePlan(details.plan.planId); window.dispatchEvent(new Event('update-plan-changed')) }
      window.location.hash = `#strategy/${item.findingId}?occurrenceId=${encodeURIComponent(item.occurrenceId)}&planId=${encodeURIComponent(item.updatePlanId)}&itemId=${encodeURIComponent(item.itemId)}&targetProjectId=${encodeURIComponent(item.targetProjectId)}&changePointTreeNodeId=${encodeURIComponent(item.changePointTreeNodeId)}&targetArtifactVersionId=${encodeURIComponent(item.targetArtifactVersionId)}&strategy=${encodeURIComponent(item.strategy)}`
    } catch { setError('Nie udało się wybrać planu do edycji.') }
  }
  async function execute(action: 'apply' | 'commit' | 'revert') {
    if (!details) return
    try {
      const result = action === 'apply' ? await applyPlan(details.plan.planId) : action === 'commit' ? await commitPlan(details.plan.planId) : await revertPlan(details.plan.planId)
      window.location.hash = `#jobs/${result.jobId}?return=${encodeURIComponent(`#plans/${details.plan.planId}`)}`
    } catch { setError('Nie udało się uruchomić operacji planu.') }
  }
  async function removePlan() {
    if (!details || !window.confirm('Czy na pewno usunąć cały plan wraz ze wszystkimi PlanItem?')) return
    try { await deletePlan(details.plan.planId); window.dispatchEvent(new Event('update-plan-changed')); window.location.hash = '#plans'; setDetails(undefined); await load() }
    catch { setError('Nie udało się usunąć planu.') }
  }
  async function removeItem(item: PlanDetails['items'][number]) {
    if (!details || !window.confirm(`Czy na pewno usunąć PlanItem ${item.currentVersion ?? '—'} → ${item.targetVersion ?? '—'}?`)) return
    try { await deletePlanItem(details.plan.planId, item.itemId); setDetails(await getPlan(details.plan.planId)) }
    catch { setError('Nie udało się usunąć PlanItem.') }
  }

  return <div className="page">
    <div className="page-heading"><div><p className="eyebrow">UI-13</p><h1>Update plans</h1><p className="muted">Przegląd i przygotowanie planów zmian.</p></div><div className="workspace-actions"><select value={createRepositoryId} onChange={event => setCreateRepositoryId(event.target.value)} aria-label="Repozytorium nowego planu"><option value="">Repozytorium…</option>{repositories.map(repo => <option value={repo.repositoryId} key={repo.repositoryId}>{repo.name}</option>)}</select><button className="button primary" onClick={newPlan} disabled={!createRepositoryId}>Nowy plan</button></div></div>
    {error && <div className="state-card error-state">{error}</div>}
    <div className="plans-layout"><section className="state-card"><strong>Plany</strong>{plans.length === 0 ? <span className="muted">Brak planów.</span> : plans.map(plan => <button className={`plan-row ${plan.active ? 'selected' : ''}`} key={plan.planId} onClick={() => void select(plan)}><span><strong>{plan.taskId} · {plan.name}</strong><small>{plan.status} · {plan.itemCount} PlanItem</small></span><em>{plan.active ? 'AKTYWNY' : plan.status === 'PLANNED' ? 'WYBIERZ' : 'PODGLĄD'}</em></button>)}</section>
    {details && <section className="state-card"><div className="section-heading"><div><strong>{details.plan.taskId} · {details.plan.name}</strong><span className="muted">{details.plan.status} · {details.items.length} elementów</span></div><button className="button danger" disabled={!['PLANNED', 'FAILED'].includes(details.plan.status)} onClick={() => void removePlan()}>Usuń plan</button></div>{details.warnings.map(warning => <div className="warning-state" key={warning}>{warning}</div>)}{details.items.length === 0 ? <span className="muted">Plan nie zawiera jeszcze decyzji.</span> : <div className="plan-items">{details.items.map(item => <div className="plan-item-row" key={item.itemId}><span><strong>{item.currentVersion ?? '—'} → {item.targetVersion ?? '—'}</strong><small>{item.strategy} · status: {item.status}{item.appliedFromVersion ? ` · zastosowano od ${item.appliedFromVersion}` : ''}</small>{item.errorMessage && <small className="plan-item-error">{item.errorMessage}</small>}</span><span className="plan-item-actions"><button className="list-link" disabled={details.plan.status !== 'PLANNED'} onClick={() => void editItem(item)}>Edytuj</button><button className="list-link danger-link" disabled={item.status === 'APPLIED'} onClick={() => void removeItem(item)}>Usuń</button></span></div>)}</div>}<div className="workspace-actions"><button className="button primary" disabled={!['PLANNED', 'FAILED'].includes(details.plan.status) || !details.items.length} onClick={() => void execute('apply')}>Apply i pokaż diff</button><button className="button secondary" disabled={details.plan.status !== 'APPLIED'} onClick={() => void execute('commit')}>Commit i walidacja</button><button className="button secondary" disabled={!['APPLIED', 'COMMITTED', 'FAILED'].includes(details.plan.status) || !details.plan.branchName} onClick={() => void execute('revert')}>Revert</button></div></section>}
    </div>
  </div>
}
