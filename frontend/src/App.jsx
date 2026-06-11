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

/** 상단 내비게이션에서 사용하는 OHARA 로고를 렌더링한다. */
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

/** 그래프 외 페이지에 공통 헤더와 본문 레이아웃을 제공한다. */
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

/** 로그인 이후 페이지 전환과 선택 워크스페이스 상태를 관리한다. */
export default function App({ user, onLogout }) {
  const [activePage, setActivePage] = useState('graph')
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(0)

  /** 워크스페이스 페이지에서 선택한 그래프로 이동한다. */
  function openWorkspaceGraph(workspaceId) {
    setSelectedWorkspaceId(workspaceId)
    setActivePage('graph')
  }

  if (activePage === 'graph') {
    return (
      <GraphPage
        user={user}
        onLogout={onLogout}
        activePage={activePage}
        onNavigate={setActivePage}
        selectedWorkspaceId={selectedWorkspaceId}
        onSelectWorkspace={setSelectedWorkspaceId}
      />
    )
  }

  const pages = {
    dashboard: <DashboardPage onOpenGraph={() => setActivePage('graph')} />,
    workspaces: <WorkspacePage onOpenGraph={openWorkspaceGraph} />,
    sources: <SourcesPage />,
    settings: <SettingsPage />,
  }

  return (
    <PageShell activePage={activePage} onNavigate={setActivePage} user={user} onLogout={onLogout}>
      {pages[activePage] ?? pages.dashboard}
    </PageShell>
  )
}
