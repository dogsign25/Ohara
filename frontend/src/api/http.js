import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

/** Axios 오류에서 서버 메시지와 상태 코드를 읽어 표준 Error로 변환한다. */
export function apiError(error, fallback = 'API 오류') {
  const status = error.response?.status
  const message = error.response?.data?.message
  return new Error(message || (status ? `${fallback} ${status}` : fallback))
}

/** 본문이 없는 성공 응답을 빈 객체로 통일한다. */
export function responseData(response) {
  return response.data === '' ? {} : response.data
}
