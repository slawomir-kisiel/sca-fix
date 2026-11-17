export type ProjectCandidate = {
  pomPath: string
  groupId?: string
  artifactId?: string
  version?: string
  packaging?: string
  parent?: { groupId?: string; artifactId?: string; version?: string }
  modules: string[]
  registered: boolean
  error?: string
}

export type ProjectSummary = {
  projectId: string
  repositoryId: string
  name: string
  pomPath: string
  groupId?: string
  artifactId?: string
  version?: string
  packaging?: string
  status: string
  mavenAnalysisStatus: string
  mavenAnalysisJobId?: string
  mavenAnalysisStartedAt?: string
  mavenAnalysisFinishedAt?: string
}

export async function runBatchMavenAnalysis(repositoryId: string, projectIds: string[]) {
  const response = await fetch(`/api/repositories/${repositoryId}/projects/dependency-analysis`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projectIds })
  })
  if (!response.ok) throw new Error('MAVEN_BATCH_ANALYSIS_FAILED')
  return response.json() as Promise<{ jobs: Array<{ jobId: string; status: string }>; skippedProjectIds: string[] }>
}

export async function discoverProjects(repositoryId: string) {
  const response = await fetch(`/api/repositories/${repositoryId}/projects/discover`, { method: 'POST' })
  if (!response.ok) throw new Error('PROJECT_DISCOVERY_FAILED')
  return (await response.json()) as { repositoryId: string; candidates: ProjectCandidate[] }
}

export async function registerProjects(repositoryId: string, pomPaths: string[]) {
  const response = await fetch(`/api/repositories/${repositoryId}/projects`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pomPaths }) })
  if (!response.ok) throw new Error('PROJECT_REGISTRATION_FAILED')
  return response.json()
}

export async function getProjects(repositoryId: string) {
  const response = await fetch(`/api/repositories/${repositoryId}/projects`)
  if (!response.ok) throw new Error('PROJECT_LIST_FAILED')
  return response.json() as Promise<ProjectSummary[]>
}
