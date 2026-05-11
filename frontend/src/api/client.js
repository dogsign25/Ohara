async function get(path, params = {}) {
  // /api/graph 처럼 풀 경로를 origin에 붙여야 Vite 프록시가 정상 동작
  const url = new URL(`/api${path}`, window.location.origin)
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) url.searchParams.set(k, v)
  })
  const res = await fetch(url.toString())
  if (!res.ok) throw new Error(`API ${res.status}: ${path}`)
  return res.json()
}

export const api = {
  /** 전체 그래프 — limit, minStrength로 크기/품질 조절 */
  getGraph(limit = 100, minStrength = 1) {
    return get('/graph', { limit, minStrength })
  },

  /** 특정 노드 상세 (관련 노드 + 기사) */
  getNode(name) {
    return get(`/node/${encodeURIComponent(name)}`)
  },

  /** 검색 자동완성 */
  search(q, limit = 10) {
    return get('/search', { q, limit })
  },
}