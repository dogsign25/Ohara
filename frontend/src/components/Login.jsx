import { useState } from 'react'
import { authApi, saveAuth } from '../api/auth.js'

export default function Login({ onSuccess, onGoRegister }) {
  const [form,    setForm]    = useState({ username: '', password: '' })
  const [error,   setError]   = useState('')
  const [loading, setLoading] = useState(false)

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }))

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
    } catch {
      setError('서버 연결 오류')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
      <div className="w-full max-w-md">

        {/* 로고 */}
        <div className="text-center mb-10">
          <h1 className="text-5xl font-bold text-white tracking-tight mb-2">OHARA</h1>
          <p className="text-blue-400 text-sm">Global Relationship Intelligence</p>
        </div>

        {/* 카드 */}
        <div className="bg-gray-900 border border-white/10 rounded-2xl p-8">
          <h2 className="text-white text-xl font-semibold mb-6">로그인</h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-white/50 text-sm block mb-1.5">아이디</label>
              <input
                type="text"
                value={form.username}
                onChange={set('username')}
                placeholder="아이디 입력"
                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder-white/20 outline-none focus:border-blue-500 transition-colors"
                required
              />
            </div>

            <div>
              <label className="text-white/50 text-sm block mb-1.5">비밀번호</label>
              <input
                type="password"
                value={form.password}
                onChange={set('password')}
                placeholder="비밀번호 입력"
                className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder-white/20 outline-none focus:border-blue-500 transition-colors"
                required
              />
            </div>

            {error && (
              <div className="bg-red-500/10 border border-red-500/30 rounded-xl px-4 py-3 text-red-400 text-sm">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-blue-900 disabled:text-blue-700 text-white font-semibold py-3 rounded-xl transition-colors mt-2"
            >
              {loading ? '로그인 중...' : '로그인'}
            </button>
          </form>

          <p className="text-center text-white/40 text-sm mt-6">
            계정이 없으신가요?{' '}
            <button onClick={onGoRegister} className="text-blue-400 hover:text-blue-300 transition-colors">
              회원가입
            </button>
          </p>
        </div>
      </div>
    </div>
  )
}
