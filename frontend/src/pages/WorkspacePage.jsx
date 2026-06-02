import { useEffect, useState } from 'react'
import { workspaceApi } from '../api/workspace.js'

const STATUS = {
  PENDING: { label: '대기 중', dot: 'bg-white/25', text: 'text-white/35' },
  ANALYZING: { label: '분석 중', dot: 'bg-amber-400 animate-pulse', text: 'text-amber-300' },
  DONE: { label: '완료', dot: 'bg-emerald-400', text: 'text-emerald-300' },
  ERROR: { label: '오류', dot: 'bg-red-400', text: 'text-red-300' },
}

function Panel({ title, children, action }) {
  return (
    <section className="rounded-lg border border-white/10 bg-white/[0.03]">
      <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
        <h2 className="text-sm font-medium text-white/85">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

export default function WorkspacePage({ onOpenGraph }) {
  const [workspaces, setWorkspaces] = useState([])
  const [selected, setSelected] = useState(null)
  const [docs, setDocs] = useState([])
  const [loading, setLoading] = useState(true)
  const [docLoading, setDocLoading] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')

  async function loadWorkspaces() {
    setLoading(true)
    setError('')
    try {
      const items = await workspaceApi.list()
      setWorkspaces(items)
      setSelected(prev => prev ?? items[0] ?? null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadDocs(workspace) {
    if (!workspace) return
    setDocLoading(true)
    try {
      const items = await workspaceApi.listDocuments(workspace.id)
      setDocs(items)
    } catch {
      setDocs([])
    } finally {
      setDocLoading(false)
    }
  }

  useEffect(() => { loadWorkspaces() }, [])
  useEffect(() => { loadDocs(selected) }, [selected])

  async function handleCreate(e) {
    e.preventDefault()
    if (!title.trim()) return
    try {
      const created = await workspaceApi.create(title.trim(), description.trim())
      setWorkspaces(prev => [created, ...prev])
      setSelected(created)
      setTitle('')
      setDescription('')
    } catch (err) {
      alert(err.message)
    }
  }

  async function handleAddUrl(e) {
    e.preventDefault()
    if (!selected || !url.trim()) return
    try {
      const doc = await workspaceApi.addUrl(selected.id, url.trim())
      setDocs(prev => [doc, ...prev])
      setUrl('')
    } catch (err) {
      alert(err.message)
    }
  }

  async function handleDeleteDoc(docId) {
    if (!selected) return
    try {
      await workspaceApi.deleteDocument(selected.id, docId)
      setDocs(prev => prev.filter(doc => doc.id !== docId))
    } catch (err) {
      alert(err.message)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs uppercase tracking-widest text-blue-300/70">Workspace</p>
          <h1 className="mt-2 text-2xl font-semibold tracking-tight">워크스페이스</h1>
          <p className="mt-2 text-sm text-white/40">URL과 문서를 모아 별도 그래프로 분석합니다.</p>
        </div>
        <button
          onClick={onOpenGraph}
          className="w-fit rounded-lg border border-blue-400/25 bg-blue-500/15 px-4 py-2 text-sm text-blue-200 transition-colors hover:bg-blue-500/25"
        >
          선택 그래프 열기
        </button>
      </div>

      <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
        <Panel title="목록">
          <form onSubmit={handleCreate} className="space-y-2 border-b border-white/10 p-4">
            <input
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="새 워크스페이스"
              className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white outline-none placeholder:text-white/25"
            />
            <input
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="설명"
              className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white outline-none placeholder:text-white/25"
            />
            <button className="w-full rounded-lg border border-white/10 bg-white/8 px-3 py-2 text-sm text-white/70 transition-colors hover:bg-white/12">
              만들기
            </button>
          </form>

          <div className="max-h-[520px] overflow-y-auto p-2">
            {loading ? (
              <p className="p-4 text-sm text-white/35">불러오는 중...</p>
            ) : error ? (
              <p className="p-4 text-sm text-red-300">{error}</p>
            ) : workspaces.length === 0 ? (
              <p className="p-4 text-sm text-white/35">워크스페이스가 없습니다.</p>
            ) : (
              workspaces.map(workspace => (
                <button
                  key={workspace.id}
                  onClick={() => setSelected(workspace)}
                  className={`w-full rounded-lg px-3 py-3 text-left transition-colors ${
                    selected?.id === workspace.id ? 'bg-blue-500/15 text-white' : 'text-white/55 hover:bg-white/5'
                  }`}
                >
                  <p className="truncate text-sm font-medium">{workspace.title}</p>
                  <p className="mt-1 line-clamp-2 text-xs text-white/30">{workspace.description || '설명 없음'}</p>
                </button>
              ))
            )}
          </div>
        </Panel>

        <Panel
          title={selected ? selected.title : '문서'}
          action={selected && <span className="text-xs text-white/30">{docs.length}개 문서</span>}
        >
          {selected ? (
            <>
              {!selected.defaultWorkspace && selected.id !== 0 && (
                <form onSubmit={handleAddUrl} className="flex gap-2 border-b border-white/10 p-4">
                  <input
                    value={url}
                    onChange={e => setUrl(e.target.value)}
                    placeholder="https://..."
                    className="min-w-0 flex-1 rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white outline-none placeholder:text-white/25"
                  />
                  <button className="rounded-lg border border-blue-400/25 bg-blue-500/15 px-4 py-2 text-sm text-blue-200 hover:bg-blue-500/25">
                    추가
                  </button>
                </form>
              )}

              <div className="divide-y divide-white/5">
                {docLoading ? (
                  <p className="p-6 text-sm text-white/35">문서를 불러오는 중...</p>
                ) : docs.length === 0 ? (
                  <p className="p-6 text-sm text-white/35">아직 문서가 없습니다.</p>
                ) : (
                  docs.map(doc => {
                    const status = STATUS[doc.status] ?? STATUS.PENDING
                    return (
                      <div key={doc.id} className="flex items-center gap-3 px-4 py-3">
                        <span className={`h-2 w-2 shrink-0 rounded-full ${status.dot}`} />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm text-white/80">{doc.title}</p>
                          <p className={`mt-1 text-xs ${status.text}`}>
                            {status.label}{doc.entityCount != null ? ` · 엔티티 ${doc.entityCount}개` : ''}
                          </p>
                        </div>
                        <button
                          onClick={() => handleDeleteDoc(doc.id)}
                          className="rounded-lg px-2 py-1 text-xs text-white/30 hover:bg-red-500/10 hover:text-red-300"
                        >
                          삭제
                        </button>
                      </div>
                    )
                  })
                )}
              </div>
            </>
          ) : (
            <p className="p-6 text-sm text-white/35">워크스페이스를 선택하세요.</p>
          )}
        </Panel>
      </div>
    </div>
  )
}
