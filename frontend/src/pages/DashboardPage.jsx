import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client.js'

const TYPE_LABEL = {
  Country: '국가',
  Organization: '기관',
  Person: '인물',
}

const TYPE_COLOR = {
  Country: 'bg-blue-400',
  Organization: 'bg-amber-400',
  Person: 'bg-purple-400',
}

/** 대시보드의 단일 통계 값을 카드 형태로 표시한다. */
function StatCard({ label, value, sub }) {
  return (
    <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
      <p className="text-xs text-white/35">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-white">{value}</p>
      {sub && <p className="mt-1 text-xs text-white/30">{sub}</p>}
    </div>
  )
}

/** 데이터가 없을 때 공통 안내 영역을 표시한다. */
function EmptyState({ children }) {
  return (
    <div className="rounded-lg border border-white/10 bg-white/[0.03] p-8 text-center text-sm text-white/35">
      {children}
    </div>
  )
}

/** 최근 그래프 데이터를 요약해 주요 통계와 엔티티를 보여준다. */
export default function DashboardPage({ onOpenGraph }) {
  const [data, setData] = useState({ nodes: [], edges: [] })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    setLoading(true)
    api.getGraph(150, 1, 30)
      .then(setData)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  const summary = useMemo(() => {
    const typeCounts = data.nodes.reduce((acc, node) => {
      acc[node.type] = (acc[node.type] ?? 0) + 1
      return acc
    }, {})
    const topNodes = [...data.nodes].sort((a, b) => (b.degree ?? 0) - (a.degree ?? 0)).slice(0, 8)
    const topEdges = [...data.edges].sort((a, b) => (b.strength ?? 0) - (a.strength ?? 0)).slice(0, 8)
    const latestMention = data.edges
      .map(edge => edge.lastMentioned)
      .filter(Boolean)
      .sort()
      .at(-1)

    return { typeCounts, topNodes, topEdges, latestMention }
  }, [data])

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs uppercase tracking-widest text-blue-300/70">Overview</p>
          <h1 className="mt-2 text-2xl font-semibold tracking-tight">인텔리전스 대시보드</h1>
          <p className="mt-2 text-sm text-white/40">최근 30일 그래프에서 중요한 엔티티와 관계를 요약합니다.</p>
        </div>
        <button
          onClick={onOpenGraph}
          className="w-fit rounded-lg border border-blue-400/25 bg-blue-500/15 px-4 py-2 text-sm text-blue-200 transition-colors hover:bg-blue-500/25"
        >
          그래프로 보기
        </button>
      </div>

      {loading ? (
        <EmptyState>대시보드를 불러오는 중...</EmptyState>
      ) : error ? (
        <EmptyState>{error}</EmptyState>
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="노드" value={data.nodes.length} sub="최근 30일 기준" />
            <StatCard label="관계" value={data.edges.length} sub="강도 1 이상" />
            <StatCard label="최신 언급" value={summary.latestMention ? new Date(summary.latestMention).toLocaleDateString('ko-KR') : '-'} />
            <StatCard label="최상위 노드" value={summary.topNodes[0]?.name ?? '-'} sub={summary.topNodes[0] ? `${summary.topNodes[0].degree}개 연결` : ''} />
          </div>

          <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
            <section className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <h2 className="text-sm font-medium text-white/85">엔티티 유형</h2>
              <div className="mt-4 space-y-3">
                {Object.entries(TYPE_LABEL).map(([type, label]) => {
                  const count = summary.typeCounts[type] ?? 0
                  const pct = data.nodes.length ? Math.round((count / data.nodes.length) * 100) : 0
                  return (
                    <div key={type}>
                      <div className="mb-1 flex items-center justify-between text-xs">
                        <span className="text-white/55">{label}</span>
                        <span className="text-white/35">{count}개</span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-white/8">
                        <div className={`h-full ${TYPE_COLOR[type]}`} style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  )
                })}
              </div>
            </section>

            <section className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <h2 className="text-sm font-medium text-white/85">핵심 엔티티</h2>
              <div className="mt-3 divide-y divide-white/5">
                {summary.topNodes.map(node => (
                  <div key={node.name} className="flex items-center justify-between py-2.5">
                    <div className="min-w-0">
                      <p className="truncate text-sm text-white/80">{node.name}</p>
                      <p className="text-xs text-white/30">{TYPE_LABEL[node.type] ?? node.type}</p>
                    </div>
                    <span className="rounded-full bg-white/8 px-2 py-1 text-xs text-white/45">{node.degree} 연결</span>
                  </div>
                ))}
              </div>
            </section>
          </div>

          <section className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
            <h2 className="text-sm font-medium text-white/85">강한 관계</h2>
            <div className="mt-3 grid gap-2 md:grid-cols-2">
              {summary.topEdges.map(edge => (
                <div key={`${edge.source}-${edge.target}`} className="rounded-lg border border-white/8 bg-white/[0.025] px-3 py-2">
                  <p className="truncate text-sm text-white/75">{edge.source} ↔ {edge.target}</p>
                  <p className="mt-1 text-xs text-white/30">강도 {edge.strength} · 기사 {edge.articleCount ?? 0}개</p>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
