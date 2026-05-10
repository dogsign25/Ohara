import { useCallback, useEffect, useRef, useState } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import { api } from './api/client.js'
import SearchBar from './components/SearchBar.jsx'
import ArticlePanel from './components/ArticlePanel.jsx'
import GraphControls from './components/GraphControls.jsx'

const NODE_COLOR = {
  Country:      '#60a5fa',  // blue
  Organization: '#fbbf24',  // amber
  Person:       '#c084fc',  // purple
}

export default function App() {
  const fgRef = useRef()

  const [graphData,    setGraphData]    = useState({ nodes: [], links: [] })
  const [loading,      setLoading]      = useState(true)
  const [selectedNode, setSelectedNode] = useState(null)
  const [highlight,    setHighlight]    = useState(null)
  const [limit,        setLimit]        = useState(100)
  const [minStrength,  setMinStrength]  = useState(1)

  // ── 그래프 로드 ────────────────────────────────────────────────
  const loadGraph = useCallback(async () => {
    setLoading(true)
    try {
      const data = await api.getGraph(limit, minStrength)
      setGraphData({
        nodes: data.nodes.map(n => ({ id: n.name, name: n.name, type: n.type, degree: n.degree })),
        links: data.edges.map(e => ({ source: e.source, target: e.target, strength: e.strength })),
      })
    } catch (err) {
      console.error('그래프 로드 실패:', err)
    } finally {
      setLoading(false)
    }
  }, [limit, minStrength])

  useEffect(() => { loadGraph() }, [loadGraph])

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
    const r      = Math.sqrt(node.degree + 1) * 4 + 2
    const color  = NODE_COLOR[node.type] ?? '#9ca3af'
    const isHigh = node.id === highlight

    if (isHigh) {
      ctx.beginPath()
      ctx.arc(node.x, node.y, r + 4, 0, 2 * Math.PI)
      ctx.fillStyle = `${color}33`
      ctx.fill()
    }

    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, 2 * Math.PI)
    ctx.fillStyle = isHigh ? color : `${color}cc`
    ctx.fill()

    if (scale > 0.8) {
      const label = node.name.length > 16 ? node.name.slice(0, 14) + '…' : node.name
      ctx.font = `${Math.max(10 / scale, 8)}px sans-serif`
      ctx.fillStyle = 'rgba(255,255,255,0.85)'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(label, node.x, node.y + r + 8 / scale)
    }
  }, [highlight])

  const nodePointer = useCallback((node, color, ctx) => {
    const r = Math.sqrt(node.degree + 1) * 4 + 2
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, 2 * Math.PI)
    ctx.fill()
  }, [])

  const linkWidth = useCallback(
    link => Math.min(Math.log2(link.strength + 1) * 1.5, 6),
    []
  )

  // ── 렌더 ───────────────────────────────────────────────────────
  return (
    <div className="relative w-screen h-screen bg-gray-950 overflow-hidden">

      {/* 상단 툴바 */}
      <div className="absolute top-4 left-4 right-4 flex items-center gap-3 z-30">
        <span className="text-white font-semibold text-lg tracking-tight shrink-0">OHARA</span>
        <SearchBar onSelect={handleSearch}/>
        <GraphControls
          limit={limit} minStrength={minStrength}
          onLimit={setLimit} onMinStrength={setMinStrength}
        />
        <span className="ml-auto text-white/30 text-xs shrink-0">
          {graphData.nodes.length}노드 · {graphData.links.length}관계
        </span>
      </div>

      {/* 범례 */}
      <div className="absolute bottom-4 left-4 flex gap-4 z-30">
        {Object.entries(NODE_COLOR).map(([type, color]) => (
          <div key={type} className="flex items-center gap-1.5">
            <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: color }}/>
            <span className="text-white/50 text-xs">{type}</span>
          </div>
        ))}
      </div>

      {/* 로딩 */}
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center z-20 bg-gray-950/60">
          <span className="text-white/50 text-sm">로딩 중...</span>
        </div>
      )}

      {/* 그래프 */}
      <ForceGraph2D
        ref={fgRef}
        graphData={graphData}
        backgroundColor="#030712"
        nodeCanvasObject={paintNode}
        nodePointerAreaPaint={nodePointer}
        linkWidth={linkWidth}
        linkColor={() => 'rgba(255,255,255,0.10)'}
        onNodeClick={node => {
          setSelectedNode(node.id)
          setHighlight(node.id)
        }}
        onBackgroundClick={() => {
          setSelectedNode(null)
          setHighlight(null)
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
