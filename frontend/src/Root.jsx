import { useState, useEffect } from 'react'
import { getToken, getUser, saveAuth, clearAuth, authApi } from './api/auth.js'
import Landing from './pages/Landing.jsx'
import Login from './components/Login.jsx'
import Register from './components/Register.jsx'
import App from './App.jsx'

// page: 'landing' | 'login' | 'register' | 'graph'
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
        if (!saved) clearAuth()
      })
      .finally(() => setReady(true))
  }, [])

  function handleLoginSuccess(username) {
    setUser(username)
    setPage('graph')
  }

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
