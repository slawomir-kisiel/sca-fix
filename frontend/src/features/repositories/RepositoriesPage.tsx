import { RepositoryList } from './RepositoryList'

export function RepositoriesPage() {
  return <div className="page">
    <div className="page-heading"><div><p className="eyebrow">UI-03 / UI-04</p><h1>Repositories</h1><p className="muted">Zarejestrowane checkouty i repozytoria Git.</p></div><a className="button primary" href="#register-repository">Zarejestruj repozytorium</a></div>
    <RepositoryList />
  </div>
}
