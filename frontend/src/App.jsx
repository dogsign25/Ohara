import { useState } from 'react'
import GraphPage from './pages/GraphPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import WorkspacePage from './pages/WorkspacePage.jsx'
import SourcesPage from './pages/SourcesPage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'

const PAGES = [
  { key: 'graph', label: '그래프' },
  { key: 'dashboard', label: '대시보드' },
  { key: 'workspaces', label: '워크스페이스' },
  { key: 'sources', label: '소스' },
  { key: 'settings', label: '설정' },
]

function Logo() {
  return (
    <div className="flex items-center gap-2">
      <svg width="20" height="20" viewBox="0 0 22 22" fill="none">
        <circle cx="11" cy="11" r="3" fill="#60a5fa"/>
        <circle cx="4" cy="4" r="2" fill="#c084fc" opacity="0.8"/>
        <circle cx="18" cy="5" r="2" fill="#fbbf24" opacity="0.8"/>
        <circle cx="4" cy="18" r="2" fill="#fbbf24" opacity="0.8"/>
        <circle cx="18" cy="17" r="2" fill="#c084fc" opacity="0.8"/>
        <line x1="11" y1="11" x2="4" y2="4" stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
        <line x1="11" y1="11" x2="18" y2="5" stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
        <line x1="11" y1="11" x2="4" y2="18" stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
        <line x1="11" y1="11" x2="18" y2="17" stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
      </svg>
      <span className="text-white font-bold tracking-widest text-xs uppercase">OHARA</span>
    </div>
  )
}

function PageShell({ activePage, onNavigate, user, onLogout, children }) {
  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <header className="sticky top-0 z-40 border-b border-white/10 bg-gray-950/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-4 px-5 py-3">
          <Logo />
          <nav className="flex flex-1 items-center gap-1 overflow-x-auto">
            {PAGES.map(page => (
              <button
                key={page.key}
                onClick={() => onNavigate(page.key)}
                className={`shrink-0 rounded-lg px-3 py-2 text-xs transition-colors ${
                  activePage === page.key
                    ? 'bg-white/12 text-white'
                    : 'text-white/40 hover:bg-white/5 hover:text-white/70'
                }`}
              >
                {page.label}
              </button>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            {user && <span className="hidden text-xs text-white/35 sm:block">{user}</span>}
            <button
              onClick={onLogout}
              className="rounded-lg px-3 py-2 text-xs text-white/40 transition-colors hover:bg-white/5 hover:text-white/70"
            >
              로그아웃
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-5 py-6">
        {children}
      </main>
    </div>
  )
}

export default function App({ user, onLogout }) {
  const [activePage, setActivePage] = useState('graph')

  if (activePage === 'graph') {
    return (
      <GraphPage
        user={user}
        onLogout={onLogout}
        activePage={activePage}
        onNavigate={setActivePage}
      />
    )
  }

  const pages = {
    dashboard: <DashboardPage onOpenGraph={() => setActivePage('graph')} />,
    workspaces: <WorkspacePage onOpenGraph={() => setActivePage('graph')} />,
    sources: <SourcesPage />,
    settings: <SettingsPage />,
  }

  return (
    <PageShell activePage={activePage} onNavigate={setActivePage} user={user} onLogout={onLogout}>
      {pages[activePage] ?? pages.dashboard}
    </PageShell>
  )
}
