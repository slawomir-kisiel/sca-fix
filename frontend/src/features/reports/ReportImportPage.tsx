import { useEffect, useState } from 'react'
import { getRepositories, type RepositorySummary } from '../repositories/api'

type ImportResponse = { reportId: string; status: string; source?: string; format?: string; formatVersion?: string }

export function ReportImportPage({ initialRepositoryId }: { initialRepositoryId?: string }) {
  const [file, setFile] = useState<File>()
  const [repositoryId, setRepositoryId] = useState(initialRepositoryId ?? '')
  const [progress, setProgress] = useState(0)
  const [result, setResult] = useState<ImportResponse>()
  const [error, setError] = useState<string>()
  const [uploading, setUploading] = useState(false)
  const [repositories, setRepositories] = useState<RepositorySummary[]>([])
  const [repositoriesLoading, setRepositoriesLoading] = useState(true)
  const [repositoriesError, setRepositoriesError] = useState(false)

  useEffect(() => {
    getRepositories()
      .then(setRepositories)
      .catch(() => setRepositoriesError(true))
      .finally(() => setRepositoriesLoading(false))
  }, [])

  function upload() {
    if (!file) { setError('Wybierz plik raportu.'); return }
    setError(undefined); setResult(undefined); setProgress(0); setUploading(true)
    const form = new FormData(); form.append('file', file)
    if (repositoryId.trim()) form.append('repositoryId', repositoryId.trim())
    const request = new XMLHttpRequest()
    request.open('POST', '/api/reports/import')
    request.upload.onprogress = (event) => { if (event.lengthComputable) setProgress(Math.round(event.loaded / event.total * 100)) }
    request.onload = () => { setUploading(false); if (request.status >= 200 && request.status < 300) { setProgress(100); setResult(JSON.parse(request.responseText) as ImportResponse) } else { setError('Nie udało się zapisać raportu.') } }
    request.onerror = () => { setUploading(false); setError('Nie udało się połączyć z backendem.') }
    request.send(form)
  }

  if (repositoriesLoading || repositoriesError || repositories.length >= 0) {
    return <div className="page"><p className="eyebrow">UI-06</p><div className="page-heading"><div><h1>Import security report</h1><p className="muted">Zachowaj oryginalny raport do późniejszej kanonizacji.</p></div></div><div className="upload-card"><label className="field"><span>Plik raportu</span><input type="file" accept=".json,.xml,.zip,application/json,application/xml" onChange={(event) => setFile(event.target.files?.[0])} disabled={uploading} /></label><label className="field"><span>Repository <small>(opcjonalnie)</small></span><select value={repositoryId} onChange={(event) => setRepositoryId(event.target.value)} disabled={uploading || repositoriesLoading}><option value="">Bez przypisania</option>{repositories.map((repository) => <option key={repository.repositoryId} value={repository.repositoryId}>{repository.name}</option>)}</select>{repositoriesLoading && <small className="field-hint">Ładowanie repozytoriów…</small>}{repositoriesError && <small className="field-error">Nie udało się pobrać listy repozytoriów.</small>}</label>{uploading && <div className="upload-progress"><div><span>Przesyłanie pliku</span><b>{progress}%</b></div><progress value={progress} max="100" /></div>}<button className="primary" onClick={upload} disabled={uploading}>{uploading ? 'Zapisywanie…' : 'Wczytaj raport'}</button>{error && <div className="state-card error-state">{error}</div>}{result && <div className="state-card success-state"><strong>Raport zapisany</strong><span>Report ID: <code>{result.reportId}</code></span><span>Status: {result.status}</span></div>}</div></div>
  }

  return <div className="page"><p className="eyebrow">UI-06</p><div className="page-heading"><div><h1>Import security report</h1><p className="muted">Zachowaj oryginalny raport do późniejszej kanonizacji.</p></div></div><div className="upload-card"><label className="field"><span>Plik raportu</span><input type="file" accept=".json,.xml,.zip,application/json,application/xml" onChange={(event) => setFile(event.target.files?.[0])} disabled={uploading} /></label><label className="field"><span>Repository ID <small>(opcjonalnie)</small></span><input value={repositoryId} onChange={(event) => setRepositoryId(event.target.value)} placeholder="UUID repozytorium" disabled={uploading} /></label>{uploading && <div className="upload-progress"><div><span>Przesyłanie pliku</span><b>{progress}%</b></div><progress value={progress} max="100" /></div>}<button className="primary" onClick={upload} disabled={uploading}>{uploading ? 'Zapisywanie…' : 'Wczytaj raport'}</button>{error && <div className="state-card error-state">{error}</div>}{result && <div className="state-card success-state"><strong>Raport zapisany</strong><span>Report ID: <code>{result.reportId}</code></span><span>Status: {result.status}</span></div>}</div></div>
}
