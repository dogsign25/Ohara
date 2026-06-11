import { useState } from 'react'
import { authApi, saveAuth } from '../api/auth.js'

/** 사용자 로그인을 처리하고 성공 시 상위 인증 상태를 갱신한다. */
export default function Login({ onSuccess, onGoRegister, onGoLanding }) {
  const [form,    setForm]    = useState({ username: '', password: '' })
  const [error,   setError]   = useState('')
  const [loading, setLoading] = useState(false)

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }))

  /** 로그인 폼을 검증한 뒤 인증 API를 호출한다. */
  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await authApi.login(form.username, form.password)
      if (res.token) {
        saveAuth(res.token, res.username)
        onSuccess(res.username)
      } else {
        setError(res.message || '로그인 실패')
      }
    } catch (err) {
      setError(err.message || '서버 연결 오류')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#050810] flex items-center justify-center px-4">
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
          <h1 className="text-white text-2xl font-bold">로그인</h1>
          <p className="text-white/35 text-sm mt-1">인텔리전스 플랫폼에 접속하세요</p>
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
                placeholder="아이디 입력"
                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white text-sm placeholder-white/20 outline-none focus:border-blue-500/60 focus:bg-white/8 transition-all"
                required
              />
            </div>

            <div>
              <label className="text-white/45 text-xs uppercase tracking-widest block mb-2">비밀번호</label>
              <input
                type="password"
                value={form.password}
                onChange={set('password')}
                placeholder="비밀번호 입력"
                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white text-sm placeholder-white/20 outline-none focus:border-blue-500/60 focus:bg-white/8 transition-all"
                required
              />
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
              {loading ? '접속 중...' : '로그인'}
            </button>
          </form>
        </div>

        <p className="text-center text-white/30 text-sm mt-5">
          계정이 없으신가요?{' '}
          <button onClick={onGoRegister} className="text-blue-400 hover:text-blue-300 transition-colors">
            회원가입
          </button>
        </p>
      </div>
    </div>
  )
}
