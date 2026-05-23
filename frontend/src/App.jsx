import { useCallback, useEffect, useRef, useState } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import { forceCollide } from 'd3-force'
import { api } from './api/client.js'
import SearchBar from './components/SearchBar.jsx'
import ArticlePanel from './components/ArticlePanel.jsx'
import GraphControls from './components/GraphControls.jsx'
import FilterPanel, { filterEdges } from './components/FilterPanel.jsx'
import WorkspacePanel from './components/WorkspacePanel.jsx'


const NODE_COLOR = {
  Country:      '#60a5fa',
  Organization: '#fbbf24',
  Person:       '#c084fc',
}

const getNodeRadius = (degree) => Math.sqrt(degree + 1) * 1.5 + 2.5

export default function App({ user, onLogout }) {
  const fgRef = useRef()

  const [graphData,    setGraphData]    = useState({ nodes: [], links: [] })
  const [filtered,     setFiltered]     = useState({ nodes: [], links: [] })
  const [loading,      setLoading]      = useState(true)
  const [selectedNode, setSelectedNode] = useState(null)
  const [highlight,    setHighlight]    = useState(null)
  const [limit,        setLimit]        = useState(100)
  const [minStrength,  setMinStrength]  = useState(1)
  const [edgeFilter,   setEdgeFilter]   = useState('all')
  const [showFilter,     setShowFilter]     = useState(false)
  const [showWorkspace,  setShowWorkspace]  = useState(false)
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(null)

  // ── 그래프 로드 ────────────────────────────────────────────────
  // 워크스페이스 선택 시 해당 워크스페이스 노드만 쿼리
const loadGraph = useCallback(async () => {
    setLoading(true)
    try {
        let data
        if (selectedWorkspaceId) {
            // 워크스페이스 전용 그래프 (GraphController에 추가 필요)
            data = await api.getWorkspaceGraph(selectedWorkspaceId, limit, minStrength)
        } else {
            // 기존 전역 그래프
            data = await api.getGraph(limit, minStrength)
        }
        const nodes = data.nodes.map(n => ({ id: n.name, name: n.name, type: n.type, degree: n.degree }))
        const links = data.edges.map(e => ({ source: e.source, target: e.target, strength: e.strength }))
        setGraphData({ nodes, links })
    } catch (err) {
        console.error('그래프 로드 실패:', err)
    } finally {
        setLoading(false)
    }
}, [limit, minStrength, selectedWorkspaceId])  // selectedWorkspaceId 의존성 추가
 
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

  // ── 노드 그리기 ────────────────────────────────────────────────
  const paintNode = useCallback((node, ctx, scale) => {
    const r      = getNodeRadius(node.degree ?? 0)
    const color  = NODE_COLOR[node.type] ?? '#9ca3af'
    const isHigh = node.id === highlight

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
  }, [highlight])

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
      return baseWidth
    },
    [highlight]
  )

  const getLinkColor = useCallback(link => {
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
  }, [highlight])

  // ── 렌더 ───────────────────────────────────────────────────────
  return (
    <div className="relative w-screen h-screen bg-gray-950 overflow-hidden">

      <WorkspacePanel
          show={showWorkspace}
          onSelectWorkspace={(wsId) => {
              setSelectedWorkspaceId(wsId)
              setSelectedNode(null)
              setHighlight(null)
          }}
      />

      {/* 상단 툴바 */}
      <div className="absolute top-4 left-4 right-4 flex items-center gap-3 z-30">
        {/* 로고 */}
        <div className="flex items-center gap-1.5 shrink-0">
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

        <SearchBar onSelect={handleSearch}/>

        <GraphControls
          limit={limit} minStrength={minStrength}
          onLimit={setLimit} onMinStrength={setMinStrength}
        />

        {/* 워크스페이스 토글 */}
        <button
          onClick={() => setShowWorkspace(v => !v)}
          className={`shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl border text-xs transition-all ${
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
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-blue-500/15 border border-blue-400/25 text-blue-300 text-xs shrink-0">
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
          className={`shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl border text-xs transition-all ${
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

        {/* 통계 */}
        <span className="text-white/25 text-xs shrink-0 hidden lg:block">
          {filtered.nodes.length}노드 · {filtered.links.length}관계
        </span>

        {/* 유저 / 로그아웃 */}
        <div className="ml-auto flex items-center gap-2 shrink-0">
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
        }}
        onBackgroundClick={() => {
          setSelectedNode(null)
          setHighlight(null)
        }}
        onNodeDrag={node => {
          setHighlight(node.id)
        }}
        onNodeDragEnd={node => {
          setSelectedNode(node.id)
          setHighlight(node.id)
        }}
        cooldownTicks={100}
      />

      {/* 기사 패널 */}
      <ArticlePanel
        selectedNode={selectedNode}
        onClose={() => { setSelectedNode(null); setHighlight(null) }}
      />
    </div>
  )
}
