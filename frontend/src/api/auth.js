// 토큰을 localStorage에 저장/조회
export const getToken = () => localStorage.getItem('ohara_token')
export const getUser  = () => localStorage.getItem('ohara_user')

export const saveAuth = (token, username) => {
  localStorage.setItem('ohara_token', token)
  localStorage.setItem('ohara_user', username)
}

export const clearAuth = () => {
  localStorage.removeItem('ohara_token')
  localStorage.removeItem('ohara_user')
}

async function post(path, body) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return res.json()
}

export const authApi = {
  register: (username, email, password) =>
    post('/api/auth/register', { username, email, password }),

  login: (username, password) =>
    post('/api/auth/login', { username, password }),

  logout: (token) =>
    fetch('/api/auth/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    }),

  me: (token) =>
    fetch('/api/auth/me', {
      headers: { Authorization: `Bearer ${token}` },
    }).then(r => r.json()),
}
