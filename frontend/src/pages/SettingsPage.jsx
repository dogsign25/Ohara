import { useEffect, useState } from 'react'

const STORAGE_KEY = 'ohara:settings'

const DEFAULTS = {
  defaultLimit: 100,
  defaultDays: '',
  autoOpenWorkspace: false,
  densePanels: false,
}

/** 브라우저에 저장되는 그래프 표시 설정을 관리한다. */
export default function SettingsPage() {
  const [settings, setSettings] = useState(DEFAULTS)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    try {
      setSettings({ ...DEFAULTS, ...JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') })
    } catch {
      setSettings(DEFAULTS)
    }
  }, [])

  /** 설정 객체의 특정 항목만 변경한다. */
  function update(key, value) {
    setSaved(false)
    setSettings(prev => ({ ...prev, [key]: value }))
  }

  /** 현재 설정을 localStorage에 저장하고 완료 상태를 표시한다. */
  function save() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    setSaved(true)
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <p className="text-xs uppercase tracking-widest text-blue-300/70">Settings</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">설정</h1>
        <p className="mt-2 text-sm text-white/40">브라우저에 저장되는 개인 기본값입니다.</p>
      </div>

      <section className="rounded-lg border border-white/10 bg-white/[0.03]">
        <div className="border-b border-white/10 px-4 py-3">
          <h2 className="text-sm font-medium text-white/85">그래프 기본값</h2>
        </div>
        <div className="space-y-4 p-4">
          <label className="block">
            <span className="text-xs text-white/40">기본 노드 수</span>
            <input
              type="number"
              min="20"
              max="500"
              value={settings.defaultLimit}
              onChange={e => update('defaultLimit', Number(e.target.value))}
              className="mt-2 w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white outline-none"
            />
          </label>

          <label className="block">
            <span className="text-xs text-white/40">기본 기간</span>
            <select
              value={settings.defaultDays}
              onChange={e => update('defaultDays', e.target.value)}
              className="ohara-select mt-2 w-full rounded-lg border border-white/10 bg-gray-950 px-3 py-2 text-sm text-white outline-none"
            >
              <option value="">전체 기간</option>
              <option value="1">최근 1일</option>
              <option value="7">최근 7일</option>
              <option value="30">최근 30일</option>
              <option value="90">최근 90일</option>
            </select>
          </label>
        </div>
      </section>

      <section className="rounded-lg border border-white/10 bg-white/[0.03]">
        <div className="border-b border-white/10 px-4 py-3">
          <h2 className="text-sm font-medium text-white/85">인터페이스</h2>
        </div>
        <div className="divide-y divide-white/5">
          <label className="flex items-center justify-between gap-4 px-4 py-3">
            <span>
              <span className="block text-sm text-white/75">워크스페이스 패널 우선 열기</span>
              <span className="text-xs text-white/30">그래프 화면 진입 시 문서 작업을 먼저 볼 때 사용합니다.</span>
            </span>
            <input
              type="checkbox"
              checked={settings.autoOpenWorkspace}
              onChange={e => update('autoOpenWorkspace', e.target.checked)}
              className="h-4 w-4"
            />
          </label>
          <label className="flex items-center justify-between gap-4 px-4 py-3">
            <span>
              <span className="block text-sm text-white/75">패널 밀도 높이기</span>
              <span className="text-xs text-white/30">작은 화면에서 더 많은 행을 보여주기 위한 옵션입니다.</span>
            </span>
            <input
              type="checkbox"
              checked={settings.densePanels}
              onChange={e => update('densePanels', e.target.checked)}
              className="h-4 w-4"
            />
          </label>
        </div>
      </section>

      <div className="flex items-center gap-3">
        <button
          onClick={save}
          className="rounded-lg border border-blue-400/25 bg-blue-500/15 px-4 py-2 text-sm text-blue-200 transition-colors hover:bg-blue-500/25"
        >
          저장
        </button>
        {saved && <span className="text-sm text-emerald-300">저장됨</span>}
      </div>
    </div>
  )
}
