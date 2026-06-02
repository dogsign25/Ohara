import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client.js'

const SOURCES = [
  'BBC',
  'Al Jazeera',
  'The Guardian',
  'Reuters',
  'AP News',
  'NPR',
  'DW',
  'France 24',
]

export default function SourcesPage() {
  const [graph, setGraph] = useState({ nodes: [], edges: [] })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.getGraph(120, 1, 7)
      .then(setGraph)
      .catch(() => setGraph({ nodes: [], edges: [] }))
      .finally(() => setLoading(false))
  }, [])

  const stats = useMemo(() => {
    const totalArticles = graph.edges.reduce((sum, edge) => sum + (edge.articleCount ?? 0), 0)
    const latest = graph.edges.map(edge => edge.lastMentioned).filter(Boolean).sort().at(-1)
    return { totalArticles, latest }
  }, [graph])

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs uppercase tracking-widest text-blue-300/70">Sources</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">뉴스 소스</h1>
        <p className="mt-2 text-sm text-white/40">수집 대상과 최근 그래프 반영 상태를 확인합니다.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
          <p className="text-xs text-white/35">등록 소스</p>
          <p className="mt-2 text-2xl font-semibold">{SOURCES.length}</p>
        </div>
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
          <p className="text-xs text-white/35">최근 7일 기사 참조</p>
          <p className="mt-2 text-2xl font-semibold">{loading ? '-' : stats.totalArticles}</p>
        </div>
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
          <p className="text-xs text-white/35">마지막 반영</p>
          <p className="mt-2 text-2xl font-semibold">
            {stats.latest ? new Date(stats.latest).toLocaleDateString('ko-KR') : '-'}
          </p>
        </div>
      </div>

      <section className="rounded-lg border border-white/10 bg-white/[0.03]">
        <div className="border-b border-white/10 px-4 py-3">
          <h2 className="text-sm font-medium text-white/85">수집 채널</h2>
        </div>
        <div className="grid gap-2 p-4 md:grid-cols-2">
          {SOURCES.map((source, index) => (
            <div key={source} className="flex items-center gap-3 rounded-lg border border-white/8 bg-white/[0.025] p-3">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm text-white/80">{source}</p>
                <p className="mt-1 text-xs text-white/30">활성 · {index % 3 === 0 ? '국제' : index % 3 === 1 ? '정치' : '속보'} 중심</p>
              </div>
              <span className="rounded-full bg-emerald-500/10 px-2 py-1 text-xs text-emerald-300">ON</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
