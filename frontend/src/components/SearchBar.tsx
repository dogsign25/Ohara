// components/SearchBar.tsx
import { useState, useEffect, useRef } from "react";
import { api, NodeDto } from "../api/client";

interface Props {
  onSelect: (node: NodeDto) => void;
}

// 타입별 색상
const TYPE_COLOR: Record<string, string> = {
  Country:      "bg-blue-500/20 text-blue-300",
  Organization: "bg-amber-500/20 text-amber-300",
  Person:       "bg-purple-500/20 text-purple-300",
};

export function SearchBar({ onSelect }: Props) {
  const [query, setQuery]         = useState("");
  const [results, setResults]     = useState<NodeDto[]>([]);
  const [loading, setLoading]     = useState(false);
  const [open, setOpen]           = useState(false);
  const debounceRef               = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    if (query.length < 2) {
      setResults([]);
      setOpen(false);
      return;
    }
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await api.search(query);
        setResults(data);
        setOpen(true);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 300);

    return () => clearTimeout(debounceRef.current);
  }, [query]);

  function handleSelect(node: NodeDto) {
    setQuery(node.name);
    setOpen(false);
    onSelect(node);
  }

  return (
    <div className="relative w-full max-w-md">
      <div className="flex items-center gap-2 bg-white/10 backdrop-blur border border-white/20 rounded-xl px-4 py-2">
        <svg className="w-4 h-4 text-white/50 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z"/>
        </svg>
        <input
          className="bg-transparent text-white placeholder-white/40 outline-none w-full text-sm"
          placeholder="국가, 기관, 인물 검색..."
          value={query}
          onChange={e => setQuery(e.target.value)}
          onFocus={() => results.length > 0 && setOpen(true)}
        />
        {loading && (
          <div className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin shrink-0"/>
        )}
      </div>

      {open && results.length > 0 && (
        <ul className="absolute top-full mt-2 w-full bg-gray-900 border border-white/10 rounded-xl overflow-hidden shadow-2xl z-50">
          {results.map(node => (
            <li
              key={`${node.type}:${node.name}`}
              className="flex items-center justify-between px-4 py-2.5 hover:bg-white/5 cursor-pointer transition-colors"
              onMouseDown={() => handleSelect(node)}
            >
              <span className="text-white text-sm">{node.name}</span>
              <span className={`text-xs px-2 py-0.5 rounded-full ${TYPE_COLOR[node.type] ?? ""}`}>
                {node.type}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
