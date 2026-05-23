// src/api/workspace.js
// 워크스페이스 관련 API (서버 기반)

import { getToken } from './auth.js'

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${getToken()}`,
    }
}

async function req(method, path, body) {
    const res = await fetch(path, {
        method,
        headers: authHeaders(),
        body: body ? JSON.stringify(body) : undefined,
    })
    if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.message || `API 오류 ${res.status}`)
    }
    return res.json()
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

    // 문서 삭제
    deleteDocument: (workspaceId, docId) =>
        req('DELETE', `/api/workspaces/${workspaceId}/documents/${docId}`),
}