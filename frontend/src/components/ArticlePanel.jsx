import { useEffect, useState } from 'react'
import { api } from '../api/client.js'

const BADGE = {
  Country:      'bg-blue-500/30 text-blue-200',
  Organization: 'bg-amber-500/30 text-amber-200',
  Person:       'bg-purple-500/30 text-purple-200',
}

/** 선택한 엔티티의 상세 정보와 수정·삭제 동작을 제공한다. */
export default function ArticlePanel({ selectedNode, onClose, onDelete, onUpdated, onConnect }) {
  const [detail,  setDetail]  = useState(null)
  const [loading, setLoading] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState('')
  const [editType, setEditType] = useState('Country')

  useEffect(() => {
    if (!selectedNode) { setDetail(null); return }
    setLoading(true)
    api.getNode(selectedNode)
      .then(data => {
        setDetail(data)
        setEditName(data.name)
        setEditType(data.type)
      })
      .catch(() => setDetail(null))
      .finally(() => setLoading(false))
  }, [selectedNode])

  if (!selectedNode) return null

  /** 확인 후 선택한 Neo4j 엔티티 노드를 삭제한다. */
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

  /** 변경된 엔티티 이름과 타입을 서버에 저장한다. */
  async function handleUpdate(e) {
    e.preventDefault()
    if (!editName.trim()) return
    try {
      const updated = await api.updateNode(selectedNode, {
        name: editName.trim(),
        type: editType,
      })
      setDetail(prev => prev ? { ...prev, name: updated.name, type: updated.type } : prev)
      setEditing(false)
      onUpdated?.()
      onClose()
    } catch (err) {
      alert(err.message)
    }
  }

  return (
    <aside
      className="absolute right-4 top-20 bottom-4 w-80 bg-gray-900/90 backdrop-blur border border-white/10 rounded-2xl flex flex-col overflow-hidden shadow-2xl z-50 pointer-events-auto"
      aria-label={`${selectedNode} 노드 상세`}
    >

      {/* 헤더 */}
      <div className="flex items-start justify-between p-4 border-b border-white/10">
        <div>
          {loading
            ? <div className="h-5 w-32 bg-white/10 rounded animate-pulse"/>
            : editing
              ? (
                <form onSubmit={handleUpdate} className="space-y-2">
                  <input
                    value={editName}
                    onChange={e => setEditName(e.target.value)}
                    className="w-full bg-white/5 border border-white/15 rounded-lg px-2 py-1.5 text-white text-sm outline-none"
                  />
                  <div className="flex gap-2">
                    <select
                      value={editType}
                      onChange={e => setEditType(e.target.value)}
                      className="ohara-select flex-1 bg-gray-950 border border-white/15 rounded-lg px-2 py-1.5 text-white/80 text-xs outline-none"
                    >
                      <option>Country</option>
                      <option>Organization</option>
                      <option>Person</option>
                    </select>
                    <button className="px-2.5 py-1.5 rounded-lg bg-blue-500/15 border border-blue-400/25 text-blue-300 text-xs">
                      저장
                    </button>
                  </div>
                </form>
              )
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
            onClick={() => onConnect?.(detail?.name ?? selectedNode)}
            className="text-white/35 hover:text-emerald-300 transition-colors"
            title="다른 노드와 관계 연결"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M13.828 10.172a4 4 0 010 5.656l-2 2a4 4 0 01-5.656-5.656l1-1m3-3 2-2a4 4 0 015.656 5.656l-1 1"/>
            </svg>
          </button>
          <button
            onClick={() => setEditing(v => !v)}
            className="text-white/35 hover:text-blue-300 transition-colors"
            title="엔티티 수정"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
            </svg>
          </button>
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
    </aside>
  )
}
