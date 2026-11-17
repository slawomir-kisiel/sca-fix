import { ReactNode, useEffect, useState } from 'react'
import { activatePlan, getPlans, PlanSummary } from '../../features/plans/api'

type AppLayoutProps = { children: ReactNode }

export function AppLayout({ children }: AppLayoutProps) {
  const [plans, setPlans] = useState<PlanSummary[]>([])
  const [activeId, setActiveId] = useState('')
  async function loadPlans() { try { const result = await getPlans(); setPlans(result); setActiveId(result.find(item => item.active)?.planId ?? '') } catch { setPlans([]) } }
  useEffect(() => { loadPlans(); const refresh = () => loadPlans(); window.addEventListener('update-plan-changed', refresh); return () => window.removeEventListener('update-plan-changed', refresh) }, [])
  async function changePlan(id: string) { if (!id) return; await activatePlan(id); await loadPlans(); window.dispatchEvent(new Event('update-plan-changed')) }
  function newPlan() { window.location.hash = '#plans' }
  return <main className="shell">
    <aside className="sidebar"><div className="brand"><span className="brand-mark">⚒</span> <span>SCA-fix-tool</span></div><nav aria-label="Główna nawigacja"><a className={window.location.hash === '' || window.location.hash === '#' ? 'active' : ''} href="#">Dashboard</a><a className={window.location.hash === '#workspace' ? 'active' : ''} href="#workspace">Workspace</a><a className={window.location.hash === '#repositories' || window.location.hash.startsWith('#repository/') ? 'active' : ''} href="#repositories">Repositories</a><a href="#reports">Reports</a><a href="#plans">Update plans</a><a href="#jobs">Jobs</a><a className={window.location.hash === '#history' ? 'active' : ''} href="#history">History</a></nav></aside>
    <section className="content"><header className="topbar"><span>Local workspace</span><div className="topbar-plan"><label htmlFor="active-plan">Aktywny plan</label><select id="active-plan" value={activeId} onChange={event => changePlan(event.target.value)}><option value="">Brak aktywnego planu</option>{plans.filter(item => item.status === 'PLANNED').map(plan => <option key={plan.planId} value={plan.planId}>{plan.taskId} · {plan.name}</option>)}</select><button className="button" onClick={newPlan}>Nowy</button><span className="status">READY</span></div></header>{children}</section>
  </main>
}
