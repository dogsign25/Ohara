import { useEffect, useRef, useState } from 'react'

const STORAGE_KEY = 'ohara_workspace_v1'

function genId() {
  return Math.random().toString(36).slice(2, 9)
}

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function save(tables) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tables))
}

/* ── 행 컴포넌트 ───────────────────────────────────────────────── */
function TableRow({ row, onDelete, onUpdateNote }) {
  const [editing, setEditing] = useState(false)
  const [note, setNote]       = useState(row.note ?? '')
  const inputRef              = useRef()

  function commit() {
    setEditing(false)
    onUpdateNote(row.id, note)
  }

  return (
    <div className="group flex items-start gap-2 px-3 py-2 rounded-xl hover:bg-white/5 transition-colors">
      {/* 컬러 도트 */}
      <span className="mt-1.5 w-1.5 h-1.5 rounded-full shrink-0"
        style={{ backgroundColor: row.color ?? '#9ca3af' }} />

      <div className="flex-1 min-w-0">
        <p className="text-white/80 text-xs font-medium truncate">{row.label}</p>
        {editing ? (
          <input
            ref={inputRef}
            value={note}
            onChange={e => setNote(e.target.value)}
            onBlur={commit}
            onKeyDown={e => { if (e.key === 'Enter') commit() }}
            className="mt-0.5 w-full bg-white/10 border border-white/15 rounded-lg px-2 py-1 text-xs text-white/70 outline-none"
            placeholder="메모 입력..."
            autoFocus
          />
        ) : (
          <p
            onClick={() => setEditing(true)}
            className="mt-0.5 text-white/35 text-xs cursor-text hover:text-white/55 transition-colors truncate min-h-[14px]"
          >
            {note || <span className="italic">메모 추가…</span>}
          </p>
        )}
      </div>

      {/* 삭제 */}
      <button
        onClick={() => onDelete(row.id)}
        className="opacity-0 group-hover:opacity-100 transition-opacity text-white/30 hover:text-red-400 mt-0.5"
      >
        <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12"/>
        </svg>
      </button>
    </div>
  )
}

/* ── 테이블 카드 컴포넌트 ─────────────────────────────────────── */
const NODE_COLOR = {
  Country:      '#60a5fa',
  Organization: '#fbbf24',
  Person:       '#c084fc',
}

function TableCard({ table, selectedNode, selectedNodeType, onDelete, onUpdate }) {
  const [editingTitle, setEditingTitle] = useState(false)
  const [title, setTitle]               = useState(table.title)
  const [newLabel, setNewLabel]         = useState('')
  const [showAdd, setShowAdd]           = useState(false)
  const titleInputRef                   = useRef()

  function commitTitle() {
    setEditingTitle(false)
    if (title.trim()) onUpdate({ ...table, title: title.trim() })
    else setTitle(table.title)
  }

  function addRow(label, type) {
    if (!label.trim()) return
    const row = {
      id:    genId(),
      label: label.trim(),
      color: NODE_COLOR[type] ?? '#9ca3af',
      note:  '',
    }
    onUpdate({ ...table, rows: [...table.rows, row] })
  }

  function addManual() {
    if (!newLabel.trim()) return
    addRow(newLabel, null)
    setNewLabel('')
    setShowAdd(false)
  }

  function addSelectedNode() {
    if (!selectedNode) return
    addRow(selectedNode, selectedNodeType)
  }

  function deleteRow(rowId) {
    onUpdate({ ...table, rows: table.rows.filter(r => r.id !== rowId) })
  }

  function updateNote(rowId, note) {
    onUpdate({ ...table, rows: table.rows.map(r => r.id === rowId ? { ...r, note } : r) })
  }

  return (
    <div className="rounded-2xl border border-white/8 bg-white/4 overflow-hidden">
      {/* 카드 헤더 */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-white/8">
        {editingTitle ? (
          <input
            ref={titleInputRef}
            value={title}
            onChange={e => setTitle(e.target.value)}
            onBlur={commitTitle}
            onKeyDown={e => { if (e.key === 'Enter') commitTitle() }}
            className="flex-1 bg-transparent text-white text-sm font-semibold outline-none border-b border-blue-400/50"
            autoFocus
          />
        ) : (
          <h3
            onDoubleClick={() => setEditingTitle(true)}
            className="text-white text-sm font-semibold cursor-pointer hover:text-white/80 transition-colors flex-1 truncate"
            title="더블클릭으로 편집"
          >
            {table.title}
          </h3>
        )}

        <div className="flex items-center gap-1 ml-2 shrink-0">
          {/* 행 수 뱃지 */}
          <span className="text-white/30 text-xs">{table.rows.length}</span>

          {/* 삭제 */}
          <button
            onClick={() => onDelete(table.id)}
            className="text-white/25 hover:text-red-400 transition-colors ml-1"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
            </svg>
          </button>
        </div>
      </div>

      {/* 행 목록 */}
      <div className="max-h-52 overflow-y-auto py-1">
        {table.rows.length === 0 ? (
          <p className="text-white/20 text-xs text-center py-4 italic">행이 없습니다</p>
        ) : (
          table.rows.map(row => (
            <TableRow
              key={row.id}
              row={row}
              onDelete={deleteRow}
              onUpdateNote={updateNote}
            />
          ))
        )}
      </div>

      {/* 액션 영역 */}
      <div className="border-t border-white/8 px-3 py-2 flex flex-col gap-1.5">
        {/* 선택된 노드 추가 */}
        {selectedNode && (
          <button
            onClick={addSelectedNode}
            className="flex items-center gap-2 w-full px-3 py-1.5 rounded-xl bg-blue-500/15 border border-blue-400/25 text-blue-300 text-xs hover:bg-blue-500/25 transition-all"
          >
            <svg className="w-3 h-3 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4"/>
            </svg>
            <span className="truncate max-w-[180px]">"{selectedNode}" 추가</span>
          </button>
        )}

        {/* 직접 입력 */}
        {showAdd ? (
          <div className="flex gap-1.5">
            <input
              value={newLabel}
              onChange={e => setNewLabel(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') addManual()
                if (e.key === 'Escape') { setShowAdd(false); setNewLabel('') }
              }}
              className="flex-1 bg-white/8 border border-white/15 rounded-lg px-2 py-1 text-xs text-white outline-none placeholder-white/25"
              placeholder="항목 이름 입력..."
              autoFocus
            />
            <button
              onClick={addManual}
              className="px-2 py-1 rounded-lg bg-white/10 text-white/60 hover:bg-white/15 text-xs transition-colors"
            >추가</button>
            <button
              onClick={() => { setShowAdd(false); setNewLabel('') }}
              className="px-2 py-1 rounded-lg text-white/30 hover:text-white/60 text-xs transition-colors"
            >취소</button>
          </div>
        ) : (
          <button
            onClick={() => setShowAdd(true)}
            className="flex items-center gap-1.5 w-full px-3 py-1.5 rounded-xl border border-dashed border-white/15 text-white/35 text-xs hover:text-white/60 hover:border-white/25 transition-all"
          >
            <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4"/>
            </svg>
            직접 입력으로 추가
          </button>
        )}
      </div>
    </div>
  )
}

/* ── 메인 워크스페이스 패널 ───────────────────────────────────── */
export default function WorkspacePanel({ show, selectedNode, selectedNodeType }) {
  const [tables, setTables] = useState(load)

  // localStorage 동기화
  useEffect(() => { save(tables) }, [tables])

  function addTable() {
    setTables(prev => [...prev, { id: genId(), title: '새 연구 주제', rows: [] }])
  }

  function deleteTable(id) {
    setTables(prev => prev.filter(t => t.id !== id))
  }

  function updateTable(updated) {
    setTables(prev => prev.map(t => t.id === updated.id ? updated : t))
  }

  return (
    <div
      className={`
        absolute left-0 top-0 bottom-0 z-20 flex flex-col
        transition-all duration-300 ease-in-out
        ${show ? 'translate-x-0' : '-translate-x-full'}
      `}
      style={{ width: '340px' }}
    >
      {/* 패널 배경 */}
      <div className="flex-1 bg-gray-900/90 backdrop-blur-xl border-r border-white/10 flex flex-col overflow-hidden">

        {/* 헤더 */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/10 shrink-0">
          <div className="flex items-center gap-2.5">
            <svg className="w-4 h-4 text-white/50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            <span className="text-white/70 text-sm font-semibold tracking-wide">워크스페이스</span>
          </div>
          <span className="text-white/25 text-xs">{tables.length}개 테이블</span>
        </div>

        {/* 선택된 노드 배너 */}
        {selectedNode && (
          <div className="mx-4 mt-3 px-3 py-2 rounded-xl bg-white/5 border border-white/10 flex items-center gap-2 shrink-0">
            <span
              className="w-2 h-2 rounded-full shrink-0"
              style={{ backgroundColor: NODE_COLOR[selectedNodeType] ?? '#9ca3af' }}
            />
            <span className="text-white/60 text-xs truncate">선택됨: <span className="text-white/85 font-medium">{selectedNode}</span></span>
          </div>
        )}

        {/* 테이블 목록 */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
          {tables.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-40 text-center">
              <svg className="w-8 h-8 text-white/15 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                  d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
              </svg>
              <p className="text-white/25 text-xs">아직 테이블이 없습니다</p>
              <p className="text-white/15 text-xs mt-1">아래 버튼으로 첫 연구 주제를 만들어보세요</p>
            </div>
          ) : (
            tables.map(table => (
              <TableCard
                key={table.id}
                table={table}
                selectedNode={selectedNode}
                selectedNodeType={selectedNodeType}
                onDelete={deleteTable}
                onUpdate={updateTable}
              />
            ))
          )}
        </div>

        {/* 새 테이블 추가 버튼 */}
        <div className="px-4 py-3 border-t border-white/10 shrink-0">
          <button
            onClick={addTable}
            className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl border border-dashed border-white/20 text-white/45 text-sm hover:text-white/70 hover:border-white/35 hover:bg-white/5 transition-all"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4"/>
            </svg>
            새 연구 테이블
          </button>
        </div>
      </div>
    </div>
  )
}
