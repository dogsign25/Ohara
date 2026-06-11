import { useState } from 'react'
import { authApi, saveAuth } from '../api/auth.js'

/** 신규 사용자 등록과 가입 후 자동 로그인을 처리한다. */
export default function Register({ onSuccess, onGoLogin, onGoLanding }) {
  const [form,    setForm]    = useState({ username: '', email: '', password: '', confirm: '' })
  const [error,   setError]   = useState('')
  const [loading, setLoading] = useState(false)

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }))

  /** 회원가입 폼을 검증하고 등록 API를 호출한다. */
  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (form.password !== form.confirm) { setError('비밀번호가 일치하지 않습니다.'); return }
    if (form.password.length < 6)       { setError('비밀번호는 6자 이상이어야 합니다.'); return }
    setLoading(true)
    try {
      const res = await authApi.register(form.username, form.email, form.password)
      if (res.token) {
        saveAuth(res.token, res.username)
        onSuccess(res.username)
      } else {
        setError(res.message || '회원가입 실패')
      }
    } catch (err) {
      setError(err.message || '서버 연결 오류')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#050810] flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">

        {/* 뒤로가기 */}
        <button
          onClick={onGoLanding}
          className="flex items-center gap-1.5 text-white/30 hover:text-white/60 text-sm mb-8 transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7"/>
          </svg>
          홈으로
        </button>

        {/* 로고 */}
        <div className="mb-10">
          <div className="flex items-center gap-2 mb-3">
            <svg width="20" height="20" viewBox="0 0 22 22" fill="none">
              <circle cx="11" cy="11" r="3" fill="#60a5fa"/>
              <circle cx="4"  cy="4"  r="2" fill="#c084fc" opacity="0.8"/>
              <circle cx="18" cy="5"  r="2" fill="#fbbf24" opacity="0.8"/>
              <circle cx="4"  cy="18" r="2" fill="#fbbf24" opacity="0.8"/>
              <circle cx="18" cy="17" r="2" fill="#c084fc" opacity="0.8"/>
              <line x1="11" y1="11" x2="4"  y2="4"  stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
              <line x1="11" y1="11" x2="18" y2="5"  stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
              <line x1="11" y1="11" x2="4"  y2="18" stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
              <line x1="11" y1="11" x2="18" y2="17" stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
            </svg>
            <span className="text-white font-bold tracking-widest text-sm uppercase">OHARA</span>
          </div>
          <h1 className="text-white text-2xl font-bold">회원가입</h1>
          <p className="text-white/35 text-sm mt-1">무료로 시작하세요</p>
        </div>

        {/* 폼 */}
        <div className="bg-white/3 border border-white/8 rounded-2xl p-7">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-white/45 text-xs uppercase tracking-widest block mb-2">아이디</label>
              <input
                type="text"
                value={form.username}
                onChange={set('username')}
                placeholder="영문, 숫자 (3~50자)"
                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white text-sm placeholder-white/20 outline-none focus:border-blue-500/60 focus:bg-white/8 transition-all"
                required
              />
            </div>

            <div>
              <label className="text-white/45 text-xs uppercase tracking-widest block mb-2">이메일</label>
              <input
                type="email"
                value={form.email}
                onChange={set('email')}
                placeholder="example@email.com"
                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white text-sm placeholder-white/20 outline-none focus:border-blue-500/60 focus:bg-white/8 transition-all"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-white/45 text-xs uppercase tracking-widest block mb-2">비밀번호</label>
                <input
                  type="password"
                  value={form.password}
                  onChange={set('password')}
                  placeholder="6자 이상"
                  className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white text-sm placeholder-white/20 outline-none focus:border-blue-500/60 focus:bg-white/8 transition-all"
                  required
                />
              </div>
              <div>
                <label className="text-white/45 text-xs uppercase tracking-widest block mb-2">확인</label>
                <input
                  type="password"
                  value={form.confirm}
                  onChange={set('confirm')}
                  placeholder="재입력"
                  className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white text-sm placeholder-white/20 outline-none focus:border-blue-500/60 focus:bg-white/8 transition-all"
                  required
                />
              </div>
            </div>

            {error && (
              <div className="bg-red-500/8 border border-red-500/20 rounded-xl px-4 py-3 text-red-400 text-sm">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-500 hover:bg-blue-400 disabled:bg-blue-900 disabled:text-blue-700 text-white font-semibold py-3 rounded-xl transition-all mt-2 text-sm"
            >
              {loading ? '처리 중...' : '계정 만들기'}
            </button>
          </form>
        </div>

        <p className="text-center text-white/30 text-sm mt-5">
          이미 계정이 있으신가요?{' '}
          <button onClick={onGoLogin} className="text-blue-400 hover:text-blue-300 transition-colors">
            로그인
          </button>
        </p>
      </div>
    </div>
  )
}
