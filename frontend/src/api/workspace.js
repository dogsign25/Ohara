// src/api/workspace.js
// 워크스페이스 관련 API (서버 기반)

import { getToken } from './auth.js'
import { apiError, http, responseData } from './http.js'

/** 현재 로그인 토큰을 워크스페이스 요청 헤더로 만든다. */
function authHeaders() {
    return {
        'Authorization': `Bearer ${getToken()}`,
    }
}

/** multipart 요청에 사용할 Bearer 인증 헤더를 만든다. */
function bearerHeaders() {
    return {
        'Authorization': `Bearer ${getToken()}`,
    }
}

/** 워크스페이스 API 요청에 인증 헤더와 공통 오류 처리를 적용한다. */
async function req(method, path, body) {
    try {
        const res = await http.request({
            method,
            url: path.replace(/^\/api/, ''),
            headers: authHeaders(),
            data: body,
        })
        return responseData(res)
    } catch (error) {
        throw apiError(error)
    }
}

export const workspaceApi = {
    // 워크스페이스 목록
    list: () =>
        req('GET', '/api/workspaces'),

    // 생성
    create: (title, description = '') =>
        req('POST', '/api/workspaces', { title, description }),

    // 삭제
    delete: (id) =>
        req('DELETE', `/api/workspaces/${id}`),

    // 이름 변경
    rename: (id, title) =>
        req('PATCH', `/api/workspaces/${id}`, { title }),

    // 문서 목록
    listDocuments: (workspaceId) =>
        req('GET', `/api/workspaces/${workspaceId}/documents`),

    // URL 추가 + 분석
    addUrl: (workspaceId, url) =>
        req('POST', `/api/workspaces/${workspaceId}/documents`, { url }),

    addText: (workspaceId, title, text) =>
        req('POST', `/api/workspaces/${workspaceId}/documents/text`, { title, text }),

    addFile: async (workspaceId, file) => {
        const form = new FormData()
        form.append('file', file)
        try {
            const res = await http.post(`/workspaces/${workspaceId}/documents/file`, form, {
                headers: bearerHeaders(),
            })
            return responseData(res)
        } catch (error) {
            throw apiError(error)
        }
    },

    // 문서 삭제
    deleteDocument: (workspaceId, docId) =>
        req('DELETE', `/api/workspaces/${workspaceId}/documents/${docId}`),
}
