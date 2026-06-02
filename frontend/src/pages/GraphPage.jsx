import { useCallback, useEffect, useRef, useState } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import { forceCollide } from 'd3-force'
import { api } from '../api/client.js'
import SearchBar from '../components/SearchBar.jsx'
import ArticlePanel from '../components/ArticlePanel.jsx'
import GraphControls from '../components/GraphControls.jsx'
import FilterPanel, { filterEdges } from '../components/FilterPanel.jsx'
import WorkspacePanel from '../components/WorkspacePanel.jsx'


const NODE_COLOR = {
  Country:      '#60a5fa',
  Organization: '#fbbf24',
  Person:       '#c084fc',
}

const getNodeRadius = (degree) => Math.sqrt(degree + 1) * 1.5 + 2.5

const edgeKey = (link) => {
  const sourceId = typeof link.source === 'object' ? link.source.id : link.source
  const targetId = typeof link.target === 'object' ? link.target.id : link.target
  return [sourceId, targetId].sort().join('::')
}

export default function GraphPage({ user, onLogout, activePage = 'graph', onNavigate }) {
  const fgRef = useRef()

  const [graphData,    setGraphData]    = useState({ nodes: [], links: [] })
  const [filtered,     setFiltered]     = useState({ nodes: [], links: [] })
  const [loading,      setLoading]      = useState(true)
  const [selectedNode, setSelectedNode] = useState(null)
  const [highlight,    setHighlight]    = useState(null)
  const [limit,        setLimit]        = useState(100)
  const [minStrength,  setMinStrength]  = useState(1)
  const [edgeFilter,   setEdgeFilter]   = useState('all')
  const [days,         setDays]         = useState('')
  const [showFilter,     setShowFilter]     = useState(false)
  const [showWorkspace,  setShowWorkspace]  = useState(false)
  const [showTools,      setShowTools]      = useState(false)
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(0)
  const [selectedEdge, setSelectedEdge] = useState(null)
  const [edgeSources,  setEdgeSources]  = useState([])
  const [pathNodes,    setPathNodes]    = useState(new Set())
  const [pathEdges,    setPathEdges]    = useState(new Set())
  const [pathFrom,     setPathFrom]     = useState('')
  const [pathTo,       setPathTo]       = useState('')
  const [snapshots,    setSnapshots]    = useState(() => {
    try { return JSON.parse(localStorage.getItem('ohara:snapshots') || '[]') }
    catch { return [] }
  })

  // ── 그래프 로드 ────────────────────────────────────────────────
  // 워크스페이스 선택 시 해당 워크스페이스 노드만 쿼리
const loadGraph = useCallback(async () => {
    setLoading(true)
    try {
        let data
        if (selectedWorkspaceId !== null) {
            // 워크스페이스 전용 그래프 (GraphController에 추가 필요)
            data = await api.getWorkspaceGraph(selectedWorkspaceId, limit, minStrength, days || undefined)
        } else {
            // 기존 전역 그래프
            data = await api.getGraph(limit, minStrength, days || undefined)
        }
        const nodes = data.nodes.map(n => ({ id: n.name, name: n.name, type: n.type, degree: n.degree }))
        const links = data.edges.map(e => ({
          source: e.source,
          target: e.target,
          strength: e.strength,
          articleCount: e.articleCount,
          lastMentioned: e.lastMentioned,
        }))
        setGraphData({ nodes, links })
    } catch (err) {
        console.error('그래프 로드 실패:', err)
    } finally {
        setLoading(false)
    }
}, [limit, minStrength, selectedWorkspaceId, days])  // selectedWorkspaceId 의존성 추가
 
useEffect(() => { loadGraph() }, [loadGraph])

  // ── 엣지 필터 적용 ──────────────────────────────────────────────
  useEffect(() => {
    const links = filterEdges(graphData.links, graphData.nodes, edgeFilter)
    setFiltered({ nodes: graphData.nodes, links })
  }, [graphData, edgeFilter])

  // ── D3 시뮬레이션 힘(Force) 조절 ──────────────────────────────────
  useEffect(() => {
    const fg = fgRef.current
    if (fg) {
      // 겹침 방지 (Collision) 적용 - 노드 반지름에 여백 추가
      fg.d3Force('collide', forceCollide(node => getNodeRadius(node.degree ?? 0) + 6))
      // 척력 (Repulsion) 강화하여 서로 더 멀어지게 설정
      fg.d3Force('charge').strength(-150)
    }
  }, [filtered])

  // ── 검색 → 노드 포커스 ─────────────────────────────────────────
  function handleSearch(node) {
    setSelectedNode(node.name)
    setHighlight(node.name)
    const found = graphData.nodes.find(n => n.id === node.name)
    if (found?.x != null) {
      fgRef.current?.centerAt(found.x, found.y, 800)
      fgRef.current?.zoom(3, 800)
    }
  }

  function handleNodeDeleted(name) {
    setGraphData(prev => ({
      nodes: prev.nodes.filter(n => n.id !== name),
      links: prev.links.filter(link => {
        const sourceId = typeof link.source === 'object' ? link.source.id : link.source
        const targetId = typeof link.target === 'object' ? link.target.id : link.target
        return sourceId !== name && targetId !== name
      })
    }))
    setSelectedNode(null)
    setHighlight(null)
  }

  async function handleLinkClick(link) {
    const sourceId = typeof link.source === 'object' ? link.source.id : link.source
    const targetId = typeof link.target === 'object' ? link.target.id : link.target
    setSelectedEdge({ source: sourceId, target: targetId, strength: link.strength })
    setSelectedNode(null)
    setHighlight(null)
    setEdgeSources([])
    try {
      const sources = await api.getEdgeSources(sourceId, targetId, selectedWorkspaceId || undefined)
      setEdgeSources(sources)
    } catch {
      setEdgeSources([])
    }
  }

  async function handleFindPath(e) {
    e.preventDefault()
    if (!pathFrom.trim() || !pathTo.trim()) return
    try {
      const result = await api.findPath(pathFrom.trim(), pathTo.trim(), 5, selectedWorkspaceId || undefined)
      setPathNodes(new Set(result.nodes.map(n => n.name)))
      setPathEdges(new Set(result.edges.map(edgeKey)))
      const first = graphData.nodes.find(n => n.id === pathFrom.trim())
      if (first?.x != null) {
        fgRef.current?.centerAt(first.x, first.y, 800)
        fgRef.current?.zoom(2.2, 800)
      }
    } catch {
      setPathNodes(new Set())
      setPathEdges(new Set())
      alert('연결 경로를 찾지 못했습니다.')
    }
  }

  function saveSnapshot() {
    const title = prompt('스냅샷 이름을 입력하세요', `Snapshot ${snapshots.length + 1}`)
    if (!title) return
    const shot = {
      id: Date.now(),
      title,
      createdAt: new Date().toISOString(),
      limit,
      minStrength,
      edgeFilter,
      days,
      selectedWorkspaceId,
      selectedNode,
      highlight,
    }
    const next = [shot, ...snapshots].slice(0, 12)
    setSnapshots(next)
    localStorage.setItem('ohara:snapshots', JSON.stringify(next))
  }

  function restoreSnapshot(shot) {
    setLimit(shot.limit)
    setMinStrength(shot.minStrength)
    setEdgeFilter(shot.edgeFilter)
    setDays(shot.days || '')
    setSelectedWorkspaceId(shot.selectedWorkspaceId)
    setSelectedNode(shot.selectedNode)
    setHighlight(shot.highlight)
  }

  // ── 노드 그리기 ────────────────────────────────────────────────
  const paintNode = useCallback((node, ctx, scale) => {
    const r      = getNodeRadius(node.degree ?? 0)
    const color  = NODE_COLOR[node.type] ?? '#9ca3af'
    const isHigh = node.id === highlight || pathNodes.has(node.id)

    if (isHigh) {
      ctx.beginPath()
      ctx.arc(node.x, node.y, r + 5, 0, 2 * Math.PI)
      ctx.fillStyle = `${color}22`
      ctx.fill()
      ctx.beginPath()
      ctx.arc(node.x, node.y, r + 2, 0, 2 * Math.PI)
      ctx.fillStyle = `${color}44`
      ctx.fill()
    }

    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, 2 * Math.PI)
    ctx.fillStyle = isHigh ? color : `${color}bb`
    ctx.fill()

    if (scale > 0.8) {
      const label = node.name.length > 16 ? node.name.slice(0, 14) + '…' : node.name
      ctx.font = `${Math.max(10 / scale, 8)}px sans-serif`
      ctx.fillStyle = 'rgba(255,255,255,0.80)'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(label, node.x, node.y + r + 9 / scale)
    }
  }, [highlight, pathNodes])

  const nodePointer = useCallback((node, color, ctx) => {
    const r = getNodeRadius(node.degree ?? 0)
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, 2 * Math.PI)
    ctx.fill()
  }, [])

  const linkWidth = useCallback(
    link => {
      const sourceId = typeof link.source === 'object' ? link.source.id : link.source
      const targetId = typeof link.target === 'object' ? link.target.id : link.target
      const baseWidth = Math.min(Math.log2(link.strength + 1) * 1.5, 6)
      if (highlight && (sourceId === highlight || targetId === highlight)) {
        return baseWidth + 2.0
      }
      if (pathEdges.has(edgeKey(link))) return baseWidth + 2.5
      return baseWidth
    },
    [highlight, pathEdges]
  )

  const getLinkColor = useCallback(link => {
    if (pathEdges.has(edgeKey(link))) return 'rgba(52,211,153,0.9)'
    if (!highlight) return 'rgba(255,255,255,0.08)'

    const sourceObj = typeof link.source === 'object' ? link.source : null
    const targetObj = typeof link.target === 'object' ? link.target : null

    const sourceId = sourceObj ? sourceObj.id : link.source
    const targetId = targetObj ? targetObj.id : link.target

    if (sourceId === highlight) {
      const color = NODE_COLOR[sourceObj?.type] ?? '#9ca3af'
      return `${color}aa`
    }
    if (targetId === highlight) {
      const color = NODE_COLOR[targetObj?.type] ?? '#9ca3af'
      return `${color}aa`
    }

    return 'rgba(255,255,255,0.02)'
  }, [highlight, pathEdges])

  // ── 렌더 ───────────────────────────────────────────────────────
  return (
    <div className="relative w-screen h-screen bg-gray-950 overflow-hidden">

      <WorkspacePanel
          show={showWorkspace}
          onClose={() => setShowWorkspace(false)}
          onSelectWorkspace={(wsId) => {
              setSelectedWorkspaceId(wsId)
              setSelectedNode(null)
              setHighlight(null)
          }}
      />

      {/* 상단 툴바 */}
      <div className="absolute top-4 left-4 right-4 flex items-center gap-3 z-30 pointer-events-none">
        {/* 로고 */}
        <div className="flex items-center gap-1.5 shrink-0 pointer-events-auto">
          <svg width="18" height="18" viewBox="0 0 22 22" fill="none">
            <circle cx="11" cy="11" r="3" fill="#60a5fa"/>
            <circle cx="4"  cy="4"  r="2" fill="#c084fc" opacity="0.8"/>
            <circle cx="18" cy="5"  r="2" fill="#fbbf24" opacity="0.8"/>
            <circle cx="4"  cy="18" r="2" fill="#fbbf24" opacity="0.8"/>
            <circle cx="18" cy="17" r="2" fill="#c084fc" opacity="0.8"/>
            <line x1="11" y1="11" x2="4"  y2="4"  stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
            <line x1="11" y1="11" x2="18" y2="5"  stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
            <line x1="11" y1="11" x2="4"  y2="18" stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
            <line x1="11" y1="11" x2="18" y2="17" stroke="rgba(255,255,255,0.25)" strokeWidth="0.8"/>
          </svg>
          <span className="text-white font-bold tracking-widest text-xs uppercase">OHARA</span>
        </div>

        {onNavigate && (
          <div className="hidden xl:flex items-center gap-1 pointer-events-auto rounded-xl border border-white/10 bg-white/5 p-1">
            {[
              ['graph', '그래프'],
              ['dashboard', '대시보드'],
              ['workspaces', '워크스페이스'],
              ['sources', '소스'],
              ['settings', '설정'],
            ].map(([key, label]) => (
              <button
                key={key}
                onClick={() => onNavigate(key)}
                className={`px-2.5 py-1.5 rounded-lg text-xs transition-colors ${
                  activePage === key
                    ? 'bg-white/12 text-white'
                    : 'text-white/40 hover:text-white/70 hover:bg-white/5'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        )}

        <div className="pointer-events-auto">
          <SearchBar onSelect={handleSearch}/>
        </div>

        <div className="pointer-events-auto">
          <GraphControls
            limit={limit} minStrength={minStrength}
            onLimit={setLimit} onMinStrength={setMinStrength}
          />
        </div>

        <select
          value={days}
          onChange={e => setDays(e.target.value)}
          className="pointer-events-auto bg-white/5 border border-white/10 text-white/60 text-xs rounded-xl px-2 py-2 outline-none hover:bg-white/10"
          title="시간 필터"
        >
          <option value="">전체 기간</option>
          <option value="1">최근 1일</option>
          <option value="7">최근 7일</option>
          <option value="30">최근 30일</option>
          <option value="90">최근 90일</option>
        </select>

        {/* 워크스페이스 토글 */}
        <button
          onClick={() => setShowWorkspace(v => !v)}
          className={`shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl border text-xs transition-all pointer-events-auto ${
            showWorkspace
              ? 'bg-white/15 border-white/25 text-white'
              : 'bg-white/5 border-white/10 text-white/50 hover:text-white hover:bg-white/10'
          }`}
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
          </svg>
          워크스페이스
        </button>

        {selectedWorkspaceId && (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-blue-500/15 border border-blue-400/25 text-blue-300 text-xs shrink-0 pointer-events-auto">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-400" />
            워크스페이스 그래프
            <button
              onClick={() => setSelectedWorkspaceId(null)}
              className="text-blue-400/60 hover:text-blue-300 ml-1"
            >
              ✕
            </button>
          </div>
        )}

        {/* 필터 토글 */}
        <button
          onClick={() => setShowFilter(v => !v)}
          className={`shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl border text-xs transition-all pointer-events-auto ${
            showFilter
              ? 'bg-white/15 border-white/25 text-white'
              : 'bg-white/5 border-white/10 text-white/50 hover:text-white hover:bg-white/10'
          }`}
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2a1 1 0 01-.293.707L13 13.414V19a1 1 0 01-.553.894l-4 2A1 1 0 017 21v-7.586L3.293 6.707A1 1 0 013 6V4z"/>
          </svg>
          필터
        </button>

        <button
          onClick={() => setShowTools(v => !v)}
          className={`shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl border text-xs transition-all pointer-events-auto ${
            showTools
              ? 'bg-white/15 border-white/25 text-white'
              : 'bg-white/5 border-white/10 text-white/50 hover:text-white hover:bg-white/10'
          }`}
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.5 4.5L21 12l-7.5 7.5M21 12H3"/>
          </svg>
          탐색
        </button>

        {/* 통계 */}
        <span className="text-white/25 text-xs shrink-0 hidden lg:block">
          {filtered.nodes.length}노드 · {filtered.links.length}관계
        </span>

        {/* 유저 / 로그아웃 */}
        <div className="ml-auto flex items-center gap-2 shrink-0 pointer-events-auto">
          {user && (
            <span className="text-white/30 text-xs hidden sm:block">{user}</span>
          )}
          <button
            onClick={onLogout}
            className="flex items-center gap-1 text-white/30 hover:text-white/60 text-xs px-2 py-1.5 rounded-lg hover:bg-white/5 transition-all"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
            </svg>
            로그아웃
          </button>
        </div>
      </div>

      {/* 필터 패널 (드롭다운) */}
      {showFilter && (
        <div className="absolute top-16 left-4 z-30">
          <FilterPanel active={edgeFilter} onChange={setEdgeFilter} />
        </div>
      )}

      {showTools && (
        <div className="absolute top-16 left-4 z-30 w-80 rounded-2xl bg-gray-900/95 backdrop-blur border border-white/10 shadow-2xl overflow-hidden">
          <form onSubmit={handleFindPath} className="p-4 border-b border-white/10">
            <p className="text-white/50 text-xs mb-2">경로 찾기</p>
            <div className="grid grid-cols-[1fr_auto_1fr] gap-2 items-center">
              <input
                value={pathFrom}
                onChange={e => setPathFrom(e.target.value)}
                placeholder="출발 노드"
                className="min-w-0 bg-white/5 border border-white/10 rounded-lg px-2 py-1.5 text-white/80 text-xs outline-none placeholder-white/25"
              />
              <span className="text-white/20 text-xs">→</span>
              <input
                value={pathTo}
                onChange={e => setPathTo(e.target.value)}
                placeholder="도착 노드"
                className="min-w-0 bg-white/5 border border-white/10 rounded-lg px-2 py-1.5 text-white/80 text-xs outline-none placeholder-white/25"
              />
            </div>
            <div className="flex justify-between mt-2">
              <button
                type="button"
                onClick={() => { setPathNodes(new Set()); setPathEdges(new Set()) }}
                className="text-white/35 hover:text-white/60 text-xs"
              >
                초기화
              </button>
              <button
                type="submit"
                className="px-3 py-1.5 rounded-lg bg-emerald-500/15 border border-emerald-400/25 text-emerald-300 text-xs hover:bg-emerald-500/25"
              >
                찾기
              </button>
            </div>
          </form>

          <div className="p-4">
            <div className="flex items-center justify-between mb-2">
              <p className="text-white/50 text-xs">스냅샷</p>
              <button
                onClick={saveSnapshot}
                className="px-2.5 py-1 rounded-lg bg-blue-500/15 border border-blue-400/25 text-blue-300 text-xs hover:bg-blue-500/25"
              >
                저장
              </button>
            </div>
            {snapshots.length === 0 ? (
              <p className="text-white/25 text-xs">저장된 스냅샷 없음</p>
            ) : (
              <div className="space-y-1.5 max-h-44 overflow-y-auto">
                {snapshots.map(shot => (
                  <button
                    key={shot.id}
                    onClick={() => restoreSnapshot(shot)}
                    className="w-full text-left px-3 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/5"
                  >
                    <p className="text-white/70 text-xs truncate">{shot.title}</p>
                    <p className="text-white/25 text-xs mt-0.5">
                      {new Date(shot.createdAt).toLocaleDateString('ko-KR')}
                    </p>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 범례 */}
      <div className="absolute bottom-4 left-4 flex gap-4 z-30">
        {Object.entries(NODE_COLOR).map(([type, color]) => (
          <div key={type} className="flex items-center gap-1.5">
            <div className="w-2 h-2 rounded-full" style={{ backgroundColor: color }}/>
            <span className="text-white/35 text-xs">{type}</span>
          </div>
        ))}
      </div>

      {/* 로딩 */}
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center z-20 bg-gray-950/60">
          <div className="flex items-center gap-3">
            <div className="w-4 h-4 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
            <span className="text-white/40 text-sm">그래프 로딩 중...</span>
          </div>
        </div>
      )}

      {/* 그래프 */}
      <ForceGraph2D
        ref={fgRef}
        graphData={filtered}
        backgroundColor="#030712"
        nodeCanvasObject={paintNode}
        nodePointerAreaPaint={nodePointer}
        linkWidth={linkWidth}
        linkColor={getLinkColor}
        onNodeClick={node => {
          setSelectedNode(node.id)
          setHighlight(node.id)
          setSelectedEdge(null)
        }}
        onBackgroundClick={() => {
          setSelectedNode(null)
          setHighlight(null)
          setSelectedEdge(null)
        }}
        onLinkClick={handleLinkClick}
        onNodeDrag={node => {
          setHighlight(node.id)
        }}
        onNodeDragEnd={node => {
          setSelectedNode(node.id)
          setHighlight(node.id)
        }}
        cooldownTicks={100}
      />

      {selectedEdge && (
        <div className="absolute right-4 top-20 w-80 bg-gray-900/90 backdrop-blur border border-white/10 rounded-2xl overflow-hidden shadow-2xl z-40">
          <div className="flex items-start justify-between p-4 border-b border-white/10">
            <div className="min-w-0">
              <p className="text-white/40 text-xs">관계 출처</p>
              <h2 className="text-white text-sm font-medium truncate mt-1">
                {selectedEdge.source} ↔ {selectedEdge.target}
              </h2>
              <p className="text-white/35 text-xs mt-1">강도 {selectedEdge.strength}</p>
            </div>
            <button
              onClick={() => setSelectedEdge(null)}
              className="text-white/40 hover:text-white transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <div className="max-h-80 overflow-y-auto">
            {edgeSources.length ? (
              edgeSources.map((item, i) => (
                <a
                  key={i}
                  href={item.url && !item.url.startsWith('workspace://') ? item.url : undefined}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block px-4 py-3 border-b border-white/5 hover:bg-white/5"
                >
                  <p className="text-white/85 text-sm leading-snug line-clamp-2">{item.title || item.url}</p>
                  <p className="text-white/35 text-xs mt-1">
                    {item.kind} · {item.source}
                  </p>
                </a>
              ))
            ) : (
              <p className="text-white/30 text-sm text-center py-8">출처를 불러오는 중...</p>
            )}
          </div>
        </div>
      )}

      {/* 기사 패널 */}
      <ArticlePanel
        selectedNode={selectedNode}
        onClose={() => { setSelectedNode(null); setHighlight(null) }}
        onDelete={handleNodeDeleted}
        onUpdated={loadGraph}
      />
    </div>
  )
}
