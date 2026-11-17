export type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'

export type JobResponse = {
  jobId: string
  jobType: string
  status: JobStatus
  stage?: string
  errorCode?: string
  errorMessage?: string
}

export type JobOutputLine = { sequenceNo: number; stream: 'STDOUT' | 'STDERR' | string; content: string; emittedAt: string }

export type HistoryEntry = {
  entryId: string; entryType: 'JOB' | 'PLAN' | 'REPORT' | string; occurredAt: string; finishedAt?: string; status: string
  repositoryId?: string; repositoryName?: string; projectName?: string; jobId?: string; planId?: string; reportId?: string
  taskId?: string; jobType?: string; branchName?: string; baseCommitSha?: string; message?: string; targetHash?: string
}
export type HistoryPage = { entries: HistoryEntry[]; total: number; page: number; size: number; hasNext: boolean }
export type DashboardSummary = { repositories: number; reports: number; openFindings: number; activeJobs: number; plannedPlans: number; recentActivity: HistoryEntry[] }

export async function getHistory(params: { query?: string; types?: string[]; statuses?: string[]; repositoryId?: string; page?: number; size?: number } = {}) {
  const search = new URLSearchParams(); if (params.query) search.set('query', params.query)
  params.types?.forEach(value => search.append('types', value)); params.statuses?.forEach(value => search.append('statuses', value))
  if (params.repositoryId) search.set('repositoryId', params.repositoryId); search.set('page', String(params.page ?? 0)); search.set('size', String(params.size ?? 25))
  const response = await fetch(`/api/history?${search}`); if (!response.ok) throw new Error(`HISTORY_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<HistoryPage>
}

export async function getDashboard() {
  const response = await fetch('/api/dashboard'); if (!response.ok) throw new Error(`DASHBOARD_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<DashboardSummary>
}

export async function getJob(jobId: string): Promise<JobResponse> {
  const response = await fetch(`/api/jobs/${jobId}`)
  if (!response.ok) throw new Error(`JOB_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<JobResponse>
}

export async function getJobOutput(jobId: string, afterSequence = 0): Promise<JobOutputLine[]> {
  const response = await fetch(`/api/jobs/${jobId}/output?afterSequence=${afterSequence}`)
  if (!response.ok) throw new Error(`JOB_OUTPUT_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<JobOutputLine[]>
}
