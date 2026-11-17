import { useState } from 'react'
import { discoverProjects, ProjectCandidate, registerProjects } from './api'

export function ProjectDiscoveryPage({ repositoryId }: { repositoryId: string }) {
  const [candidates, setCandidates] = useState<ProjectCandidate[]>()
  const [selected, setSelected] = useState<string[]>([])
  const [error, setError] = useState('')
  const [discovering, setDiscovering] = useState(false)
  const [saving, setSaving] = useState(false)

  async function discover() {
    setError('')
    setDiscovering(true)
    try {
      const result = await discoverProjects(repositoryId)
      setCandidates(result.candidates)
      setSelected(result.candidates.filter(item => !item.registered && !item.error).map(item => item.pomPath))
    } catch {
      setError('Nie udało się wyszukać plików pom.xml.')
    } finally {
      setDiscovering(false)
    }
  }

  async function register() {
    setSaving(true)
    setError('')
    try {
      await registerProjects(repositoryId, selected)
      window.location.hash = `#repository/${repositoryId}`
    } catch {
      setError('Nie udało się zarejestrować wybranych projektów.')
    } finally {
      setSaving(false)
    }
  }

  return <div className="page" aria-busy={discovering}><a className="back-link" href={`#repository/${repositoryId}`}>← Repository</a><p className="eyebrow">UI-05</p><h1>Detect Maven projects</h1><p className="muted">Wyszukaj pliki pom.xml w checkoutcie i wybierz projekty do rejestracji.</p><div className="workspace-actions"><button className="primary" onClick={discover} disabled={discovering}>{discovering ? 'Wczytywanie…' : 'Wyszukaj pom.xml'}</button>{candidates && <button onClick={register} disabled={!selected.length || saving || discovering}>{saving ? 'Rejestrowanie…' : `Zarejestruj wybrane (${selected.length})`}</button>}</div>{discovering && <div className="state-card" role="status">Wczytywanie listy projektów… Git analizuje śledzone pliki POM.</div>}{error && <div className="state-card error-state" role="alert">{error}</div>}{!discovering && candidates && candidates.length === 0 && <div className="state-card">Nie znaleziono plików pom.xml.</div>}{!discovering && candidates && candidates.length > 0 && <div className="project-table">{candidates.map(candidate => <label className="project-row" key={candidate.pomPath}><input type="checkbox" checked={selected.includes(candidate.pomPath)} disabled={candidate.registered || !!candidate.error} onChange={event => setSelected(current => event.target.checked ? [...current, candidate.pomPath] : current.filter(item => item !== candidate.pomPath))} /><span><strong>{candidate.artifactId ?? 'Nieprawidłowy POM'}</strong><small>{candidate.pomPath}</small><small>{candidate.error ?? `${candidate.groupId ?? 'brak groupId'} · ${candidate.version ?? 'brak version'} · ${candidate.modules.length} modules`}</small></span><em>{candidate.registered ? 'REGISTERED' : candidate.error ? 'ERROR' : 'NEW'}</em></label>)}</div>}</div>
}
