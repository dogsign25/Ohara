// 토큰을 브라우저 세션에 저장/조회
export const getToken = () => sessionStorage.getItem('ohara_token') || localStorage.getItem('ohara_token')
export const getUser  = () => sessionStorage.getItem('ohara_user') || localStorage.getItem('ohara_user')

export const saveAuth = (token, username) => {
  sessionStorage.setItem('ohara_token', token)
  sessionStorage.setItem('ohara_user', username)
  localStorage.removeItem('ohara_token')
  localStorage.removeItem('ohara_user')
}

export const clearAuth = () => {
  sessionStorage.removeItem('ohara_token')
  sessionStorage.removeItem('ohara_user')
  localStorage.removeItem('ohara_token')
  localStorage.removeItem('ohara_user')
}

async function post(path, body) {
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.message || `API 오류 ${res.status}`)
  return data
}

export const authApi = {
  register: (username, email, password) =>
    post('/api/auth/register', { username, email, password }),

  login: (username, password) =>
    post('/api/auth/login', { username, password }),

  logout: (token) =>
    fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers: { Authorization: `Bearer ${token}` },
    }),

  me: (token) =>
    fetch('/api/auth/me', {
      credentials: 'include',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }).then(r => r.json()),
}
