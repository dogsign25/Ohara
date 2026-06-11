// 토큰을 브라우저 세션에 저장/조회
import { apiError, http, responseData } from './http.js'

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

/** 인증 API 응답을 변환하고 서버 오류를 사용자용 Error로 바꾼다. */
async function request(call) {
  try {
    const res = await call()
    return responseData(res)
  } catch (error) {
    throw apiError(error)
  }
}

export const authApi = {
  register: (username, email, password) =>
    request(() => http.post('/auth/register', { username, email, password })),

  login: (username, password) =>
    request(() => http.post('/auth/login', { username, password })),

  logout: (token) =>
    http.post('/auth/logout', undefined, {
      headers: { Authorization: `Bearer ${token}` },
    }).then(responseData),

  me: (token) =>
    http.get('/auth/me', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }).then(responseData),
}
