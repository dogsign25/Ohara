# OHARA

OHARA는 뉴스/URL 문서에서 국가, 기관, 인물 엔티티를 추출하고 Neo4j 그래프로 시각화하는 관계 분석 애플리케이션입니다. Spring Boot 백엔드는 인증, 워크스페이스, MySQL 저장, Neo4j 조회를 담당하고, Python AI Engine은 URL 본문 수집과 NER 분석, 그래프 저장을 담당합니다. React 프론트엔드는 그래프 탐색과 워크스페이스 문서 관리를 제공합니다.

## 실행 방법

1. MySQL과 Neo4j를 실행합니다.
2. 백엔드를 실행합니다.

```bash
cd backend
./gradlew bootRun
```

3. AI Engine을 실행합니다.

```bash
cd ai-engine
uvicorn api:app --port 8001 --reload
```

4. 프론트엔드를 실행합니다.

```bash
cd frontend
npm install
npm run dev
```

5. 브라우저에서 `http://localhost:3000`을 엽니다.

## 주요 설정

`backend/src/main/resources/application.yml`

- `DB_URL`, `DB_USER`, `DB_PASSWORD`: MySQL 연결 정보
- `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`: Neo4j 연결 정보
- `AI_ENGINE_URL`: Spring Boot가 호출할 Python AI Engine 주소

`ai-engine/api.py`

- `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`: AI Engine의 Neo4j 저장 연결 정보
- `CORS_ALLOWED_ORIGINS`: AI Engine CORS 허용 origin

## 요청 흐름

### 회원가입/로그인

1. 프론트엔드가 `/api/auth/register` 또는 `/api/auth/login`으로 요청합니다.
2. `AuthController`가 요청을 받고 `AuthService`에 위임합니다.
3. `AuthService`는 MySQL `users` 테이블에서 계정을 생성하거나 비밀번호를 검증합니다.
4. 성공 시 UUID 토큰을 생성하고 `user_tokens` 테이블에 저장합니다.
5. 동시에 Spring `HttpSession`에도 `username`, `token`을 저장합니다.
6. 새로고침 시 프론트엔드는 `/api/auth/me`를 호출하고, 백엔드는 세션 또는 토큰으로 로그인 상태를 복원합니다.

### 전체 그래프 조회

1. 프론트엔드가 `/api/graph?limit=100&minStrength=1`로 요청합니다.
2. `GraphController`가 `GraphService.getGraph()`를 호출합니다.
3. `GraphService`가 Neo4j에서 `Country`, `Organization`, `Person` 노드와 `RELATED_TO` 관계를 조회합니다.
4. 프론트엔드는 응답을 `react-force-graph-2d`에 넘겨 시각화합니다.

### 워크스페이스 그래프 조회

1. 프론트엔드가 `/api/graph/workspace/{workspaceId}`로 요청합니다.
2. `workspaceId`가 `0`이면 모두가 공유하는 `Default` 워크스페이스로 처리하며 전체 그래프를 반환합니다.
3. 일반 워크스페이스는 `MENTIONED_IN_WORKSPACE {workspaceId}` 관계가 있는 엔티티만 조회합니다.
4. 엣지는 같은 워크스페이스 문서에서 함께 언급된 엔티티 사이만 반환합니다.

### URL 문서 추가 및 분석

1. 프론트엔드가 `/api/workspaces/{id}/documents`로 URL을 POST합니다.
2. `WorkspaceController`가 `WorkspaceService.addUrl()`을 호출합니다.
3. `WorkspaceService`는 MySQL `documents`에 `PENDING` 문서를 저장합니다.
4. 트랜잭션 커밋 후 `DocumentAnalysisService.analyzeUrl()`이 비동기로 실행됩니다.
5. `DocumentAnalysisService`가 Python AI Engine의 `/analyze/url`을 호출합니다.
6. AI Engine은 URL 본문을 수집하고 엔티티를 추출한 뒤 Neo4j에 `Document`, 엔티티 노드, `MENTIONED_IN_WORKSPACE`, `RELATED_TO` 관계를 저장합니다.
7. 백엔드는 분석 결과에 따라 문서 상태를 `DONE` 또는 `ERROR`로 갱신합니다.

### 노드 삭제

1. 프론트엔드 노드 상세 패널에서 삭제 버튼을 누릅니다.
2. `/api/node/{name}` DELETE 요청이 발생합니다.
3. `GraphService.deleteNode()`가 Neo4j에서 해당 엔티티 노드를 `DETACH DELETE`합니다.
4. 프론트엔드는 삭제된 노드와 연결 엣지를 현재 화면에서 제거합니다.

## 데이터 흐름

### MySQL

- `users`: 사용자 계정과 BCrypt 해시 비밀번호
- `user_tokens`: 로그인 토큰과 사용자 연결 정보
- `workspaces`: 사용자별 워크스페이스
- `documents`: 워크스페이스에 추가된 URL/PDF/노트 문서와 분석 상태

### Neo4j

- `Country`, `Organization`, `Person`: 추출된 엔티티 노드
- `Article`: 수집 파이프라인의 기사 노드
- `Document`: 워크스페이스에 사용자가 추가한 문서 노드
- `RELATED_TO`: 엔티티 간 공동 등장 관계
- `MENTIONED_IN`: 엔티티가 일반 기사에 언급됨
- `MENTIONED_IN_WORKSPACE`: 엔티티가 특정 워크스페이스 문서에 언급됨

## Java 파일 역할

### 진입점과 설정

- `OharaApplication.java`: Spring Boot 애플리케이션 진입점이며 `@EnableAsync`로 비동기 분석을 활성화합니다.
- `SecurityConfig.java`: BCrypt 인코더와 Spring Security 필터 체인을 설정합니다. 현재 API는 자체 세션/토큰 인증을 사용하므로 기본 폼 로그인과 HTTP Basic은 끕니다.

### Controller

- `AuthController.java`: 회원가입, 로그인, 로그아웃, 현재 로그인 사용자 조회 API를 제공합니다. 성공 시 서버 세션에 로그인 상태를 저장합니다.
- `GraphController.java`: 전체 그래프, 워크스페이스 그래프, 노드 상세, 검색, 노드 삭제 API를 제공합니다.
- `WorkspaceController.java`: 워크스페이스 목록/생성/수정/삭제, 문서 목록/추가/삭제 API를 제공합니다. `Default` 워크스페이스는 API에서 항상 합성해 반환합니다.

### Service

- `AuthService.java`: 사용자 검증, 비밀번호 해시 검증, 토큰 생성/검증/삭제를 담당합니다.
- `GraphService.java`: Neo4j 쿼리를 수행해 그래프 응답 DTO를 만듭니다.
- `WorkspaceService.java`: 사용자 소유권을 확인하고 MySQL 워크스페이스/문서 데이터를 변경합니다.
- `DocumentAnalysisService.java`: URL 문서 분석을 비동기로 AI Engine에 요청하고 문서 상태를 갱신합니다.

### Entity

- `User.java`: 사용자 계정 엔티티입니다.
- `UserToken.java`: 로그인 토큰 엔티티입니다. `AuthService`가 생성한 토큰을 MySQL에 저장해 서버 재시작 후에도 검증할 수 있게 합니다.
- `Workspace.java`: 사용자별 워크스페이스 엔티티입니다.
- `Document.java`: 워크스페이스 문서와 분석 상태 엔티티입니다.

### Repository

- `UserRepository.java`: 사용자 조회와 중복 검사를 담당합니다.
- `UserTokenRepository.java`: 토큰 저장/조회/삭제를 담당합니다.
- `WorkspaceRepository.java`: 사용자별 워크스페이스 조회와 소유권 확인을 담당합니다.
- `DocumentRepository.java`: 워크스페이스별 문서 목록 조회를 담당합니다.

### DTO

- `AuthDto.java`: 인증 요청/응답 record 모음입니다.
- `GraphDto.java`: 그래프 노드, 엣지, 노드 상세, 기사 응답 record 모음입니다.

## UserToken 관련 주의

`AuthService`는 `UserToken`과 `UserTokenRepository`에 의존합니다. 따라서 아래 두 파일이 반드시 존재해야 합니다.

- `backend/src/main/java/com/ohara/entity/UserToken.java`
- `backend/src/main/java/com/ohara/repository/UserTokenRepository.java`

현재 코드에는 두 파일이 포함되어 있으며 `./gradlew test`로 컴파일 검증됩니다. 만약 실행 중 `UserToken` 관련 오류가 계속 난다면 백엔드 프로세스가 오래된 빌드로 떠 있을 가능성이 높으므로 백엔드를 완전히 종료한 뒤 다시 실행하세요.
