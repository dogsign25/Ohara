import { useEffect, useState } from 'react'
import { api } from '../api/client.js'

const BADGE = {
  Country:      'bg-blue-500/30 text-blue-200',
  Organization: 'bg-amber-500/30 text-amber-200',
  Person:       'bg-purple-500/30 text-purple-200',
}

export default function ArticlePanel({ selectedNode, onClose, onDelete }) {
  const [detail,  setDetail]  = useState(null)
  const [loading, setLoading] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    if (!selectedNode) { setDetail(null); return }
    setLoading(true)
    api.getNode(selectedNode)
      .then(setDetail)
      .catch(() => setDetail(null))
      .finally(() => setLoading(false))
  }, [selectedNode])

  if (!selectedNode) return null

  async function handleDelete() {
    if (!confirm(`'${selectedNode}' 노드를 삭제할까요? 연결 관계도 함께 삭제됩니다.`)) return
    setDeleting(true)
    try {
      await api.deleteNode(selectedNode)
      onDelete?.(selectedNode)
      onClose()
    } catch (err) {
      alert(err.message)
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="absolute right-4 top-20 bottom-4 w-80 bg-gray-900/90 backdrop-blur border border-white/10 rounded-2xl flex flex-col overflow-hidden shadow-2xl z-40">

      {/* 헤더 */}
      <div className="flex items-start justify-between p-4 border-b border-white/10">
        <div>
          {loading
            ? <div className="h-5 w-32 bg-white/10 rounded animate-pulse"/>
            : <>
                <h2 className="text-white font-medium">{detail?.name ?? selectedNode}</h2>
                {detail && (
                  <div className="flex items-center gap-2 mt-1">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${BADGE[detail.type] ?? ''}`}>
                      {detail.type}
                    </span>
                    <span className="text-white/40 text-xs">{detail.degree}개 연결</span>
                  </div>
                )}
              </>
          }
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleDelete}
            disabled={deleting}
            className="text-white/35 hover:text-red-400 disabled:text-white/15 transition-colors"
            title="노드 삭제"
          >
            {deleting ? (
              <div className="w-4 h-4 border-2 border-white/20 border-t-white/70 rounded-full animate-spin" />
            ) : (
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
              </svg>
            )}
          </button>
          <button onClick={onClose} className="text-white/40 hover:text-white transition-colors">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
      </div>

      {/* 관련 노드 태그 */}
      {detail?.relatedNodes?.length > 0 && (
        <div className="px-4 py-3 border-b border-white/10">
          <p className="text-white/40 text-xs mb-2">주요 연결</p>
          <div className="flex flex-wrap gap-1.5">
            {detail.relatedNodes.slice(0, 8).map(n => (
              <span key={n.name}
                className={`text-xs px-2 py-1 rounded-lg ${BADGE[n.type] ?? 'bg-white/10 text-white/70'}`}>
                {n.name}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* 기사 목록 */}
      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="p-4 space-y-3">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="space-y-1.5">
                <div className="h-3 bg-white/10 rounded animate-pulse"/>
                <div className="h-3 w-3/4 bg-white/10 rounded animate-pulse"/>
              </div>
            ))}
          </div>
        ) : detail?.recentArticles?.length ? (
          <ul className="divide-y divide-white/5">
            {detail.recentArticles.map((article, i) => (
              <li key={i} className="px-4 py-3 hover:bg-white/5 transition-colors">
                <a href={article.url} target="_blank" rel="noopener noreferrer" className="block group">
                  <p className="text-white text-sm leading-snug group-hover:text-blue-300 transition-colors line-clamp-2">
                    {article.title}
                  </p>
                  <div className="flex items-center gap-2 mt-1.5">
                    <span className="text-white/40 text-xs">{article.source}</span>
                    <span className="text-white/20 text-xs">·</span>
                    <span className="text-white/40 text-xs">
                      {article.publishedAt
                        ? new Date(article.publishedAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
                        : ''}
                    </span>
                  </div>
                </a>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-white/30 text-sm text-center mt-8">관련 기사 없음</p>
        )}
      </div>
    </div>
  )
}
