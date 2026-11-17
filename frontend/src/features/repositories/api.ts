export type RepositorySummary = {
  repositoryId: string
  name: string
  repositoryUrl?: string
  clonePath: string
  defaultBranch?: string
  baseCommitSha?: string
  projectCount: number
  reportCount: number
  activePlanCount: number
  status: 'READY' | 'PARTIAL' | 'FAILED' | string
}

export type RepositoryDetails = {
  summary: RepositorySummary
  branches: unknown[]
  projects: unknown[]
  reports: ReportSummary[]
  plans: unknown[]
  lastJob?: unknown
}

export type ReportSummary = {
  reportId: string
  repositoryId?: string
  originalFilename: string
  contentType?: string
  importedAt: string
  scanDate?: string
  sizeBytes: number
  sha256: string
  status: string
  source?: string
  format?: string
  formatVersion?: string
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) throw new Error(`REPOSITORY_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<T>
}

export function getRepositories() {
  return request<RepositorySummary[]>('/api/repositories')
}

export function getRepository(repositoryId: string) {
  return request<RepositoryDetails>(`/api/repositories/${repositoryId}`)
}
