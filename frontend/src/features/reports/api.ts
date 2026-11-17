export type ReportMetadata = {
  reportId: string
  repositoryId?: string
  originalFilename: string
  contentType?: string
  source?: string
  format?: string
  formatVersion?: string
  importedAt: string
  scanDate?: string
  sizeBytes: number
  sha256: string
  status: string
}

export type FindingListItem = {
  findingId: string
  externalId?: string
  identifierType?: string
  source?: string
  severity?: string
  cvssScore?: number
  artifactCoordinates?: string
  reportedVersion?: string
  occurrenceCount: number
  correlationStatus: string
}

export type FindingGroupItem = {
  findingId: string
  cve?: string
  externalId?: string
  identifierType?: string
  title?: string
  source?: string
  severity?: string
  cvssScore?: number
  occurrenceCount: number
  correlationStatus: string
  planItem?: PlanItemLink
}

export type PlanItemLink = {
  itemId: string
  planId: string
  findingId: string
  occurrenceId: string
  targetProjectId: string
  changePointTreeNodeId: string
  targetArtifactVersionId: string
  strategy: string
  status: string
}

export type VulnerableArtifactGroup = {
  groupId?: string
  artifactId?: string
  version?: string
  occurrenceCount: number
  findingCount: number
  maxCvssScore?: number
  maxCvssSeverity?: string
  maxCvssFindingId?: string
  planItem?: PlanItemLink
  findings: FindingGroupItem[]
}

export type PlanPreparationItem = {
  itemId: string
  groupId?: string
  artifactId?: string
  version?: string
  maxCvssScore?: number
  jobId?: string
  findingId?: string
  occurrenceId?: string
  status: 'QUEUED' | 'PLANNING' | 'PLANNED' | 'SKIPPED' | 'FAILED'
  message?: string
  updatedAt: string
}
export type PlanPreparationRun = {
  runId: string
  reportId: string
  planId: string
  status: 'RUNNING' | 'COMPLETED'
  createdAt: string
  completedAt?: string
  items: PlanPreparationItem[]
}

export type FindingDetails = FindingListItem & {
  cve?: string
  title?: string
  description?: string
  recommendation?: string
  cvssVersion?: string
  cvssVector?: string
  cwes: string[]
  references: string[]
  affectedArtifacts: Array<{ artifactId: string; purl?: string; groupId?: string; artifactIdValue?: string; reportedVersion?: string; fileName?: string; filePath?: string; sha1?: string; sha256?: string }>
  occurrences: OccurrenceOption[]
}

export type OccurrenceOption = {
  occurrenceId: string
  projectId: string
  artifactVersionId: string
  treeNodeId?: string
  projectName?: string
  pomPath?: string
  groupId?: string
  artifactId?: string
  resolvedVersion?: string
  definitionSource?: string
  sourcesAvailable: boolean
  selected: boolean
  path?: Array<{ treeNodeId: string; artifactVersionId: string; groupId?: string; artifactId?: string; version?: string; relationType: string; locallyManaged: boolean; vulnerable: boolean }>
}

export type TargetVersionOption = {
  artifactVersionId: string
  version: string
  current: boolean
  eligible: boolean
  rejectionReason?: string
  source: string
  repositoryId?: string
  webUrl: string
}

export type TargetVersions = {
  findingId: string
  occurrenceId: string
  reportedVersion?: string
  currentVersion?: string
  rule?: { artifactId: string; pattern?: string; allowSnapshots: boolean }
  versions: TargetVersionOption[]
  selectedArtifactVersionId?: string
  warnings: string[]
  context: {
    repositoryId?: string
    repositoryUrl?: string
    projectName?: string
    pomPath?: string
    groupId?: string
    artifactId?: string
    reportedVersion?: string
    detectedVersion?: string
  }
}

export type UpdateStrategy = 'DIRECT_DEPENDENCY' | 'DEPENDENCY_MANAGEMENT' | 'PROPERTY' | 'CVE_OVERRIDE' | 'DEFER' | 'REJECT'
export type StrategyOptions = {
  findingId: string
  occurrenceId: string
  targetArtifactVersionId?: string
  options: Array<{ strategy: UpdateStrategy; label: string; eligible: boolean; rejectionReason?: string }>
  impact: { occurrenceCount: number; otherOccurrenceCount: number; warning?: string }
  selectedStrategy?: UpdateStrategy
  recommendedStrategy?: UpdateStrategy
  versionDefinition?: { found: boolean; sourceProject?: string; sourcePomPath?: string; section?: string; propertyName?: string; sourceLine?: number; message?: string }
}

export type TargetProject = { projectId: string; name: string; groupId?: string; artifactId?: string; version?: string; pomPath?: string; current: boolean; recommended: boolean; recommendationAvailable: boolean }
export type ChangeGraphNode = { treeNodeId: string; artifactVersionId: string; groupId?: string; artifactId?: string; version: string; locallyManaged: boolean; projectId?: string; analysisStatus: string; selected: boolean; vulnerable: boolean }
export type ChangeGraph = { occurrenceId: string; targetProjectId: string; rootTreeNodeId?: string; targetTreeNodeId: string; targetReachable: boolean; generatedAt: string; sourceCommitSha?: string; nodes: ChangeGraphNode[] }

export function getTargetProjects(findingId: string, occurrenceId: string) {
  return request<TargetProject[]>(`/api/findings/${findingId}/occurrences/${occurrenceId}/target-projects`)
}
export function getChangeGraph(findingId: string, occurrenceId: string, projectId: string) {
  return request<ChangeGraph>(`/api/findings/${findingId}/occurrences/${occurrenceId}/change-graph?projectId=${encodeURIComponent(projectId)}`)
}
export function getChangeStrategies(findingId: string, occurrenceId: string, projectId: string, changePointTreeNodeId: string, targetArtifactVersionId?: string) {
  const params = new URLSearchParams({ projectId, changePointTreeNodeId })
  if (targetArtifactVersionId) params.set('targetArtifactVersionId', targetArtifactVersionId)
  return request<StrategyOptions>(`/api/findings/${findingId}/occurrences/${occurrenceId}/change-strategies?${params}`)
}
export function saveChangeSelection(findingId: string, body: { occurrenceId: string; targetProjectId: string; changePointTreeNodeId: string; targetArtifactVersionId: string; strategy: UpdateStrategy }) {
  return fetch(`/api/findings/${findingId}/occurrences/${body.occurrenceId}/change-selection`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(async response => {
    if (!response.ok) throw new Error(`CHANGE_SELECTION_FAILED:${response.status}`)
    return response.json()
  })
}

async function request<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`REPORT_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<T>
}

export function getReport(reportId: string) { return request<ReportMetadata>(`/api/reports/${reportId}`) }
export function getFindings(reportId: string, status?: string, severity?: string) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (severity) params.set('severity', severity)
  return request<FindingListItem[]>(`/api/reports/${reportId}/findings${params.size ? `?${params}` : ''}`)
}
export function getVulnerableArtifacts(reportId: string, query?: string, severity?: string, planned?: boolean) {
  const params = new URLSearchParams()
  if (query?.trim()) params.set('q', query.trim())
  if (severity) params.set('severity', severity)
  if (planned !== undefined) params.set('planned', String(planned))
  return request<VulnerableArtifactGroup[]>(`/api/reports/${reportId}/vulnerable-artifacts${params.size ? `?${params}` : ''}`)
}
export function startPlanPreparation(reportId: string, query?: string, severity?: string, planned?: boolean) {
  const params = new URLSearchParams()
  if (query?.trim()) params.set('q', query.trim())
  if (severity) params.set('severity', severity)
  if (planned !== undefined) params.set('planned', String(planned))
  return fetch(`/api/reports/${reportId}/plan-preparation${params.size ? `?${params}` : ''}`, { method: 'POST' }).then(async response => {
    if (!response.ok) throw new Error(`PLAN_PREPARATION_FAILED:${response.status}`)
    return response.json() as Promise<PlanPreparationRun>
  })
}
export function getPlanPreparation(reportId: string, runId: string) { return request<PlanPreparationRun>(`/api/reports/${reportId}/plan-preparation/${runId}`) }
export function getFinding(findingId: string) { return request<FindingDetails>(`/api/findings/${findingId}`) }
export function getFindingOccurrences(findingId: string) { return request<OccurrenceOption[]>(`/api/findings/${findingId}/occurrences`) }
export function getTargetVersions(findingId: string, occurrenceId: string, changePointArtifactVersionId?: string, version?: string) {
  const params = new URLSearchParams({ occurrenceId })
  if (changePointArtifactVersionId) params.set('changePointArtifactVersionId', changePointArtifactVersionId)
  if (version) params.set('version', version)
  return request<TargetVersions>(`/api/findings/${findingId}/target-versions?${params}`)
}
export function selectTargetVersion(findingId: string, occurrenceId: string, targetArtifactVersionId: string) {
  return fetch(`/api/findings/${findingId}/target-version`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ occurrenceId, targetArtifactVersionId }) }).then(async response => {
    if (!response.ok) throw new Error(`TARGET_VERSION_SELECTION_FAILED:${response.status}`)
    return response.json() as Promise<{ findingId: string; occurrenceId: string; targetArtifactVersionId: string; selectedAt: string }>
  })
}
export function updateReportContext(reportId: string, body: { repositoryId: string; projectId: string; pomPath: string; branch?: string; commitSha?: string }) {
  return fetch(`/api/reports/${reportId}/context`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(async response => {
    if (!response.ok) throw new Error(`REPORT_CONTEXT_FAILED:${response.status}`)
    return response.json() as Promise<{ job: { jobId: string }; status: string }>
  })
}
