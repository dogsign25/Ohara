import { apiError, http, responseData } from './http.js'

/** Axios 호출의 응답 변환과 공통 오류 메시지 처리를 적용한다. */
async function request(path, call) {
  try {
    const res = await call()
    return responseData(res)
  } catch (error) {
    throw apiError(error, `API: ${path}`)
  }
}

/** query parameter를 포함한 GET 요청을 공통 클라이언트로 전송한다. */
async function get(path, params = {}) {
  return request(path, () => http.get(path, { params }))
}

export const api = {
  /** 전체 그래프 — limit, minStrength로 크기/품질 조절 */
  getGraph(limit = 100, minStrength = 1, days) {
    return get('/graph', { limit, minStrength, days })
  },

  /** 특정 노드 상세 (관련 노드 + 기사) */
  getNode(name) {
    return get('/node', { name })
  },

  /** 검색 자동완성 */
  search(q, limit = 10) {
    return get('/search', { q, limit })
  },

  /** 워크스페이스 전용 그래프 */
  getWorkspaceGraph(workspaceId, limit = 100, minStrength = 1, days) {
    return get(`/graph/workspace/${workspaceId}`, { limit, minStrength, days })
  },

  deleteNode(name) {
    return request('/node', () => http.delete('/node', { params: { name } }))
  },

  updateNode(name, body) {
    return request('/node', () => http.patch('/node', body, { params: { name } }))
  },

  findPath(from, to, maxDepth = 5, workspaceId) {
    return get('/path', { from, to, maxDepth, workspaceId })
  },

  getEdgeSources(source, target, workspaceId) {
    return get('/edge/sources', { source, target, workspaceId })
  },

  createEdge(body) {
    return request('/edge', () => http.post('/edge', body))
  },
}
