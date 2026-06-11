import { useState, useEffect } from 'react'
import { getToken, getUser, saveAuth, clearAuth, authApi } from './api/auth.js'
import Landing from './pages/Landing.jsx'
import Login from './components/Login.jsx'
import Register from './components/Register.jsx'
import App from './App.jsx'

// page: 'landing' | 'login' | 'register' | 'graph'
/** 저장된 인증 상태를 복원하고 로그인 전후 최상위 화면을 전환한다. */
export default function Root() {
  const [page, setPage]   = useState('landing')
  const [user, setUser]   = useState(null)
  const [ready, setReady] = useState(false)

  // 앱 시작 시 저장된 토큰 검증
  useEffect(() => {
    const token = getToken()
    const saved = getUser()
    if (saved) {
      setUser(saved)
      setPage('graph')
    }

    if (!token) {
      clearAuth()
      setUser(null)
      setPage('landing')
      setReady(true)
      return
    }

    authApi.me(token)
      .then(res => {
        if (res.username) {
          saveAuth(res.token, res.username)
          setUser(res.username)
          setPage('graph')
        } else if (!saved) {
          clearAuth()
          setUser(null)
          setPage('landing')
        }
      })
      .catch(() => {
        clearAuth()
        setUser(null)
        setPage('landing')
      })
      .finally(() => setReady(true))
  }, [])

  /** 로그인·회원가입 성공 사용자를 애플리케이션 화면으로 보낸다. */
  function handleLoginSuccess(username) {
    setUser(username)
    setPage('graph')
  }

  /** 서버 로그아웃을 요청하고 브라우저 인증 정보를 제거한다. */
  function handleLogout() {
    const token = getToken()
    if (token) authApi.logout(token).catch(() => {})
    clearAuth()
    setUser(null)
    setPage('landing')
  }

  if (!ready) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center">
        <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
      </div>
    )
  }

  if (page === 'graph') {
    return <App user={user} onLogout={handleLogout} />
  }

  if (page === 'login') {
    return (
      <Login
        onSuccess={handleLoginSuccess}
        onGoRegister={() => setPage('register')}
        onGoLanding={() => setPage('landing')}
      />
    )
  }

  if (page === 'register') {
    return (
      <Register
        onSuccess={handleLoginSuccess}
        onGoLogin={() => setPage('login')}
        onGoLanding={() => setPage('landing')}
      />
    )
  }

  return (
    <Landing
      onLogin={() => setPage('login')}
      onRegister={() => setPage('register')}
    />
  )
}
