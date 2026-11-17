import { useEffect, useState } from 'react'
import { getJob, getJobOutput, JobOutputLine, JobResponse } from '../../api/client'

function outputLine(line: JobOutputLine) {
  if (line.stream === 'COMMAND') return <span className="terminal-command">{line.content}{'\n'}</span>
  return <span className={line.stream === 'STDERR' ? 'terminal-error' : ''}>[{line.stream}] {line.content}{'\n'}</span>
}

export function JobPage({ jobId, returnHash }: { jobId?: string; returnHash?: string }) {
  const [job, setJob] = useState<JobResponse>()
  const [error, setError] = useState(false)
  const [lines, setLines] = useState<JobOutputLine[]>([])

  useEffect(() => {
    if (!jobId) return
    let lastSequence = 0
    let source: EventSource | undefined
    let cancelled = false
    const openStream = () => {
      if (cancelled) return
      source = new EventSource(`/api/jobs/${jobId}/output/stream?afterSequence=${lastSequence}`)
      source.addEventListener('job-output', event => {
        const line = JSON.parse((event as MessageEvent).data) as JobOutputLine
        lastSequence = line.sequenceNo
        setLines(current => current.some(item => item.sequenceNo === line.sequenceNo) ? current : [...current, line])
      })
      source.addEventListener('job-complete', event => { setJob(JSON.parse((event as MessageEvent).data) as JobResponse); source?.close() })
      source.onerror = () => { source?.close(); if (!cancelled) window.setTimeout(openStream, 1000) }
    }
    getJob(jobId).then(loaded => {
      if (cancelled) return
      setJob(loaded)
      return getJobOutput(jobId).then(initial => {
        if (cancelled) return
        setLines(initial)
        lastSequence = initial.at(-1)?.sequenceNo ?? 0
        openStream()
      })
    }).catch(() => setError(true))
    return () => { cancelled = true; source?.close() }
  }, [jobId])

  const backHref = returnHash ? (returnHash.startsWith('#') ? returnHash : `#${returnHash}`) : '#repositories'
  return <div className="page"><a className="back-link" href={backHref}>← {returnHash ? 'Powrót' : 'Repositories'}</a><p className="eyebrow">UI-15</p><div className="page-heading"><div><h1>Job console</h1><p className="mono muted">{jobId}</p></div>{job && <span className={`status-pill status-${job.status.toLowerCase()}`}>{job.status}</span>}</div>{error && <div className="state-card error-state">Nie udało się pobrać joba lub jego outputu.</div>}{!job && !error && <div className="state-card">Ładowanie joba…</div>}{job && <div className="terminal"><div className="terminal-bar">{job.jobType} · {job.stage ?? '—'}</div><pre>{lines.length ? lines.map(line => <span key={line.sequenceNo}>{outputLine(line)}</span>) : 'Brak outputu.'}</pre></div>}</div>
}
