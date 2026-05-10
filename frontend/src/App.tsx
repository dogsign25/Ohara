// App.tsx
import { useCallback, useEffect, useRef, useState } from "react";
import ForceGraph2D, { ForceGraphMethods } from "react-force-graph-2d";
import { api, GraphResponse, NodeDto } from "./api/client";
import { SearchBar } from "./components/SearchBar";
import { ArticlePanel } from "./components/ArticlePanel";
import { GraphControls } from "./components/GraphControls";

// ── 타입별 노드 색상 ──────────────────────────────────────────────
const NODE_COLOR: Record<string, string> = {
  Country:      "#60a5fa",   // blue-400
  Organization: "#fbbf24",   // amber-400
  Person:       "#c084fc",   // purple-400
};

// ── ForceGraph에 넘길 데이터 형식 ─────────────────────────────────
interface GraphNode {
  id: string;
  name: string;
  type: string;
  degree: number;
  x?: number;
  y?: number;
}

interface GraphLink {
  source: string;
  target: string;
  strength: number;
}

interface GraphData {
  nodes: GraphNode[];
  links: GraphLink[];
}

export default function App() {
  const fgRef = useRef<ForceGraphMethods>();

  const [graphData, setGraphData]     = useState<GraphData>({ nodes: [], links: [] });
  const [loading, setLoading]         = useState(true);
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const [highlightNode, setHighlightNode] = useState<string | null>(null);

  // 내 지적 #4: 사용자가 실시간으로 조절 가능
  const [limit, setLimit]             = useState(100);
  const [minStrength, setMinStrength] = useState(1);

  // ── 데이터 로드 ──────────────────────────────────────────────────
  const loadGraph = useCallback(async () => {
    setLoading(true);
    try {
      const data: GraphResponse = await api.getGraph(limit, minStrength);
      setGraphData({
        nodes: data.nodes.map(n => ({ id: n.name, name: n.name, type: n.type, degree: n.degree })),
        links: data.edges.map(e => ({ source: e.source, target: e.target, strength: e.strength })),
      });
    } catch (err) {
      console.error("그래프 로드 실패:", err);
    } finally {
      setLoading(false);
    }
  }, [limit, minStrength]);

  useEffect(() => { loadGraph(); }, [loadGraph]);

  // ── 검색으로 노드 포커스 ─────────────────────────────────────────
  function handleSearchSelect(node: NodeDto) {
    setSelectedNode(node.name);
    setHighlightNode(node.name);
    const found = graphData.nodes.find(n => n.id === node.name);
    if (found?.x && found?.y) {
      fgRef.current?.centerAt(found.x, found.y, 800);
      fgRef.current?.zoom(3, 800);
    }
  }

  // ── 노드 렌더링 (크기 = degree 기반) ────────────────────────────
  const paintNode = useCallback(
    (node: GraphNode, ctx: CanvasRenderingContext2D, globalScale: number) => {
      const r        = Math.sqrt(node.degree + 1) * 4 + 2;
      const isSelected = node.id === highlightNode;
      const color    = NODE_COLOR[node.type] ?? "#9ca3af";

      // 하이라이트 링
      if (isSelected) {
        ctx.beginPath();
        ctx.arc(node.x ?? 0, node.y ?? 0, r + 4, 0, 2 * Math.PI);
        ctx.fillStyle = `${color}33`;
        ctx.fill();
      }

      // 노드 원
      ctx.beginPath();
      ctx.arc(node.x ?? 0, node.y ?? 0, r, 0, 2 * Math.PI);
      ctx.fillStyle = isSelected ? color : `${color}cc`;
      ctx.fill();

      // 레이블 (일정 줌 이상에서만 표시)
      if (globalScale > 0.8) {
        const label = node.name.length > 16 ? `${node.name.slice(0, 14)}…` : node.name;
        ctx.font = `${Math.max(10 / globalScale, 8)}px sans-serif`;
        ctx.fillStyle = "rgba(255,255,255,0.85)";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(label, node.x ?? 0, (node.y ?? 0) + r + 8 / globalScale);
      }
    },
    [highlightNode]
  );

  const getNodeRadius = useCallback(
    (node: GraphNode) => Math.sqrt(node.degree + 1) * 4 + 2,
    []
  );

  const getLinkWidth = useCallback(
    (link: GraphLink) => Math.min(Math.log2(link.strength + 1) * 1.5, 6),
    []
  );

  return (
    <div className="relative w-screen h-screen bg-gray-950 overflow-hidden">
      {/* 상단 헤더 */}
      <div className="absolute top-4 left-4 right-4 flex items-center gap-3 z-30">
        <div className="text-white font-semibold text-lg tracking-tight">OHARA</div>
        <SearchBar onSelect={handleSearchSelect}/>
        <GraphControls
          limit={limit} minStrength={minStrength}
          onLimitChange={setLimit} onMinStrengthChange={setMinStrength}
        />
        <div className="ml-auto flex items-center gap-2 text-white/30 text-xs">
          {graphData.nodes.length}개 노드 · {graphData.links.length}개 관계
        </div>
      </div>

      {/* 범례 */}
      <div className="absolute bottom-4 left-4 flex gap-3 z-30">
        {Object.entries(NODE_COLOR).map(([type, color]) => (
          <div key={type} className="flex items-center gap-1.5">
            <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: color }}/>
            <span className="text-white/50 text-xs">{type}</span>
          </div>
        ))}
      </div>

      {/* 로딩 */}
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center z-20 bg-gray-950/70">
          <div className="text-white/60 text-sm">데이터 로딩 중...</div>
        </div>
      )}

      {/* 그래프 */}
      <ForceGraph2D
        ref={fgRef}
        graphData={graphData as any}
        backgroundColor="#030712"
        nodeId="id"
        nodeCanvasObject={paintNode as any}
        nodePointerAreaPaint={(node: any, color, ctx) => {
          const r = getNodeRadius(node);
          ctx.fillStyle = color;
          ctx.beginPath();
          ctx.arc(node.x, node.y, r, 0, 2 * Math.PI);
          ctx.fill();
        }}
        linkWidth={getLinkWidth as any}
        linkColor={() => "rgba(255,255,255,0.12)"}
        onNodeClick={(node: any) => {
          setSelectedNode(node.id);
          setHighlightNode(node.id);
        }}
        onBackgroundClick={() => {
          setSelectedNode(null);
          setHighlightNode(null);
        }}
        cooldownTicks={100}
      />

      {/* 기사 패널 */}
      <ArticlePanel
        selectedNode={selectedNode}
        onClose={() => { setSelectedNode(null); setHighlightNode(null); }}
      />
    </div>
  );
}
