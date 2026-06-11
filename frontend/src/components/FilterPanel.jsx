// FilterPanel.jsx
// 어떤 타입 간의 엣지를 보여줄지 선택

const FILTERS = [
  { key: 'all',         label: '전체',               color: 'bg-white/20 text-white' },
  { key: 'CC',          label: '🌍 국가 ↔ 국가',      color: 'bg-blue-500/30 text-blue-300' },
  { key: 'OO',          label: '🏛 조직 ↔ 조직',      color: 'bg-amber-500/30 text-amber-300' },
  { key: 'PP',          label: '👤 인물 ↔ 인물',      color: 'bg-purple-500/30 text-purple-300' },
  { key: 'CO',          label: '🌍↔🏛 국가-조직',     color: 'bg-teal-500/30 text-teal-300' },
  { key: 'CP',          label: '🌍↔👤 국가-인물',     color: 'bg-cyan-500/30 text-cyan-300' },
  { key: 'OP',          label: '🏛↔👤 조직-인물',     color: 'bg-pink-500/30 text-pink-300' },
]

/** 그래프 관계 표시 기준을 선택하는 필터 패널이다. */
export default function FilterPanel({ active, onChange }) {
  return (
    <div className="flex items-center gap-2 bg-white/10 backdrop-blur border border-white/20 rounded-xl px-4 py-2 flex-wrap">
      <span className="text-white/40 text-xs shrink-0">연결 필터</span>
      {FILTERS.map(f => (
        <button
          key={f.key}
          onClick={() => onChange(f.key)}
          className={`text-xs px-3 py-1 rounded-lg transition-all font-medium ${
            active === f.key
              ? f.color + ' ring-1 ring-white/30'
              : 'bg-white/5 text-white/40 hover:bg-white/10 hover:text-white/70'
          }`}
        >
          {f.label}
        </button>
      ))}
    </div>
  )
}

// 엣지 필터링 함수 — App.jsx에서 사용
export function filterEdges(edges, nodes, activeFilter) {
  if (activeFilter === 'all') return edges

  // 노드 이름 → 타입 맵
  const typeMap = {}
  nodes.forEach(n => { typeMap[n.id] = n.type })

  const typeCode = (t) => {
    if (t === 'Country')      return 'C'
    if (t === 'Organization') return 'O'
    if (t === 'Person')       return 'P'
    return '?'
  }

  return edges.filter(e => {
    const src = typeCode(typeMap[e.source?.id ?? e.source])
    const tgt = typeCode(typeMap[e.target?.id ?? e.target])
    const pair = [src, tgt].sort().join('')   // 항상 알파벳 순서
    return pair === activeFilter
  })
}
