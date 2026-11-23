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

export type OwaspScanScope = {
  scopeId: string
  rootProjectId: string
  projectName: string
  pomPath: string
  groupId?: string
  artifactId?: string
  version?: string
  aggregator: boolean
  coveredProjectIds: string[]
  coveredPomPaths: string[]
}

export type OwaspScanPlan = {
  repositoryId: string
  pluginVersion: string
  latestPluginVersion?: string
  scopes: OwaspScanScope[]
  warnings: string[]
}

export type OwaspScanStart = {
  repositoryId: string
  pluginVersion: string
  jobs: Array<{ scopeId: string; projectId: string; jobId: string; status: string; pomPath: string }>
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

export function getOwaspScanPlan(repositoryId: string) {
  return request<OwaspScanPlan>(`/api/repositories/${repositoryId}/owasp-scan/plan`)
}

export function startOwaspScan(repositoryId: string, scopeIds: string[]) {
  return request<OwaspScanStart>(`/api/repositories/${repositoryId}/owasp-scan/start`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scopeIds })
  })
}
