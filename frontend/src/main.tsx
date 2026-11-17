import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'
import { AppLayout } from './components/layout/AppLayout'
import { WorkspacePage } from './features/workspace/WorkspacePage'
import { RepositoriesPage } from './features/repositories/RepositoriesPage'
import { RepositoryDetailsPage } from './features/repositories/RepositoryDetailsPage'
import { RepositoryRegistrationPage } from './features/repositories/RepositoryRegistrationPage'
import { JobPage } from './features/jobs/JobPage'
import { ProjectDiscoveryPage } from './features/projects/ProjectDiscoveryPage'
import { ProjectGraphPage } from './features/projects/ProjectGraphPage'
import { DashboardPage } from './pages/DashboardPage'
import { ReportImportPage } from './features/reports/ReportImportPage'
import { ReportDetailsPage } from './features/reports/ReportDetailsPage'
import { FindingDetailsPage } from './features/reports/FindingDetailsPage'
import { ReportContextPage } from './features/reports/ReportContextPage'
import { StrategySelectionPage } from './features/reports/StrategySelectionPage'
import { PlansPage } from './features/plans/PlansPage'
import { HistoryPage } from './features/history/HistoryPage'

function App() {
  const [hash, setHash] = useState(window.location.hash)
  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash)
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])
  const repositoryMatch = hash.match(/^#repository\/(.+)$/)
  const jobMatch = hash.match(/^#jobs(?:\/([^?]+)(?:\?return=(.+))?)?$/)
  const projectMatch = hash.match(/^#projects\/(.+)$/)
  const graphMatch = hash.match(/^#graph\/(.+)$/)
  const reportMatch = hash.match(/^#reports(?:\/import)?(?:\?repositoryId=([^&]+))?$/)
  const reportDetailsMatch = hash.match(/^#report\/([^?]+)$/)
  const findingMatch = hash.match(/^#finding\/([^?]+)$/)
  const reportContextMatch = hash.match(/^#report-context\/([^?]+)$/)
  const strategyMatch = hash.match(/^#strategy\/([^?]+)(?:\?(.+))?$/)
  const strategyParams = strategyMatch?.[2] ? new URLSearchParams(strategyMatch[2]) : undefined
  const planMatch = hash.match(/^#plans(?:\/([^?]+))?$/)
  const historyMatch = hash === '#history'
  const panel = hash === '#workspace' ? <WorkspacePage />
    : hash === '#repositories' ? <RepositoriesPage />
      : hash === '#register-repository' ? <RepositoryRegistrationPage />
      : reportDetailsMatch ? <ReportDetailsPage reportId={reportDetailsMatch[1]} />
      : findingMatch ? <FindingDetailsPage findingId={findingMatch[1]} />
      : reportContextMatch ? <ReportContextPage reportId={reportContextMatch[1]} />
      : strategyMatch ? <StrategySelectionPage findingId={strategyMatch[1]} occurrenceId={strategyParams?.get('occurrenceId') ?? undefined} planId={strategyParams?.get('planId') ?? undefined} itemId={strategyParams?.get('itemId') ?? undefined} targetProjectId={strategyParams?.get('targetProjectId') ?? undefined} changePointTreeNodeId={strategyParams?.get('changePointTreeNodeId') ?? undefined} targetArtifactVersionId={strategyParams?.get('targetArtifactVersionId') ?? undefined} existingStrategy={strategyParams?.get('strategy') ?? undefined} />
      : planMatch ? <PlansPage planId={planMatch[1]} />
      : historyMatch ? <HistoryPage />
      : reportMatch ? <ReportImportPage initialRepositoryId={reportMatch[1] ? decodeURIComponent(reportMatch[1]) : undefined} />
      : jobMatch ? <JobPage jobId={jobMatch[1]} returnHash={jobMatch[2] ? decodeURIComponent(jobMatch[2]) : undefined} />
      : projectMatch ? <ProjectDiscoveryPage repositoryId={projectMatch[1]} />
      : graphMatch ? <ProjectGraphPage projectId={graphMatch[1]} />
      : repositoryMatch ? <RepositoryDetailsPage repositoryId={repositoryMatch[1]} />
        : <DashboardPage />
  return <AppLayout>{panel}</AppLayout>
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)
