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

async function del(path) {
  const res = await fetch(`/api${path}`, { method: 'DELETE' })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.message || `API ${res.status}: ${path}`)
  return data
}

async function patch(path, body) {
  const res = await fetch(`/api${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.message || `API ${res.status}: ${path}`)
  return data
}

export const api = {
  /** 전체 그래프 — limit, minStrength로 크기/품질 조절 */
  getGraph(limit = 100, minStrength = 1, days) {
    return get('/graph', { limit, minStrength, days })
  },

  /** 특정 노드 상세 (관련 노드 + 기사) */
  getNode(name) {
    return get(`/node/${encodeURIComponent(name)}`)
  },

  /** 검색 자동완성 */
  search(q, limit = 10) {
    return get('/search', { q, limit })
  },

  // ★ 추가: 워크스페이스 전용 그래프
  getWorkspaceGraph(workspaceId, limit = 100, minStrength = 1, days) {
      return get(`/graph/workspace/${workspaceId}`, { limit, minStrength, days })
  },

  deleteNode(name) {
    return del(`/node/${encodeURIComponent(name)}`)
  },

  updateNode(name, body) {
    return patch(`/node/${encodeURIComponent(name)}`, body)
  },

  findPath(from, to, maxDepth = 5, workspaceId) {
    return get('/path', { from, to, maxDepth, workspaceId })
  },

  getEdgeSources(source, target, workspaceId) {
    return get('/edge/sources', { source, target, workspaceId })
  },
}
