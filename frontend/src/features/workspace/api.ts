export type WorkspaceConfig = {
  rootPath: string
  effectivePath: string
  exists: boolean
  writable: boolean
  defaultValue: boolean
}

export async function getWorkspace(): Promise<WorkspaceConfig> {
  const response = await fetch('/api/workspace')
  if (!response.ok) throw new Error(`WORKSPACE_REQUEST_FAILED:${response.status}`)
  return response.json() as Promise<WorkspaceConfig>
}

export async function updateWorkspace(rootPath: string): Promise<WorkspaceConfig> {
  const response = await fetch('/api/workspace', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rootPath }),
  })
  if (!response.ok) throw new Error(`WORKSPACE_UPDATE_FAILED:${response.status}`)
  return response.json() as Promise<WorkspaceConfig>
}

export async function ensureWorkspaceDirectory(): Promise<WorkspaceConfig> {
  const response = await fetch('/api/workspace/ensure-directory', { method: 'POST' })
  if (!response.ok) throw new Error(`WORKSPACE_CREATE_FAILED:${response.status}`)
  return response.json() as Promise<WorkspaceConfig>
}
