// api/client.ts
// ─────────────
// 내 지적 #4 반영: limit, minStrength 파라미터 지원

const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api";

export interface NodeDto {
  id: string;
  name: string;
  type: "Country" | "Organization" | "Person";
  degree: number;
}

export interface EdgeDto {
  source: string;
  target: string;
  strength: number;
  articleCount: number;
  lastMentioned: string;
}

export interface GraphResponse {
  nodes: NodeDto[];
  edges: EdgeDto[];
  totalNodes: number;
  totalEdges: number;
}

export interface ArticleDto {
  title: string;
  url: string;
  source: string;
  publishedAt: string;
}

export interface NodeDetailDto {
  name: string;
  type: string;
  degree: number;
  relatedNodes: NodeDto[];
  recentArticles: ArticleDto[];
}

async function get<T>(path: string, params?: Record<string, unknown>): Promise<T> {
  const url = new URL(`${BASE}${path}`);
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined) url.searchParams.set(k, String(v));
    });
  }
  const res = await fetch(url.toString());
  if (!res.ok) throw new Error(`API ${res.status}: ${path}`);
  return res.json();
}

export const api = {
  /** 그래프 전체 조회 */
  getGraph(limit = 100, minStrength = 1): Promise<GraphResponse> {
    return get("/graph", { limit, minStrength });
  },

  /** 특정 노드 상세 */
  getNode(name: string): Promise<NodeDetailDto> {
    return get(`/node/${encodeURIComponent(name)}`);
  },

  /** 노드 관련 기사 */
  getNodeArticles(name: string): Promise<ArticleDto[]> {
    return get(`/node/${encodeURIComponent(name)}/articles`);
  },

  /** 검색 자동완성 */
  search(q: string, limit = 10): Promise<NodeDto[]> {
    return get("/search", { q, limit });
  },
};
