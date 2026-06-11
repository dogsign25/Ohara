/** 그래프 노드 제한과 최소 관계 강도 필터를 조절한다. */
export default function GraphControls({ limit, minStrength, onLimit, onMinStrength }) {
  return (
    <div className="flex items-center gap-5 bg-white/10 backdrop-blur border border-white/20 rounded-xl px-5 py-2">

      <label className="flex items-center gap-3">
        <span className="text-white/50 text-xs whitespace-nowrap">노드 수</span>
        <input type="range" min={20} max={500} step={10} value={limit}
          onChange={e => onLimit(Number(e.target.value))}
          className="w-20 accent-blue-400"/>
        <span className="text-white text-xs w-8">{limit}</span>
      </label>

      <div className="w-px h-4 bg-white/20"/>

      <label className="flex items-center gap-3">
        <span className="text-white/50 text-xs whitespace-nowrap">최소 강도</span>
        <input type="range" min={1} max={20} step={1} value={minStrength}
          onChange={e => onMinStrength(Number(e.target.value))}
          className="w-20 accent-amber-400"/>
        <span className="text-white text-xs w-4">{minStrength}</span>
      </label>
    </div>
  )
}
