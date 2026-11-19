export type UpdatePlanStatus = 'PLANNED' | 'APPLIED' | 'FAILED' | 'COMMITTED' | 'CANCELLED'
export type PlanSummary = { planId: string; repositoryId?: string; taskId: string; name: string; status: UpdatePlanStatus; active: boolean; createdAt: string; updatedAt: string; itemCount: number; branchName?: string; baseCommitSha?: string }
export type PlanItem = { itemId: string; updatePlanId: string; findingId: string; occurrenceId: string; projectName?: string; targetProjectId: string; changePointTreeNodeId: string; targetArtifactVersionId: string; groupId?: string; artifactId?: string; currentVersion?: string; targetVersion?: string; strategy: string; status: string; appliedFromVersion?: string; errorMessage?: string; selectedAt: string }
export type PlanDetails = { plan: PlanSummary; items: PlanItem[]; warnings: string[] }

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) throw new Error(`UPDATE_PLAN_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<T>
}
export function getPlans(repositoryId?: string) { return request<PlanSummary[]>(repositoryId ? `/api/update-plans?repositoryId=${encodeURIComponent(repositoryId)}` : '/api/update-plans') }
export async function getActivePlan(): Promise<PlanSummary | null> {
  try {
    const response = await fetch('/api/update-plans/active')
    if (!response.ok) throw new Error(`UPDATE_PLAN_REQUEST_FAILED:${response.status}`)
    if (response.status === 204) return null
    const body = await response.text()
    return body.trim() ? JSON.parse(body) as PlanSummary : null
  } catch {
    return null
  }
}
export function createPlan(name: string, repositoryId?: string) { return request<PlanSummary>('/api/update-plans', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, repositoryId }) }) }
export function applyPlan(planId: string) { return request<{ planId: string; jobId: string; status: string }>(`/api/update-plans/${planId}/apply`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ diffConfirmed: true }) }) }
export function commitPlan(planId: string) { return request<{ planId: string; jobId: string; status: string }>(`/api/update-plans/${planId}/commit`, { method: 'POST' }) }
export function revertPlan(planId: string) { return request<{ planId: string; jobId: string; status: string }>(`/api/update-plans/${planId}/revert`, { method: 'POST' }) }
export function activatePlan(planId: string) { return request<PlanSummary>(`/api/update-plans/${planId}/active`, { method: 'PUT' }) }
export function getPlan(planId: string) { return request<PlanDetails>(`/api/update-plans/${planId}`) }
export async function deletePlan(planId: string) {
  const response = await fetch(`/api/update-plans/${planId}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`UPDATE_PLAN_DELETE_FAILED:${response.status}`)
}
export async function deletePlanItem(planId: string, itemId: string) {
  const response = await fetch(`/api/update-plans/${planId}/items/${itemId}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`PLAN_ITEM_DELETE_FAILED:${response.status}`)
}
