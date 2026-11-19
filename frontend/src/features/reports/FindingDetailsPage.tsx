import { useEffect, useState } from 'react'
import { FindingDetails, getFinding } from './api'

function roundedCvss(value?: number) {
  return value == null ? '—' : value.toFixed(1)
}

function sourceLabel(source?: string) {
  if (source === 'OWASP_DEPENDENCY_CHECK') return 'OWASP'
  if (source?.toUpperCase().includes('CHECKMARX')) return 'CHECKMARX'
  return source ?? '—'
}

export function FindingDetailsPage({ findingId }: { findingId: string }) {
  const [finding, setFinding] = useState<FindingDetails>()
  const [error, setError] = useState(false)

  useEffect(() => {
    setFinding(undefined); setError(false)
    getFinding(findingId).then(setFinding).catch(() => setError(true))
  }, [findingId])

  if (error) return <div className="page"><div className="state-card error-state">Nie udało się pobrać findingu.</div></div>
  if (!finding) return <div className="page"><div className="state-card">Ładowanie findingu…</div></div>

  return <div className="page finding-page">
    <a className="back-link" href="#reports">← Reports</a>
    <p className="eyebrow">UI-10</p>
    <div className="page-heading"><div><h1>{finding.externalId ?? 'Finding'}</h1><p className="muted">{finding.title ?? finding.description ?? 'Brak opisu'}</p></div><span className="status-pill">{finding.correlationStatus}</span></div>
    <div className="detail-grid"><article><span className="label">Severity</span><strong>{finding.severity ?? '—'}</strong></article><article><span className="label">CVSS</span><strong>{roundedCvss(finding.cvssScore)}</strong><span className="muted">{finding.cvssVector ?? ''}</span></article><article><span className="label">Source</span><strong>{sourceLabel(finding.source)}</strong></article></div>
    <div className="state-card finding-summary"><strong>Description</strong><span>{finding.description ?? 'Brak opisu.'}</span><strong>Recommendation</strong><span>{finding.recommendation ?? 'Brak rekomendacji.'}</span><strong>Affected artifacts</strong>{finding.affectedArtifacts.map(artifact => <code key={artifact.artifactId}>{artifact.groupId && artifact.artifactIdValue ? `${artifact.groupId}:${artifact.artifactIdValue}` : artifact.purl ?? artifact.filePath ?? artifact.fileName ?? 'Unknown artifact'} · {artifact.reportedVersion ?? '—'}</code>)}</div>
    <section className="state-card finding-next-step"><strong>Occurrence context i wybór zmiany</strong><span className="muted">Wybór occurrence, miejsca zmiany, strategii i wersji docelowej znajduje się teraz na jednej formatce UI-12.</span><a className="button primary" href={`#strategy/${findingId}`}>Przejdź do wyboru occurrence i punktu zmiany →</a></section>
  </div>
}
