// src/components/WorkspacePanel.jsx
// ChatGPT 사이드바처럼: 워크스페이스 목록 + 선택된 워크스페이스의 문서 관리
import { useEffect, useRef, useState, useCallback } from 'react'
import { workspaceApi } from '../api/workspace.js'

// ── 아이콘 ──────────────────────────────────────────────────────────
const Icon = {
    Plus:    () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4"/></svg>,
    Trash:   () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>,
    Back:    () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7"/></svg>,
    Link:    () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"/></svg>,
    Edit:    () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>,
    Globe:   () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9"/></svg>,
    Spinner: () => <div className="w-3.5 h-3.5 border-2 border-white/20 border-t-white/70 rounded-full animate-spin"/>,
    Chart:   () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 3.055A9.001 9.001 0 1020.945 13H11V3.055z"/><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.488 9H15V3.512A9.025 9.025 0 0120.488 9z"/></svg>,
    Collapse: () => <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 19l-7-7 7-7M20 19l-7-7 7-7"/></svg>,
}

// 상태 색상
const STATUS = {
    PENDING:   { color: 'text-white/30', dot: 'bg-white/20',   label: '대기 중' },
    ANALYZING: { color: 'text-amber-400', dot: 'bg-amber-400 animate-pulse', label: '분석 중' },
    DONE:      { color: 'text-emerald-400', dot: 'bg-emerald-400', label: '완료' },
    ERROR:     { color: 'text-red-400',  dot: 'bg-red-400',    label: '오류' },
}

// ── 문서 행 ─────────────────────────────────────────────────────────
function DocRow({ doc, onDelete }) {
    const s = STATUS[doc.status] ?? STATUS.PENDING
    return (
        <div className="group flex items-center gap-3 px-4 py-2.5 hover:bg-white/5 transition-colors">
            <Icon.Globe/>
            <div className="flex-1 min-w-0">
                <p className="text-white/80 text-xs font-medium truncate">{doc.title}</p>
                <div className="flex items-center gap-2 mt-0.5">
                    <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${s.dot}`}/>
                    <span className={`text-xs ${s.color}`}>{s.label}</span>
                    {doc.status === 'DONE' && (
                        <span className="text-white/30 text-xs">· {doc.entityCount}개 엔티티</span>
                    )}
                </div>
            </div>
            <button
                onClick={() => onDelete(doc.id)}
                className="opacity-0 group-hover:opacity-100 text-white/25 hover:text-red-400 transition-all"
            >
                <Icon.Trash/>
            </button>
        </div>
    )
}

// ── URL 입력 폼 ──────────────────────────────────────────────────────
function AddUrlForm({ workspaceId, onAdded }) {
    const [mode,    setMode]    = useState('url')
    const [url,     setUrl]     = useState('')
    const [title,   setTitle]   = useState('')
    const [text,    setText]    = useState('')
    const [file,    setFile]    = useState(null)
    const [loading, setLoading] = useState(false)
    const [error,   setError]   = useState('')
    const inputRef = useRef()

    useEffect(() => { inputRef.current?.focus() }, [])

    async function handleSubmit(e) {
        e.preventDefault()
        if (mode === 'url' && !url.trim()) return
        if (mode === 'text' && !text.trim()) return
        if (mode === 'file' && !file) return
        if (mode === 'url' && !url.startsWith('http://') && !url.startsWith('https://')) {
            setError('http:// 또는 https://로 시작하는 URL을 입력하세요.')
            return
        }
        setError('')
        setLoading(true)
        try {
            const doc = mode === 'url'
                ? await workspaceApi.addUrl(workspaceId, url.trim())
                : mode === 'text'
                    ? await workspaceApi.addText(workspaceId, title.trim() || 'Untitled note', text.trim())
                    : await workspaceApi.addFile(workspaceId, file)
            onAdded(doc)
            setUrl('')
            setTitle('')
            setText('')
            setFile(null)
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="px-4 pb-3">
            <div className="flex gap-1 mb-2">
                {[
                    ['url', 'URL'],
                    ['text', '텍스트'],
                    ['file', '파일'],
                ].map(([key, label]) => (
                    <button
                        key={key}
                        type="button"
                        onClick={() => { setMode(key); setError('') }}
                        className={`px-2.5 py-1 rounded-lg text-xs border transition-colors ${
                            mode === key
                                ? 'bg-blue-500/20 border-blue-400/30 text-blue-200'
                                : 'bg-white/5 border-white/10 text-white/35 hover:text-white/60'
                        }`}
                    >
                        {label}
                    </button>
                ))}
            </div>
            <div className="space-y-2">
                {mode === 'url' && (
                    <div className="flex gap-2 items-center bg-white/5 border border-white/15 rounded-xl px-3 py-2">
                        <Icon.Link/>
                        <input
                            ref={inputRef}
                            value={url}
                            onChange={e => setUrl(e.target.value)}
                            placeholder="https://..."
                            className="flex-1 bg-transparent text-white/80 text-xs outline-none placeholder-white/25"
                            disabled={loading}
                        />
                    </div>
                )}
                {mode === 'text' && (
                    <>
                        <input
                            value={title}
                            onChange={e => setTitle(e.target.value)}
                            placeholder="문서 제목"
                            className="w-full bg-white/5 border border-white/15 rounded-xl px-3 py-2 text-white/80 text-xs outline-none placeholder-white/25"
                            disabled={loading}
                        />
                        <textarea
                            value={text}
                            onChange={e => setText(e.target.value)}
                            placeholder="분석할 본문을 붙여넣으세요"
                            className="w-full h-28 resize-none bg-white/5 border border-white/15 rounded-xl px-3 py-2 text-white/80 text-xs outline-none placeholder-white/25"
                            disabled={loading}
                        />
                    </>
                )}
                {mode === 'file' && (
                    <input
                        type="file"
                        accept=".pdf,.txt,.md,text/plain,application/pdf"
                        onChange={e => setFile(e.target.files?.[0] ?? null)}
                        className="w-full bg-white/5 border border-white/15 rounded-xl px-3 py-2 text-white/60 text-xs file:mr-3 file:rounded-lg file:border-0 file:bg-white/10 file:px-2 file:py-1 file:text-white/70"
                        disabled={loading}
                    />
                )}
            </div>
            <div className="flex justify-end mt-2">
                <button
                    type="submit"
                    disabled={loading || (mode === 'url' && !url.trim()) || (mode === 'text' && !text.trim()) || (mode === 'file' && !file)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-blue-500/15 border border-blue-400/25 text-blue-300 hover:bg-blue-500/25 disabled:bg-white/5 disabled:border-white/10 disabled:text-white/20 transition-colors text-xs"
                >
                    {loading ? <Icon.Spinner/> : <Icon.Plus/>}
                    추가
                </button>
            </div>
            {error && <p className="text-red-400 text-xs mt-1.5 px-1">{error}</p>}
        </form>
    )
}

// ── 워크스페이스 상세 (문서 목록) ───────────────────────────────────
function WorkspaceDetail({ workspace, onBack, onClose, onSelectWorkspace }) {
    const [docs,     setDocs]     = useState([])
    const [loading,  setLoading]  = useState(true)
    const [showAdd,  setShowAdd]  = useState(false)
    const isDefault = workspace.defaultWorkspace || workspace.id === 0

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const d = await workspaceApi.listDocuments(workspace.id)
            setDocs(d)
        } catch { /* ignore */ }
        finally { setLoading(false) }
    }, [workspace.id])

    useEffect(() => { load() }, [load])

    async function handleDelete(docId) {
        try {
            await workspaceApi.deleteDocument(workspace.id, docId)
            setDocs(prev => prev.filter(d => d.id !== docId))
        } catch (err) { alert(err.message) }
    }

    function handleAdded(doc) {
        setDocs(prev => [doc, ...prev])
        setShowAdd(false)
    }

    return (
        <div className="flex flex-col h-full">
            {/* 헤더 */}
            <div className="flex items-center gap-3 px-4 py-4 border-b border-white/10 shrink-0">
                <button
                    onClick={onBack}
                    className="text-white/40 hover:text-white/70 transition-colors"
                    title="목록으로"
                >
                    <Icon.Back/>
                </button>
                <div className="flex-1 min-w-0">
                    <h2 className="text-white text-sm font-semibold truncate">{workspace.title}</h2>
                    <p className="text-white/35 text-xs mt-0.5">
                        {isDefault ? '공유 기본 그래프' : `${docs.length}개 문서`}
                    </p>
                </div>
                {/* 이 워크스페이스 그래프 보기 버튼 */}
                <button
                    onClick={() => onSelectWorkspace(workspace.id)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-500/20 border border-blue-400/30 text-blue-300 text-xs hover:bg-blue-500/30 transition-all"
                    title="이 워크스페이스의 그래프 보기"
                >
                    <Icon.Chart/>
                    그래프
                </button>
                <button
                    onClick={onClose}
                    className="p-1.5 rounded-lg text-white/35 hover:text-white/70 hover:bg-white/5 transition-colors"
                    title="워크스페이스 접기"
                >
                    <Icon.Collapse/>
                </button>
            </div>

            {/* URL 추가 버튼 */}
            {!isDefault && (
                <div className="px-4 py-3 border-b border-white/5 shrink-0">
                    {showAdd ? (
                        <AddUrlForm workspaceId={workspace.id} onAdded={handleAdded}/>
                    ) : (
                        <button
                            onClick={() => setShowAdd(true)}
                            className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl border border-dashed border-white/20 text-white/45 text-xs hover:text-white/70 hover:border-white/35 hover:bg-white/5 transition-all"
                        >
                            <Icon.Link/>
                            문서 추가
                        </button>
                    )}
                </div>
            )}

            {/* 문서 목록 */}
            <div className="flex-1 overflow-y-auto">
                {loading ? (
                    <div className="flex items-center justify-center h-24">
                        <Icon.Spinner/>
                    </div>
                ) : isDefault ? (
                    <div className="flex flex-col items-center justify-center h-32 text-center px-6">
                        <Icon.Chart/>
                        <p className="text-white/25 text-xs mt-3">현재 기본 그래프의 모든 노드를 보여줍니다</p>
                    </div>
                ) : docs.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-32 text-center px-6">
                        <Icon.Link/>
                        <p className="text-white/25 text-xs mt-3">문서가 없습니다</p>
                        <p className="text-white/15 text-xs mt-1">URL을 추가해 분석을 시작하세요</p>
                    </div>
                ) : (
                    docs.map(doc => (
                        <DocRow key={doc.id} doc={doc} onDelete={handleDelete}/>
                    ))
                )}
            </div>
        </div>
    )
}

// ── 워크스페이스 카드 (목록) ─────────────────────────────────────────
function WorkspaceCard({ workspace, onSelect, onDelete, onRename }) {
    const [editing, setEditing] = useState(false)
    const [title,   setTitle]   = useState(workspace.title)
    const inputRef = useRef()
    const isDefault = workspace.defaultWorkspace || workspace.id === 0

    useEffect(() => { if (editing) inputRef.current?.focus() }, [editing])

    async function commitRename() {
        setEditing(false)
        if (title.trim() && title !== workspace.title) {
            try { await onRename(workspace.id, title.trim()) }
            catch { setTitle(workspace.title) }
        } else {
            setTitle(workspace.title)
        }
    }

    const updated = new Date(workspace.updatedAt)
    const timeStr = updated.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })

    return (
        <div
            className="group flex items-center gap-3 px-4 py-3 hover:bg-white/5 cursor-pointer transition-colors"
            onClick={() => !editing && onSelect(workspace)}
        >
            {/* 워크스페이스 아이콘 */}
            <div className="w-7 h-7 rounded-lg bg-white/8 border border-white/10 flex items-center justify-center shrink-0 text-white/50">
                <Icon.Chart/>
            </div>

            <div className="flex-1 min-w-0">
                {editing ? (
                    <input
                        ref={inputRef}
                        value={title}
                        onChange={e => setTitle(e.target.value)}
                        onBlur={commitRename}
                        onKeyDown={e => { if (e.key === 'Enter') commitRename(); if (e.key === 'Escape') { setEditing(false); setTitle(workspace.title) }}}
                        onClick={e => e.stopPropagation()}
                        className="w-full bg-white/10 border border-white/20 rounded px-2 py-0.5 text-white text-sm outline-none"
                    />
                ) : (
                    <p className="text-white/80 text-sm font-medium truncate">{title}</p>
                )}
                <p className="text-white/30 text-xs mt-0.5">
                    {isDefault ? '공유 기본 그래프' : `${workspace.docCount}개 문서 · ${timeStr}`}
                </p>
            </div>

            {/* 액션 버튼 */}
            {!isDefault && (
                <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                        onClick={e => { e.stopPropagation(); setEditing(true) }}
                        className="p-1 text-white/30 hover:text-white/60 transition-colors"
                    >
                        <Icon.Edit/>
                    </button>
                    <button
                        onClick={e => { e.stopPropagation(); onDelete(workspace.id) }}
                        className="p-1 text-white/30 hover:text-red-400 transition-colors"
                    >
                        <Icon.Trash/>
                    </button>
                </div>
            )}
        </div>
    )
}

// ── 메인 패널 ────────────────────────────────────────────────────────
export default function WorkspacePanel({ show, onClose, onSelectWorkspace }) {
    const [workspaces,   setWorkspaces]   = useState([])
    const [selected,     setSelected]     = useState(null) // Workspace 객체
    const [loading,      setLoading]      = useState(true)
    const [creating,     setCreating]     = useState(false)
    const [newTitle,     setNewTitle]     = useState('')
    const newInputRef = useRef()

    // 목록 로드
    const loadWorkspaces = useCallback(async () => {
        setLoading(true)
        try {
            const list = await workspaceApi.list()
            setWorkspaces(list)
        } catch { /* 미로그인 등 */ }
        finally { setLoading(false) }
    }, [])

    useEffect(() => { if (show) loadWorkspaces() }, [show, loadWorkspaces])
    useEffect(() => { if (creating) newInputRef.current?.focus() }, [creating])

    async function handleCreate(e) {
        e.preventDefault()
        if (!newTitle.trim()) return
        try {
            const ws = await workspaceApi.create(newTitle.trim())
            setWorkspaces(prev => [ws, ...prev])
            setNewTitle('')
            setCreating(false)
            setSelected(ws) // 생성 직후 해당 워크스페이스로 이동
        } catch (err) { alert(err.message) }
    }

    async function handleDelete(id) {
        if (!confirm('워크스페이스를 삭제할까요?')) return
        try {
            await workspaceApi.delete(id)
            setWorkspaces(prev => prev.filter(w => w.id !== id))
            if (selected?.id === id) setSelected(null)
        } catch (err) { alert(err.message) }
    }

    async function handleRename(id, title) {
        const ws = await workspaceApi.rename(id, title)
        setWorkspaces(prev => prev.map(w => w.id === id ? ws : w))
    }

    return (
        <div
            className={`absolute left-0 top-0 bottom-0 z-40 flex transition-all duration-300 ease-in-out
                ${show ? 'translate-x-0' : '-translate-x-full'}`}
            style={{ width: '300px' }}
        >
            <div className="flex-1 bg-gray-900/95 backdrop-blur-xl border-r border-white/10 flex flex-col overflow-hidden">

                {/* ── 워크스페이스 상세 뷰 ── */}
                {selected ? (
                    <WorkspaceDetail
                        workspace={selected}
                        onBack={() => setSelected(null)}
                        onClose={onClose}
                        onSelectWorkspace={onSelectWorkspace}
                    />
                ) : (
                    <>
                        {/* ── 워크스페이스 목록 뷰 ── */}
                        {/* 헤더 */}
                        <div className="flex items-center justify-between px-4 py-4 border-b border-white/10 shrink-0">
                            <span className="text-white/60 text-sm font-semibold">워크스페이스</span>
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={() => setCreating(v => !v)}
                                    className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-blue-500/15 border border-blue-400/25 text-blue-300 hover:bg-blue-500/25 transition-colors text-xs"
                                >
                                    <Icon.Plus/>
                                    추가
                                </button>
                                <button
                                    onClick={onClose}
                                    className="p-1.5 rounded-lg text-white/35 hover:text-white/70 hover:bg-white/5 transition-colors"
                                    title="워크스페이스 접기"
                                >
                                    <Icon.Collapse/>
                                </button>
                            </div>
                        </div>

                        {/* 새 워크스페이스 입력 */}
                        {creating && (
                            <form onSubmit={handleCreate} className="px-4 py-3 border-b border-white/5">
                                <div className="flex gap-2 items-center bg-white/5 border border-white/20 rounded-xl px-3 py-2">
                                    <input
                                        ref={newInputRef}
                                        value={newTitle}
                                        onChange={e => setNewTitle(e.target.value)}
                                        onKeyDown={e => { if (e.key === 'Escape') { setCreating(false); setNewTitle('') }}}
                                        placeholder="워크스페이스 이름..."
                                        className="flex-1 bg-transparent text-white text-sm outline-none placeholder-white/30"
                                    />
                                    <button type="submit" disabled={!newTitle.trim()}
                                        className="text-blue-400 hover:text-blue-300 disabled:text-white/20 transition-colors">
                                        <Icon.Plus/>
                                    </button>
                                </div>
                                <p className="text-white/25 text-xs mt-1.5 px-1">Enter로 생성, Esc로 취소</p>
                            </form>
                        )}

                        {/* 목록 */}
                        <div className="flex-1 overflow-y-auto">
                            {loading ? (
                                <div className="flex items-center justify-center h-24">
                                    <Icon.Spinner/>
                                </div>
                            ) : workspaces.length === 0 ? (
                                <div className="flex flex-col items-center justify-center h-40 px-6 text-center">
                                    <div className="text-white/10 mb-3">
                                        <Icon.Chart/>
                                    </div>
                                    <p className="text-white/25 text-xs">워크스페이스가 없습니다</p>
                                    <button
                                        onClick={() => setCreating(true)}
                                        className="mt-3 flex items-center gap-1.5 px-3 py-2 rounded-lg bg-blue-500/15 border border-blue-400/25 text-blue-300 hover:bg-blue-500/25 transition-colors text-xs"
                                    >
                                        <Icon.Plus/>
                                        워크스페이스 추가
                                    </button>
                                </div>
                            ) : (
                                workspaces.map(ws => (
                                    <WorkspaceCard
                                        key={ws.id}
                                        workspace={ws}
                                        onSelect={setSelected}
                                        onDelete={handleDelete}
                                        onRename={handleRename}
                                    />
                                ))
                            )}
                        </div>
                    </>
                )}
            </div>
        </div>
    )
}
