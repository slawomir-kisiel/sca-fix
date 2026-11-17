import { FormEvent, useState } from 'react'

async function registerLocal(clonePath: string) {
  const response = await fetch('/api/repositories/local', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ clonePath }),
  })
  if (!response.ok) { const error = await response.json().catch(() => undefined); throw new Error(error?.message ?? 'Nie udało się zarejestrować checkoutu.') }
  return response.json() as Promise<{ repositoryId: string }>
}

export function RepositoryRegistrationPage() {
  const [mode, setMode] = useState<'local' | 'git'>('local')
  const [clonePath, setClonePath] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [branch, setBranch] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (mode === 'local' && !clonePath.trim()) { setError('Wskaż folder lokalnego checkoutu.'); return }
    if (mode === 'git' && !repositoryUrl.trim()) { setError('Podaj adres repozytorium Git.'); return }
    setSaving(true)
    try {
      if (mode === 'local') {
        const repository = await registerLocal(clonePath.trim())
        window.location.hash = `#repository/${repository.repositoryId}`
      } else {
        const response = await fetch('/api/repositories/git', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ repositoryUrl, clonePath: clonePath || undefined, branch: branch || undefined }) })
        if (!response.ok) { const apiError = await response.json().catch(() => undefined); throw new Error(apiError?.message ?? 'Nie udało się rozpocząć klonowania.') }
        const result = await response.json() as { job?: { jobId: string } }
        window.location.hash = result.job?.jobId ? `#jobs/${result.job.jobId}` : '#jobs'
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Operacja rejestracji nie powiodła się.')
    } finally { setSaving(false) }
  }

  return <div className="page">
    <a className="back-link" href="#repositories">← Repositories</a>
    <p className="eyebrow">UI-03</p>
    <h1>Register repository</h1>
    <p className="muted">Wskaż istniejący lokalny checkout Git albo rozpocznij klonowanie repozytorium.</p>
    <form className="workspace-form" onSubmit={submit}>
      <div className="mode-switch"><button type="button" className={mode === 'local' ? 'tab active' : 'tab'} onClick={() => setMode('local')}>Istniejący checkout</button><button type="button" className={mode === 'git' ? 'tab active' : 'tab'} onClick={() => setMode('git')}>Repozytorium Git</button></div>
      {mode === 'git' && <label>Repo URL
        <input value={repositoryUrl} onChange={(event) => setRepositoryUrl(event.target.value)} placeholder="https://git.example.com/team/repository.git" disabled={saving} />
      </label>}
      <label>{mode === 'local' ? 'Folder checkoutu' : 'Folder docelowy (opcjonalnie)'}
        <input value={clonePath} onChange={(event) => setClonePath(event.target.value)} placeholder={mode === 'local' ? 'C:\\work\\my-repository' : './work/repository'} disabled={saving} />
      </label>
      {mode === 'git' && <label>Branch (opcjonalnie)
        <input value={branch} onChange={(event) => setBranch(event.target.value)} placeholder="main" disabled={saving} />
      </label>}
      {error && <div className="error-state state-card" role="alert">{error}</div>}
      <div className="workspace-actions"><a className="button" href="#repositories">Anuluj</a><button className="primary" type="submit" disabled={saving}>{saving ? (mode === 'git' ? 'Klonowanie…' : 'Rejestrowanie…') : (mode === 'git' ? 'Rozpocznij klonowanie' : 'Zarejestruj checkout')}</button></div>
    </form>
  </div>
}
