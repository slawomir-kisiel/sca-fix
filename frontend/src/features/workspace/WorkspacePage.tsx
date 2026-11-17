import { FormEvent, useEffect, useState } from 'react'
import { ensureWorkspaceDirectory, getWorkspace, updateWorkspace, WorkspaceConfig } from './api'

export function WorkspacePage() {
  const [config, setConfig] = useState<WorkspaceConfig>()
  const [rootPath, setRootPath] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => { getWorkspace().then((value) => { setConfig(value); setRootPath(value.rootPath) })
    .catch(() => setMessage('Nie udało się odczytać konfiguracji workspace.')) }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    try { setConfig(await updateWorkspace(rootPath)); setMessage('Konfiguracja została zapisana.') }
    catch { setMessage('Nie można zapisać ścieżki. Sprawdź, czy katalog istnieje i jest zapisywalny.') }
  }

  async function ensure() {
    try { setConfig(await ensureWorkspaceDirectory()); setMessage('Katalog workspace jest gotowy.') }
    catch { setMessage('Nie udało się utworzyć katalogu workspace.') }
  }

  return <div className="page">
    <p className="eyebrow">UI-02</p>
    <h1>Workspace</h1>
    <p className="muted">Globalny katalog roboczy dla checkoutów repozytoriów.</p>
    <form className="workspace-form" onSubmit={submit}>
      <label>Root workspace
        <input value={rootPath} onChange={(event) => setRootPath(event.target.value)} placeholder="./work" />
      </label>
      <div className="workspace-actions"><button type="button" onClick={ensure}>Utwórz katalog</button><button className="primary" type="submit">Zapisz</button></div>
    </form>
    {config && <p className="muted">Efektywna ścieżka: <code>{config.effectivePath}</code> · {config.exists ? 'istnieje' : 'nie istnieje'} · {config.writable ? 'zapisywalna' : 'niezapisywalna'}</p>}
    {message && <p role="status">{message}</p>}
  </div>
}
