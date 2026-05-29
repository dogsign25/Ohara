# OHARA 기말고사 코드 설명 가이드

이 문서는 교수님이 프로젝트 코드를 보면서 질문할 수 있는 내용을 대비하기 위한 설명서입니다. 단순 사용법보다 "왜 이렇게 구현했는가", "요청이 어떤 파일을 지나가는가", "각 코드가 어떤 책임을 갖는가"에 초점을 맞췄습니다.

## 1. 프로젝트 한 줄 설명

OHARA는 뉴스 기사나 사용자가 추가한 URL 문서에서 국가, 기관, 인물 엔티티를 추출하고, 엔티티 간 공동 등장 관계를 Neo4j 그래프로 저장한 뒤 React에서 인터랙티브 관계망으로 시각화하는 애플리케이션입니다.

## 2. 전체 아키텍처

프로젝트는 세 부분으로 나뉩니다.

- `frontend`: React, Vite, Tailwind 기반 화면입니다. 로그인, 그래프 시각화, 검색, 필터, 워크스페이스 문서 관리를 담당합니다.
- `backend`: Spring Boot 기반 API 서버입니다. 인증, MySQL 데이터 관리, Neo4j 그래프 조회, Python AI Engine 호출을 담당합니다.
- `ai-engine`: FastAPI와 Python NLP 파이프라인입니다. URL 본문 수집, spaCy NER, 엔티티 정규화, Neo4j 그래프 저장을 담당합니다.

데이터 저장소는 두 종류를 사용합니다.

- MySQL: 사용자, 로그인 토큰, 워크스페이스, 문서 상태처럼 관계형 업무 데이터를 저장합니다.
- Neo4j: 국가, 기관, 인물 노드와 `RELATED_TO`, `MENTIONED_IN`, `MENTIONED_IN_WORKSPACE` 같은 그래프 관계를 저장합니다.

교수님께 설명할 때는 이렇게 말하면 됩니다.

> MySQL은 사용자와 문서 상태처럼 정형 데이터를 관리하고, Neo4j는 엔티티 관계처럼 연결 탐색이 중요한 데이터를 관리하도록 역할을 분리했습니다.

## 3. 주요 실행 흐름

### 3.1 로그인/회원가입 흐름

1. React의 `Login.jsx` 또는 `Register.jsx`에서 `/api/auth/login`, `/api/auth/register`로 요청합니다.
2. `AuthController`가 요청을 받고 `AuthService`로 전달합니다.
3. `AuthService`는 `UserRepository`로 사용자를 조회하거나 생성합니다.
4. 비밀번호는 `BCryptPasswordEncoder`로 해시 저장하고, 로그인 시 `matches()`로 검증합니다.
5. 성공하면 UUID 토큰을 생성해 `user_tokens` 테이블에 저장합니다.
6. `AuthController`는 응답 토큰을 `HttpSession`에도 저장합니다.
7. 프론트엔드는 토큰과 username을 `sessionStorage`에 저장합니다.

핵심 답변:

> 비밀번호는 평문 저장하지 않고 BCrypt 해시로 저장합니다. 인증 상태는 서버 세션과 Bearer 토큰을 함께 사용해서 새로고침이나 세션 복원 상황에 대응했습니다.

### 3.2 전체 그래프 조회 흐름

1. `App.jsx`가 `api.getGraph(limit, minStrength)`를 호출합니다.
2. `client.js`가 `/api/graph?limit=...&minStrength=...`로 요청합니다.
3. `GraphController.getGraph()`가 요청 파라미터를 검증하고 `GraphService.getGraph()`를 호출합니다.
4. `GraphService`가 Neo4j에서 `Country`, `Organization`, `Person` 노드를 조회합니다.
5. 연결 수가 높은 노드를 먼저 고르고, 선택된 노드 사이의 `RELATED_TO` 관계만 조회합니다.
6. React는 응답을 `react-force-graph-2d` 형식의 `nodes`, `links`로 변환해 렌더링합니다.

핵심 답변:

> 전체 노드를 무조건 가져오면 화면과 DB가 느려지므로, 먼저 degree가 높은 노드를 limit만큼 뽑고 그 노드들 사이의 관계만 다시 조회했습니다.

### 3.3 워크스페이스 URL 분석 흐름

1. 사용자가 `WorkspacePanel.jsx`에서 URL을 추가합니다.
2. `workspaceApi.addUrl()`이 `/api/workspaces/{id}/documents`로 POST 요청합니다.
3. `WorkspaceController.addDoc()`이 `WorkspaceService.addUrl()`을 호출합니다.
4. `WorkspaceService`는 MySQL `documents`에 `PENDING` 상태 문서를 저장합니다.
5. 트랜잭션 커밋 후 `DocumentAnalysisService.analyzeUrl()`을 비동기로 실행합니다.
6. `DocumentAnalysisService`는 Python AI Engine의 `/analyze/url`을 호출합니다.
7. `api.py`는 URL 본문을 수집하고 `extract_batch()`로 엔티티와 관계를 추출합니다.
8. `GraphWriter.write_workspace_document()`가 Neo4j에 `Document`, 엔티티, 워크스페이스 관계를 저장합니다.
9. 백엔드는 분석 성공 시 문서 상태를 `DONE`, 실패 시 `ERROR`로 바꿉니다.

핵심 답변:

> URL 추가 API는 사용자 응답을 오래 막지 않도록 문서를 먼저 저장하고, 실제 분석은 `@Async` 비동기로 처리했습니다. 또 커밋 후 분석을 시작해서 비동기 작업이 아직 저장되지 않은 문서를 조회하는 문제를 피했습니다.

### 3.4 텍스트/PDF 문서 분석 흐름

1. 사용자가 `WorkspacePanel.jsx`에서 문서 추가 모드를 URL, 텍스트, 파일 중 선택합니다.
2. 텍스트 입력은 `/api/workspaces/{id}/documents/text`로 전송됩니다.
3. PDF/TXT 파일은 multipart form으로 `/api/workspaces/{id}/documents/file`에 업로드됩니다.
4. `WorkspaceService.addText()` 또는 `addFile()`이 MySQL `documents`에 `PENDING` 문서를 저장합니다.
5. PDF는 백엔드에서 Apache PDFBox로 텍스트를 추출하고, TXT/MD는 UTF-8 문자열로 읽습니다.
6. 트랜잭션 커밋 후 `DocumentAnalysisService.analyzeText()`가 AI Engine의 `/analyze/text`를 비동기로 호출합니다.
7. AI Engine은 텍스트에서 엔티티와 관계를 추출하고 Neo4j에 `Document`, 엔티티, 관계를 저장합니다.
8. 백엔드는 분석 성공 시 문서 상태를 `DONE`, 실패 시 `ERROR`로 갱신합니다.

핵심 답변:

> URL, 직접 입력 텍스트, PDF 업로드가 모두 최종적으로 같은 AI 분석 파이프라인과 `GraphWriter`를 사용합니다. 입력 방식은 다르지만 Neo4j 저장 구조는 일관되게 유지했습니다.

### 3.5 경로 찾기 흐름

1. 사용자가 탐색 패널에서 출발 노드와 도착 노드를 입력합니다.
2. `App.jsx`가 `/api/path?from=...&to=...`를 호출합니다.
3. `GraphController.findPath()`가 `GraphService.findPath()`로 전달합니다.
4. `GraphService`는 Neo4j `shortestPath()`로 `RELATED_TO` 관계 기반 최단 경로를 찾습니다.
5. 워크스페이스 그래프일 경우 해당 `workspaceId`가 포함된 관계만 경로 후보로 사용합니다.
6. 프론트는 반환된 노드와 엣지를 초록색으로 강조 표시합니다.

핵심 답변:

> Neo4j를 사용한 장점이 경로 찾기에서 잘 드러납니다. 두 엔티티가 직접 연결되어 있지 않아도 중간 인물이나 기관을 통해 어떤 관계망으로 이어지는지 탐색할 수 있습니다.

### 3.6 엣지 출처 보기 흐름

1. 사용자가 그래프의 관계선을 클릭합니다.
2. `App.jsx`가 `/api/edge/sources?source=...&target=...`를 호출합니다.
3. `GraphService.getEdgeSources()`는 `RELATED_TO.sourceKeys` 또는 `workspaceDocKeys`를 확인합니다.
4. 전체 그래프에서는 `Article` 노드를, 워크스페이스 그래프에서는 `Document` 노드를 찾아 반환합니다.
5. 프론트는 오른쪽 패널에 관계를 만든 기사/문서 출처를 표시합니다.

핵심 답변:

> 단순히 관계 강도만 보여주면 왜 연결됐는지 알기 어렵습니다. 그래서 관계가 만들어진 출처 기사나 문서를 보여줘 그래프 해석의 신뢰성을 높였습니다.

### 3.7 시간 필터 흐름

1. 사용자가 상단에서 전체 기간, 최근 1일, 7일, 30일, 90일 중 하나를 선택합니다.
2. 프론트는 그래프 API 호출 시 `days` 파라미터를 함께 보냅니다.
3. 백엔드는 `RELATED_TO.lastMentioned`가 기준 시각 이후인 관계만 조회합니다.
4. 노드 degree 계산도 같은 시간 필터를 반영해 최근 관계가 있는 노드 중심으로 그래프를 구성합니다.

핵심 답변:

> 뉴스 데이터는 시간성이 중요하므로 최근 관계만 보고 싶을 수 있습니다. `lastMentioned`를 기준으로 관계를 필터링해 특정 기간의 이슈 관계망을 볼 수 있게 했습니다.

### 3.8 스냅샷 저장 흐름

1. 사용자가 탐색 패널에서 스냅샷 저장을 누릅니다.
2. 프론트는 현재 `limit`, `minStrength`, 엣지 필터, 시간 필터, 워크스페이스, 선택 노드 상태를 저장합니다.
3. 스냅샷 데이터는 브라우저 `localStorage`의 `ohara:snapshots`에 저장됩니다.
4. 스냅샷을 클릭하면 저장 당시의 필터와 선택 상태가 복원되고 그래프가 다시 로드됩니다.

핵심 답변:

> 스냅샷은 서버 데이터가 아니라 사용자의 화면 탐색 상태를 저장하는 기능입니다. 그래서 DB를 추가하지 않고 브라우저 localStorage로 가볍게 구현했습니다.

## 4. 백엔드 코드 설명

### 4.1 `OharaApplication.java`

Spring Boot 애플리케이션의 진입점입니다. `@SpringBootApplication`으로 컴포넌트 스캔과 자동 설정을 활성화합니다. `@EnableAsync`가 붙어 있어 `DocumentAnalysisService.analyzeUrl()` 같은 `@Async` 메서드가 별도 스레드에서 실행될 수 있습니다.

질문 대비:

> `@EnableAsync`가 없으면 `@Async`를 붙여도 비동기 실행이 되지 않고 일반 메서드처럼 동기 실행될 수 있습니다.

### 4.2 `SecurityConfig.java`

`BCryptPasswordEncoder`를 Bean으로 등록하고, Spring Security 기본 로그인 기능을 끕니다.

중요 코드 개념:

- `@Bean`: Spring 컨테이너가 관리하는 객체를 등록합니다.
- `csrf.disable()`: 현재 API 중심 구조와 개발 환경에서 CSRF 검증을 끕니다.
- `formLogin().disable()`, `httpBasic().disable()`: 기본 로그인 화면과 Basic 인증을 사용하지 않습니다.
- `SessionCreationPolicy.IF_REQUIRED`: 필요할 때만 세션을 만듭니다.
- `anyRequest().permitAll()`: 실제 인증 검사는 컨트롤러와 서비스에서 직접 처리합니다.

질문 대비:

> Spring Security를 완전히 안 쓰는 것이 아니라, 비밀번호 해시와 보안 필터 설정은 사용하되 로그인 검증과 토큰 처리는 프로젝트 요구에 맞게 직접 구현했습니다.

### 4.3 인증 계층

#### `AuthController.java`

인증 관련 HTTP API를 제공합니다.

- `POST /api/auth/register`: 회원가입 후 세션 저장
- `POST /api/auth/login`: 로그인 후 세션 저장
- `POST /api/auth/logout`: DB 토큰 삭제와 세션 무효화
- `GET /api/auth/me`: 세션 또는 Bearer 토큰으로 현재 로그인 사용자 복원

핵심 포인트:

- 컨트롤러는 HTTP 요청/응답 처리에 집중합니다.
- 실제 비즈니스 로직은 `AuthService`에 위임합니다.
- `HttpSession`에는 `username`, `token`을 저장합니다.

#### `AuthService.java`

사용자 인증의 핵심 비즈니스 로직입니다.

- 회원가입 입력 검증
- username/email 중복 검사
- BCrypt 비밀번호 해시 저장
- 로그인 비밀번호 검증
- UUID 토큰 생성
- `UserTokenRepository`를 통한 토큰 저장/검증/삭제
- DB 장애 시 메모리 `fallbackTokenStore` 사용

질문 대비:

> `encoder.matches(입력 평문, DB 해시)` 순서가 중요합니다. BCrypt는 매번 다른 salt를 사용하므로 입력값을 다시 encode해서 문자열 비교하면 안 됩니다.

### 4.4 워크스페이스 계층

#### `WorkspaceController.java`

워크스페이스와 문서 API를 담당합니다.

- `GET /api/workspaces`: 내 워크스페이스 목록 조회
- `POST /api/workspaces`: 워크스페이스 생성
- `PATCH /api/workspaces/{id}`: 이름 변경
- `DELETE /api/workspaces/{id}`: 삭제
- `GET /api/workspaces/{id}/documents`: 문서 목록 조회
- `POST /api/workspaces/{id}/documents`: URL 추가
- `DELETE /api/workspaces/{wsId}/documents/{docId}`: 문서 삭제

특징:

- `Default` 워크스페이스는 DB에 저장하지 않고 API 응답에서 id `0`으로 합성합니다.
- 일반 워크스페이스는 반드시 Authorization Bearer 토큰으로 사용자를 확인합니다.

#### `WorkspaceService.java`

워크스페이스의 실제 비즈니스 로직입니다.

중요 구현:

- `getUserByToken()`: 토큰에서 현재 사용자를 복원합니다.
- 모든 워크스페이스 조회/수정 전에 `findByIdAndUserId()`로 소유권을 확인합니다.
- `@Transactional(transactionManager = "transactionManager")`로 MySQL 트랜잭션을 명시합니다.
- URL 추가 시 문서를 `PENDING`으로 저장하고, `afterCommit()`에서 AI 분석을 시작합니다.
- 문서 삭제 시 MySQL 문서와 Neo4j `Document` 노드를 함께 삭제합니다.

질문 대비:

> 트랜잭션 커밋 전에 비동기 분석을 시작하면 다른 스레드에서 아직 커밋되지 않은 문서를 못 볼 수 있습니다. 그래서 `TransactionSynchronization.afterCommit()`으로 커밋 이후 AI 분석을 호출했습니다.

### 4.5 문서 분석 서비스

#### `DocumentAnalysisService.java`

Spring Boot와 Python AI Engine 사이의 연결 역할입니다.

동작:

- `@Async`: 별도 스레드에서 실행
- 문서 상태를 `ANALYZING`으로 변경
- `RestTemplate.postForObject()`로 FastAPI `/analyze/url` 호출
- 텍스트/PDF 문서는 FastAPI `/analyze/text` 호출
- 성공하면 title, entity_count를 반영하고 `DONE`
- 실패하면 최대 3회 재시도 후 `ERROR`

질문 대비:

> URL 분석은 네트워크와 NLP 처리 때문에 오래 걸릴 수 있습니다. API 응답을 막지 않기 위해 비동기로 처리하고, 문서 상태값으로 진행 상황을 프론트에 보여줍니다.

텍스트/PDF 질문 대비:

> PDF나 직접 입력 텍스트도 URL과 똑같이 비동기로 분석합니다. 백엔드에서 문서를 먼저 저장하고, PDF는 PDFBox로 텍스트를 추출한 뒤 AI Engine의 텍스트 분석 API로 넘깁니다.

### 4.6 그래프 조회 서비스

#### `GraphController.java`

Neo4j 그래프 관련 API를 제공합니다.

- `GET /api/graph`: 전체 그래프 조회
- `GET /api/graph/workspace/{workspaceId}`: 워크스페이스 그래프 조회
- `GET /api/node/{name}`: 노드 상세 조회
- `GET /api/node/{name}/articles`: 관련 기사 조회
- `GET /api/search`: 검색 자동완성
- `DELETE /api/node/{name}`: 엔티티 노드 삭제

`@Min`, `@Max`, `@Validated`를 사용해 요청 파라미터 범위를 제한합니다.

#### `GraphService.java`

Neo4j Cypher 쿼리를 실행하고 DTO로 변환합니다.

중요 상수:

- `ENTITY_LABEL_FILTER`: `Country`, `Organization`, `Person`만 엔티티로 취급합니다.
- `VALID_REL_COUNT`: `sourceKeys`가 있는 `RELATED_TO`만 유효 관계로 계산합니다.
- `WORKSPACE_REL_COUNT`: 특정 워크스페이스에 포함된 관계만 카운트합니다.

주요 메서드:

- `getGraph(limit, minStrength)`: 전체 그래프 조회
- `getWorkspaceGraph(workspaceId, limit, minStrength)`: 워크스페이스 문서 기반 그래프 조회
- `getNodeDetail(name)`: 타입, 연결 수, 관련 노드, 최근 기사 조회
- `search(query, limit)`: 엔티티 이름 부분 검색
- `deleteNode(name)`: 엔티티 삭제
- `deleteWorkspaceDocument(workspaceId, docId)`: Neo4j 문서 노드 삭제
- `findPath(from, to, maxDepth, workspaceId)`: 두 엔티티 사이의 최단 관계 경로 조회
- `getEdgeSources(source, target, workspaceId)`: 관계가 만들어진 기사/문서 출처 조회
- `updateNode(oldName, request)`: 엔티티 이름과 타입 수정

Cypher 포인트:

- `MATCH`: 노드나 관계 패턴을 찾습니다.
- `OPTIONAL MATCH`: 관계가 없어도 노드는 유지합니다.
- `MERGE`: 있으면 재사용하고 없으면 생성합니다.
- `DETACH DELETE`: 노드와 연결 관계를 함께 삭제합니다.
- `coalesce()`: null일 때 기본값을 사용합니다.
- `id(a) < id(b)`: 무방향 관계를 양방향 중복으로 반환하지 않기 위한 조건입니다.
- `shortestPath()`: 두 노드 사이의 가장 짧은 관계 경로를 찾습니다.
- `relationships(p)`: 경로에 포함된 관계 목록을 가져와 워크스페이스 필터 조건을 확인합니다.

질문 대비:

> Neo4j 관계가 무방향처럼 조회되면 같은 관계가 두 번 나올 수 있습니다. 그래서 `id(a) < id(b)` 조건으로 한 방향만 반환했습니다.

추가 기능 질문 대비:

> 경로 찾기와 엣지 출처 보기는 Neo4j의 그래프 탐색 장점을 보여주는 기능입니다. `shortestPath()`로 관계망 경로를 찾고, `sourceKeys`와 `workspaceDocKeys`로 어떤 기사나 문서에서 관계가 생겼는지 역추적합니다.

### 4.7 Entity 설명

#### `User.java`

`users` 테이블에 대응합니다. `username`, `email`은 unique이고, `password`에는 BCrypt 해시가 저장됩니다.

#### `UserToken.java`

`user_tokens` 테이블에 대응합니다. UUID 토큰을 PK로 사용하고, `User`와 `ManyToOne` 관계를 맺습니다. 서버 재시작 후에도 Bearer 토큰 검증이 가능하게 합니다.

#### `Workspace.java`

`workspaces` 테이블에 대응합니다. 한 사용자가 여러 워크스페이스를 가질 수 있으므로 `User`와 `ManyToOne` 관계입니다. `documents`는 `OneToMany`이고 `cascade = ALL`, `orphanRemoval = true`로 워크스페이스 삭제 시 문서도 함께 삭제됩니다.

#### `Document.java`

`documents` 테이블에 대응합니다. URL/PDF/NOTE 타입과 `PENDING`, `ANALYZING`, `DONE`, `ERROR` 상태를 가집니다. 현재 UI는 URL, 직접 입력 텍스트, PDF/TXT/MD 파일 분석을 지원합니다.

질문 대비:

> JPA 관계에서 `ManyToOne(fetch = LAZY)`를 사용한 이유는 매번 연관 객체를 즉시 가져오지 않아 불필요한 쿼리를 줄이기 위해서입니다.

### 4.8 Repository 설명

Spring Data JPA Repository는 메서드 이름으로 쿼리를 자동 생성합니다.

- `UserRepository.findByUsername()`: 로그인 사용자 조회
- `existsByUsername()`, `existsByEmail()`: 회원가입 중복 검사
- `WorkspaceRepository.findByIdAndUserId()`: 워크스페이스 소유권 확인
- `DocumentRepository.findByWorkspaceIdOrderByUploadedAtDesc()`: 문서 목록 최신순 조회
- `UserTokenRepository`: 기본 CRUD로 토큰 저장/조회/삭제

질문 대비:

> `findByIdAndUserId()`처럼 id와 userId를 같이 조건으로 걸면, 다른 사용자의 워크스페이스 id를 알아도 접근할 수 없습니다.

### 4.9 DTO 설명

#### `AuthDto.java`

Java `record`를 사용해 인증 요청/응답 객체를 간단하게 정의합니다.

- `RegisterRequest`
- `LoginRequest`
- `AuthResponse`
- `ErrorResponse`

#### `GraphDto.java`

그래프 API 응답 형식입니다.

- `NodeDto`: 그래프 노드
- `EdgeDto`: 그래프 관계
- `ArticleDto`: 기사 정보
- `GraphResponse`: 노드/엣지 묶음
- `NodeDetailDto`: 노드 상세 패널 데이터

질문 대비:

> Entity를 그대로 응답하지 않고 DTO를 쓰는 이유는 DB 내부 구조와 API 응답 구조를 분리하고, 프론트에 필요한 필드만 안정적으로 내려주기 위해서입니다.

## 5. AI Engine 코드 설명

### 5.1 `api.py`

FastAPI 서버입니다. Spring Boot의 `DocumentAnalysisService`가 이 서버를 호출합니다.

중요 구성:

- `AnalyzeUrlRequest`: URL 분석 요청 모델
- `AnalyzeTextRequest`: 직접 입력 텍스트/PDF 추출 텍스트 분석 요청 모델
- `AnalyzeUrlResponse`: 분석 응답 모델
- `fetch_text_from_url()`: URL HTML을 가져와 제목과 본문 텍스트 추출
- `analyze_url()`: URL 수집, NER 분석, Neo4j 저장을 한 번에 수행
- `analyze_text()`: 이미 추출된 텍스트를 NER 분석하고 Neo4j에 저장
- `_save_to_neo4j()`: `GraphWriter`에 저장 위임
- `/health`: 서버 상태 확인

본문 추출 방식:

- `requests.get()`으로 HTML 다운로드
- `BeautifulSoup`으로 파싱
- `script`, `style`, `nav`, `footer`, `header`, `aside` 제거
- `<title>` 또는 `og:title`로 제목 추출
- 본문 텍스트는 공백 정리 후 최대 8000자로 제한

질문 대비:

> HTML 전체를 그대로 NLP에 넣으면 스크립트나 메뉴가 섞여 정확도가 떨어지므로 불필요한 태그를 제거하고 텍스트만 추출했습니다.

텍스트 분석 질문 대비:

> `/analyze/text`는 URL 수집 단계가 필요 없는 입력을 처리합니다. PDF에서 추출한 텍스트나 사용자가 직접 붙여넣은 본문을 같은 `extract_batch()`와 `GraphWriter`로 처리해 저장 방식이 URL 분석과 동일합니다.

### 5.2 `crawler/collector.py`

RSS 기반 뉴스 수집 모듈입니다.

주요 개념:

- `RSS_SOURCES`: BBC, Al Jazeera, Guardian, NPR 등 RSS 목록
- `GEOPOLITICS_KEYWORDS`: 세계정세 관련 기사만 남기는 키워드
- `EXCLUDE_KEYWORDS`: 스포츠, 연예, 생활 등 제외 키워드
- `Article`: NLP에 넘길 기사 데이터 클래스
- `fetch_all()`: 모든 RSS 소스에서 기사 수집
- `_fetch()`: RSS 엔트리 하나씩 필터링
- `_clean()`: HTML 엔티티 디코딩과 공백 정리
- `_parse_date()`: 기사 발행일 UTC 변환

질문 대비:

> 모든 뉴스를 수집하면 노이즈가 커져 그래프 품질이 떨어집니다. 그래서 국제정치 키워드가 포함되고 스포츠/연예 등 제외 키워드가 없는 기사만 처리했습니다.

### 5.3 `nlp/extractor.py`

spaCy NER 결과를 프로젝트 엔티티와 관계로 변환합니다.

주요 개념:

- `spacy.load("en_core_web_lg")`: 영어 대형 NER 모델 사용
- `TARGET = {"GPE", "ORG", "PERSON"}`: 국가/지역, 기관, 인물만 사용
- `Entity`: 정규화된 엔티티
- `Result`: 기사, 엔티티 목록, 관계 목록
- `extract_batch()`: 여러 기사 텍스트를 배치 처리
- `_extract()`: spaCy 엔티티를 정규화하고 관계 생성
- `_build_person_map()`: 성만 나온 인물을 풀네임으로 통합
- `_build_sentence_relations()`: 같은 문장에 함께 등장한 엔티티 쌍을 관계로 만듦

관계 생성 기준:

> 같은 문장에 같이 등장한 국가, 기관, 인물은 해당 기사 안에서 의미적으로 관련 있을 가능성이 높다고 보고 `RELATED_TO` 후보 관계로 만듭니다.

질문 대비:

> 기사 전체에서 한 번이라도 같이 나온 모든 엔티티를 연결하면 관계가 너무 느슨해질 수 있습니다. 그래서 문장 단위 공동 등장을 기준으로 관계를 만들었습니다.

### 5.4 `nlp/normalizer.py`

NER 결과를 표준 이름과 타입으로 정리합니다.

왜 필요한가:

- `U.S.`, `USA`, `America`를 모두 `United States`로 합쳐야 합니다.
- `Trump`와 `Donald Trump`를 같은 인물로 합쳐야 합니다.
- `government`, `officials` 같은 일반 명사는 엔티티에서 제외해야 합니다.
- `Washington`, `Beijing` 같은 수도 표현을 국가로 해석할 수 있습니다.

주요 자료구조:

- `KNOWN_PERSONS`: 자주 등장하는 정치인 별칭 정규화
- `COUNTRY_ALIASES`: 국가/수도/별칭 정규화
- `ORG_ALIASES`: 국제기구와 기관 별칭 정규화
- `GENERIC_ORGS`: 너무 일반적인 조직명 제외
- `GENERIC_PERSONS`: 직책 같은 일반 인물명 제외

주요 함수:

- `get_canonical_name(raw_text, spacy_label)`: 표준 이름 반환
- `get_entity_type(raw_text, spacy_label)`: `Country`, `Organization`, `Person` 타입 반환
- `_clean_entity_text()`: HTML 엔티티, 공백, 앞뒤 문장부호 제거
- `_is_bad_person_name()`: 잘못된 인물명 필터링

질문 대비:

> NER 모델 결과를 그대로 쓰면 같은 대상이 여러 노드로 쪼개지고, 일반 명사도 노드가 됩니다. 그래서 정규화 계층을 두어 그래프 품질을 높였습니다.

### 5.5 `processor/graph_writer.py`

Neo4j에 그래프 데이터를 저장하는 모듈입니다.

초기화:

- `GraphDatabase.driver()`로 Neo4j 연결
- `_init()`에서 unique constraint 생성
- `Country`, `Organization`, `Person`은 `name` unique
- `Article`은 `url` unique
- `Document`는 `docId` unique

일반 RSS 기사 저장:

- `write(result)`
- `_merge_article()`: `Article` 노드 생성/갱신
- `_merge_article_entities()`: 엔티티 노드 생성 후 `MENTIONED_IN` 관계 연결
- `_write_related_to()`: 엔티티 간 `RELATED_TO` 관계 저장

워크스페이스 문서 저장:

- `write_workspace_document()`
- `_merge_workspace_document()`: `Document` 노드 생성/갱신
- `_merge_workspace_entities()`: `MENTIONED_IN_WORKSPACE {workspaceId}` 관계 생성
- `_write_related_to()`에 `workspaceId`, `workspaceDocKey`를 함께 저장

중요한 중복 방지 로직:

- `sourceKeys`: 어떤 기사/문서에서 만들어진 관계인지 기록합니다.
- 같은 `sourceKey`가 이미 있으면 `strength`를 다시 증가시키지 않습니다.
- `workspaceIds`: 어떤 워크스페이스에서 등장했는지 저장합니다.
- `workspaceDocKeys`: 워크스페이스별 문서 단위 관계 카운트를 가능하게 합니다.

질문 대비:

> `MERGE`로 노드와 관계를 중복 생성하지 않게 했고, `sourceKeys` 배열로 같은 기사나 문서를 다시 처리해도 관계 강도가 중복 증가하지 않게 했습니다.

### 5.6 `main.py`

RSS 자동 수집 파이프라인 실행 파일입니다.

흐름:

1. `fetch_all()`로 RSS 기사 수집
2. `extract_batch()`로 엔티티/관계 추출
3. `GraphWriter.write_batch()`로 Neo4j 저장
4. `schedule.every(INTERVAL_SEC).seconds`로 주기 실행

질문 대비:

> 사용자 URL 분석은 FastAPI `api.py`가 처리하고, 정기 뉴스 수집은 `main.py`가 처리합니다. 두 경로가 모두 같은 `extract_batch()`와 `GraphWriter`를 사용하므로 그래프 저장 방식은 일관됩니다.

## 6. 프론트엔드 코드 설명

### 6.1 `main.jsx`

React 앱의 진입점입니다. `ReactDOM.createRoot()`로 HTML의 `root`에 `Root` 컴포넌트를 렌더링합니다.

### 6.2 `Root.jsx`

페이지 전환과 로그인 상태 복원을 담당합니다.

상태:

- `page`: landing, login, register, graph 중 현재 화면
- `user`: 로그인 사용자명
- `ready`: 초기 인증 확인 완료 여부

흐름:

- 앱 시작 시 `sessionStorage`에서 토큰과 사용자명을 읽습니다.
- `/api/auth/me`를 호출해 서버 세션 또는 Bearer 토큰을 검증합니다.
- 로그인 성공 시 `App` 그래프 화면으로 이동합니다.
- 로그아웃 시 서버 로그아웃 호출 후 로컬 저장소를 정리합니다.

질문 대비:

> 브라우저 새로고침 후에도 로그인 상태를 복원하기 위해 프론트 저장 토큰과 서버 세션을 함께 확인합니다.

### 6.3 `App.jsx`

메인 그래프 화면입니다.

주요 상태:

- `graphData`: 서버에서 받은 원본 그래프
- `filtered`: 필터가 적용된 그래프
- `selectedNode`: 클릭한 노드
- `selectedEdge`: 클릭한 관계선
- `highlight`: 강조 표시할 노드
- `limit`: 가져올 노드 수
- `minStrength`: 최소 관계 강도
- `days`: 최근 며칠 관계만 볼지 정하는 시간 필터
- `edgeFilter`: 관계 타입 필터
- `selectedWorkspaceId`: 선택된 워크스페이스 id
- `pathNodes`, `pathEdges`: 경로 찾기 결과 강조 대상
- `snapshots`: localStorage에 저장된 그래프 화면 상태

주요 로직:

- `loadGraph()`: 전체 그래프 또는 워크스페이스 그래프 로드
- `filterEdges()`: 국가-국가, 국가-인물 등 관계 타입 필터 적용
- `handleFindPath()`: 두 노드 사이 최단 경로 조회 후 강조
- `handleLinkClick()`: 엣지 출처 기사/문서 조회
- `saveSnapshot()`, `restoreSnapshot()`: 현재 탐색 상태 저장과 복원
- D3 force 설정: `forceCollide`로 노드 겹침 방지, `charge`로 노드 간 반발력 설정
- `paintNode()`: Canvas에 노드 원과 라벨 직접 그림
- `linkWidth()`: 관계 강도에 따라 선 두께 조절
- `getLinkColor()`: 선택 노드와 연결된 엣지를 강조

질문 대비:

> `react-force-graph-2d`는 내부적으로 D3 force simulation을 사용합니다. 서버 데이터는 `nodes`와 `links`로 변환하고, Canvas 렌더링 함수에서 노드 크기, 색상, 라벨을 직접 그렸습니다.

추가 기능 질문 대비:

> 경로 찾기 결과는 별도 그래프를 새로 그리는 것이 아니라 기존 그래프 위에서 해당 노드와 엣지를 강조합니다. 스냅샷은 서버에 저장할 데이터가 아니라 화면 탐색 상태라서 localStorage로 처리했습니다.

### 6.4 API 모듈

#### `api/client.js`

그래프 관련 API 호출을 모아둔 파일입니다.

- `getGraph()`
- `getNode()`
- `search()`
- `getWorkspaceGraph()`
- `findPath()`
- `getEdgeSources()`
- `updateNode()`
- `deleteNode()`

Vite 개발 서버 프록시를 활용하기 위해 `/api` 경로로 요청합니다.

#### `api/auth.js`

인증 관련 API와 브라우저 저장소 처리를 담당합니다.

- `getToken()`, `getUser()`
- `saveAuth()`
- `clearAuth()`
- `authApi.register()`
- `authApi.login()`
- `authApi.logout()`
- `authApi.me()`

`credentials: 'include'`를 사용해 쿠키 기반 세션도 함께 전송합니다.

#### `api/workspace.js`

워크스페이스 API 호출을 담당합니다.

- 모든 요청에 `Authorization: Bearer {token}` 헤더를 붙입니다.
- 워크스페이스 CRUD와 문서 추가/삭제를 제공합니다.

### 6.5 화면 컴포넌트

#### `Landing.jsx`

초기 랜딩 화면입니다. Canvas를 사용해 노드와 엣지 배경 애니메이션을 직접 그립니다.

#### `Login.jsx`

로그인 폼입니다. 제출 시 `authApi.login()`을 호출하고 성공하면 토큰을 저장합니다.

#### `Register.jsx`

회원가입 폼입니다. 비밀번호 확인, 길이 검증을 프론트에서 먼저 수행한 뒤 서버에 요청합니다.

#### `SearchBar.jsx`

검색 자동완성 컴포넌트입니다.

- 입력이 2글자 이상일 때만 검색합니다.
- `setTimeout` 300ms로 debounce를 적용합니다.
- 결과 선택 시 그래프에서 해당 노드로 이동합니다.

질문 대비:

> debounce를 둔 이유는 사용자가 한 글자 입력할 때마다 API를 호출하지 않고, 입력이 잠시 멈췄을 때만 검색해 서버 부하를 줄이기 위해서입니다.

#### `GraphControls.jsx`

노드 수와 최소 관계 강도를 슬라이더로 조정하는 컴포넌트입니다. 값이 바뀌면 `App.jsx`의 상태가 변경되고 그래프를 다시 로드합니다.

#### `FilterPanel.jsx`

관계 타입 필터 UI와 필터링 함수를 제공합니다.

- `Country`는 `C`
- `Organization`은 `O`
- `Person`은 `P`

엣지의 양 끝 타입을 정렬해서 `CC`, `CO`, `CP`, `OO`, `OP`, `PP`와 비교합니다.

#### `ArticlePanel.jsx`

노드 클릭 시 오른쪽에 뜨는 상세 패널입니다.

- `api.getNode(selectedNode)`로 상세 조회
- 관련 노드 표시
- 최근 기사 목록 표시
- 노드 삭제 버튼 제공

#### `WorkspacePanel.jsx`

워크스페이스 목록과 문서 관리를 담당하는 사이드 패널입니다.

주요 기능:

- 워크스페이스 목록 조회
- 워크스페이스 생성, 이름 변경, 삭제
- 워크스페이스 상세 문서 목록 조회
- URL 추가
- 직접 입력 텍스트 추가
- PDF/TXT/MD 파일 업로드
- 문서 분석 상태 표시
- 워크스페이스별 그래프 보기

문서 추가 모드:

- URL: 웹페이지 본문을 AI Engine에서 수집하고 분석
- 텍스트: 사용자가 입력한 본문을 그대로 분석
- 파일: PDF는 백엔드에서 텍스트를 추출하고, TXT/MD는 문자열로 읽어 분석

문서 상태 UI:

- `PENDING`: 대기 중
- `ANALYZING`: 분석 중
- `DONE`: 완료, 엔티티 수 표시
- `ERROR`: 오류

질문 대비:

> 워크스페이스 문서는 MySQL에 상태를 저장하고, 분석 결과 그래프는 Neo4j에 저장합니다. 그래서 UI는 MySQL 문서 상태와 Neo4j 그래프 조회를 함께 사용합니다.

#### 경로/스냅샷/관계 출처 UI

`App.jsx` 안의 탐색 패널에서 제공합니다.

- 경로 찾기: 출발 노드와 도착 노드를 입력하면 `/api/path` 결과를 그래프에서 강조
- 관계 출처: 엣지를 클릭하면 `/api/edge/sources` 결과를 오른쪽 패널에 표시
- 시간 필터: `days` 파라미터로 최근 관계만 조회
- 스냅샷: 현재 필터와 선택 상태를 `localStorage`에 저장하고 복원

질문 대비:

> 그래프를 단순히 보여주는 데서 끝내지 않고, 사용자가 관계의 이유와 경로를 탐색할 수 있게 했습니다. 경로와 출처는 Neo4j 조회 결과이고, 스냅샷은 개인 화면 상태라 브라우저 저장소에만 저장합니다.

#### 엔티티 수정 UI

`ArticlePanel.jsx`에서 노드 상세를 볼 때 엔티티 이름과 타입을 수정할 수 있습니다.

- 이름 변경: Neo4j 노드의 `name` 속성 변경
- 타입 변경: `Country`, `Organization`, `Person` 타입 속성과 라벨 변경
- 중복 이름 방지: 이미 같은 이름의 엔티티가 있으면 수정 실패

질문 대비:

> NER과 정규화가 항상 완벽하지 않기 때문에 사용자가 잘못 잡힌 엔티티를 수정할 수 있게 했습니다. 수정 시 그래프 품질을 보정할 수 있고, 중복 이름은 막아 데이터가 섞이지 않게 했습니다.

## 7. 데이터 모델 정리

### 7.1 MySQL 모델

`users`

- 사용자 계정
- username, email, password, created_at

`user_tokens`

- 로그인 토큰
- token, user_id, created_at

`workspaces`

- 사용자별 작업 공간
- title, description, user_id, created_at, updated_at

`documents`

- 워크스페이스에 추가된 문서
- title, type, source_url, content, status, entity_count, uploaded_at
- type은 `URL`, `PDF`, `NOTE`를 사용합니다.
- content에는 직접 입력 텍스트나 PDF/TXT에서 추출한 분석 대상 텍스트를 저장합니다.

### 7.2 Neo4j 모델

노드:

- `Country`: 국가
- `Organization`: 기관/조직
- `Person`: 인물
- `Article`: RSS 수집 기사
- `Document`: 사용자가 워크스페이스에 추가한 문서

관계:

- `MENTIONED_IN`: 엔티티가 일반 기사에 언급됨
- `MENTIONED_IN_WORKSPACE`: 엔티티가 워크스페이스 문서에 언급됨
- `RELATED_TO`: 두 엔티티가 같은 문장 또는 문서에서 함께 등장함

관계 속성:

- `strength`: 공동 등장 강도
- `articleCount`: 기사/문서 기준 공동 등장 횟수
- `sourceKeys`: 중복 증가 방지용 출처 key
- `workspaceIds`: 관계가 등장한 워크스페이스 목록
- `workspaceDocKeys`: 워크스페이스 문서별 관계 출처
- `firstMentioned`, `lastMentioned`: 최초/최근 등장 시각

추가 조회 활용:

- 시간 필터는 `RELATED_TO.lastMentioned`를 기준으로 최근 관계만 조회합니다.
- 관계 출처 보기는 `sourceKeys`로 `Article`을, `workspaceDocKeys`로 `Document`를 역추적합니다.
- 경로 찾기는 `RELATED_TO` 관계를 따라 `shortestPath()`로 두 엔티티 사이의 중간 노드를 찾습니다.

## 8. 핵심 기술 개념

### Spring Boot 계층 구조

- Controller: HTTP 요청/응답 담당
- Service: 비즈니스 로직 담당
- Repository: DB 접근 담당
- Entity: DB 테이블 구조 표현
- DTO: API 요청/응답 데이터 표현

교수님 질문 답변:

> 계층을 나누면 HTTP 처리, 비즈니스 로직, 데이터 접근이 분리되어 코드 변경 범위가 줄고 테스트와 유지보수가 쉬워집니다.

### JPA

Java 객체와 관계형 DB 테이블을 매핑하는 ORM입니다. `@Entity`, `@Table`, `@Id`, `@ManyToOne`, `@OneToMany` 같은 어노테이션으로 테이블과 관계를 표현합니다.

### Transaction

여러 DB 작업을 하나의 작업 단위로 묶습니다. 중간에 실패하면 전체를 롤백해 데이터 일관성을 지킵니다.

### Async

오래 걸리는 작업을 별도 스레드에서 실행합니다. 이 프로젝트에서는 URL 분석이 오래 걸리므로 `@Async`를 사용합니다.

### REST API

프론트엔드와 백엔드는 HTTP JSON API로 통신합니다. 리소스 중심 URL을 사용하고, 작업에 따라 GET, POST, PATCH, DELETE를 나눠 사용합니다.

### Neo4j

그래프 데이터베이스입니다. 노드와 관계를 직접 저장하기 때문에 "이 인물과 연결된 기관", "이 국가와 같이 언급된 국가" 같은 관계 탐색에 적합합니다.

### NER

Named Entity Recognition입니다. 텍스트에서 인물, 조직, 장소 같은 고유명사를 찾아내는 NLP 작업입니다. 이 프로젝트는 spaCy의 `GPE`, `ORG`, `PERSON` 라벨을 사용합니다.

### Normalization

동일한 대상을 하나의 표준 이름으로 통합하는 과정입니다. 예를 들어 `U.S.`, `USA`, `America`를 모두 `United States`로 바꿉니다.

### Force Graph

그래프 노드들이 물리 시뮬레이션처럼 배치되는 시각화 방식입니다. 연결된 노드는 가까워지고, 전체 노드는 서로 밀어내며 보기 좋은 관계망을 만듭니다.

### Shortest Path

그래프에서 두 노드 사이를 잇는 가장 짧은 경로를 찾는 방식입니다. 이 프로젝트에서는 Neo4j의 `shortestPath()`를 사용해 두 국가, 기관, 인물 사이에 어떤 중간 엔티티가 있는지 탐색합니다.

### Multipart Upload

파일을 HTTP 요청으로 업로드하는 방식입니다. PDF/TXT/MD 파일 업로드 기능에서 사용하며, 백엔드는 `MultipartFile`로 파일을 받고 PDF는 PDFBox로 텍스트를 추출합니다.

### LocalStorage

브라우저에 간단한 사용자 데이터를 저장하는 저장소입니다. 이 프로젝트에서는 그래프 스냅샷처럼 서버 DB에 저장할 필요가 없는 개인 화면 상태를 저장하는 데 사용합니다.

## 9. 예상 질문과 답변

### Q1. 왜 MySQL과 Neo4j를 둘 다 사용했나요?

MySQL은 사용자, 워크스페이스, 문서 상태처럼 정형화된 데이터를 안정적으로 관리하기 좋습니다. Neo4j는 엔티티 간 관계처럼 연결 탐색이 핵심인 데이터를 저장하고 조회하기 좋습니다. 그래서 데이터 성격에 맞게 DB를 분리했습니다.

### Q2. 왜 URL 분석을 비동기로 처리했나요?

URL 접속, HTML 파싱, NLP 분석, Neo4j 저장은 시간이 오래 걸릴 수 있습니다. 동기로 처리하면 사용자가 요청 후 오래 기다려야 하므로, 문서를 먼저 `PENDING`으로 저장하고 분석은 백그라운드에서 실행했습니다.

### Q3. 왜 트랜잭션 커밋 후 분석을 시작하나요?

분석 작업은 별도 스레드에서 문서 id로 DB를 다시 조회합니다. 커밋 전에 시작하면 비동기 스레드가 아직 저장되지 않은 문서를 못 볼 수 있습니다. 그래서 `afterCommit()`에서 분석을 시작했습니다.

### Q4. 그래프 관계 강도는 어떻게 계산하나요?

AI Engine에서 같은 문장에 함께 등장한 엔티티 쌍을 관계로 만들고, Neo4j의 `RELATED_TO` 관계에 `strength`를 누적합니다. 같은 기사나 문서가 중복 처리되어도 `sourceKeys`로 확인해 한 번만 증가시킵니다.

### Q5. 워크스페이스 그래프는 전체 그래프와 어떻게 다르나요?

전체 그래프는 모든 기사와 문서를 기반으로 조회합니다. 워크스페이스 그래프는 `MENTIONED_IN_WORKSPACE {workspaceId}` 관계와 `workspaceIds`, `workspaceDocKeys` 속성을 이용해 해당 워크스페이스 문서에서 등장한 엔티티와 관계만 보여줍니다.

### Q6. 엔티티 정규화는 왜 필요한가요?

NER 모델은 `U.S.`, `USA`, `America`를 서로 다른 텍스트로 뽑을 수 있습니다. 정규화를 하지 않으면 같은 국가가 여러 노드로 나뉘어 그래프 품질이 떨어집니다. 그래서 별칭 사전과 필터링으로 표준 이름을 만듭니다.

### Q7. 프론트에서 그래프는 어떻게 그리나요?

백엔드 응답의 `nodes`, `edges`를 `react-force-graph-2d`가 요구하는 `nodes`, `links` 형태로 변환합니다. 노드는 Canvas drawing 함수에서 타입별 색상과 degree 기반 크기로 그리고, 관계는 strength 기반 두께로 표시합니다.

### Q8. 검색 기능은 어떻게 구현했나요?

프론트 `SearchBar`에서 입력을 debounce 처리한 뒤 `/api/search`를 호출합니다. 백엔드는 Neo4j에서 이름에 검색어가 포함된 엔티티를 찾고 degree가 높은 순서로 반환합니다. 선택하면 그래프에서 해당 노드를 중심으로 이동하고 확대합니다.

### Q9. 삭제 기능은 어떤 데이터를 지우나요?

노드 삭제는 Neo4j에서 해당 엔티티 노드를 `DETACH DELETE`로 삭제해 연결 관계도 함께 제거합니다. 문서 삭제는 MySQL의 문서 row와 Neo4j의 `Document` 노드를 함께 삭제합니다.

### Q10. 비밀번호 보안은 어떻게 처리했나요?

회원가입 시 비밀번호를 BCrypt로 해시해서 저장합니다. 로그인 시에는 입력 평문과 저장된 해시를 `encoder.matches()`로 비교합니다. BCrypt는 salt를 포함하므로 같은 비밀번호도 매번 다른 해시가 만들어져 더 안전합니다.

### Q11. DTO를 왜 사용했나요?

Entity를 그대로 응답하면 DB 내부 구조가 API에 노출되고, Lazy loading이나 순환 참조 문제가 생길 수 있습니다. DTO를 사용하면 프론트에 필요한 데이터만 명확하게 내려줄 수 있습니다.

### Q12. `Default` 워크스페이스는 왜 DB에 저장하지 않았나요?

Default는 모든 사용자가 공유하는 전체 그래프 보기용 가상 워크스페이스입니다. 사용자별 소유 데이터가 아니므로 DB row로 만들지 않고 API 응답에서 id `0`으로 합성했습니다.

### Q13. Neo4j에서 unique constraint를 둔 이유는 무엇인가요?

같은 이름의 국가, 기관, 인물 노드가 중복 생성되면 관계 그래프가 분산됩니다. 그래서 `Country.name`, `Organization.name`, `Person.name`, `Article.url`, `Document.docId`에 unique constraint를 둬 중복을 막았습니다.

### Q14. `sourceKeys`는 왜 필요한가요?

같은 기사나 문서가 다시 처리될 때 관계 강도가 계속 증가하면 데이터가 왜곡됩니다. `sourceKeys`에 이미 처리한 출처를 저장해 같은 출처는 한 번만 strength를 증가시킵니다.

### Q15. 현재 프로젝트의 한계는 무엇인가요?

URL 본문 추출은 사이트 구조에 따라 정확도가 달라질 수 있습니다. NER도 모델이 모든 인물/기관을 완벽히 구분하지 못하므로 정규화 사전을 계속 보강해야 합니다. 또한 현재 인증은 실습용 세션/토큰 구조이므로 실제 운영에서는 만료 시간, refresh token, HTTPS, 더 엄격한 CORS 설정이 필요합니다.

### Q16. 두 엔티티 사이의 경로 찾기는 어떻게 구현했나요?

프론트에서 출발 노드와 도착 노드를 입력하면 `/api/path`를 호출합니다. 백엔드는 Neo4j의 `shortestPath()`로 `RELATED_TO` 관계를 따라 최단 경로를 찾고, 워크스페이스 그래프에서는 해당 `workspaceId`가 포함된 관계만 사용합니다.

### Q17. 관계선을 클릭했을 때 출처는 어떻게 찾나요?

`RELATED_TO` 관계에는 `sourceKeys`와 `workspaceDocKeys`가 저장되어 있습니다. 전체 그래프에서는 `sourceKeys`의 `article:{url}` 값을 이용해 `Article` 노드를 찾고, 워크스페이스 그래프에서는 `workspaceDocKeys`를 이용해 `Document` 노드를 찾아 보여줍니다.

### Q18. 시간 필터는 어떤 기준으로 동작하나요?

관계의 `lastMentioned` 속성을 기준으로 최근 1일, 7일, 30일, 90일 관계만 조회합니다. 노드 degree 계산에도 같은 조건을 적용해 최근 이슈와 관련 있는 노드가 우선 보이게 했습니다.

### Q19. 스냅샷은 왜 DB가 아니라 localStorage에 저장했나요?

스냅샷은 그래프 데이터 자체가 아니라 사용자의 화면 상태입니다. 서버에서 공유하거나 영구 보관할 필요가 작아서 브라우저 `localStorage`에 저장해 가볍게 구현했습니다.

### Q20. PDF 업로드는 어떻게 분석하나요?

프론트에서 multipart form으로 파일을 업로드하면 백엔드가 `MultipartFile`로 받습니다. PDF는 Apache PDFBox로 텍스트를 추출하고, TXT/MD는 UTF-8 문자열로 읽은 뒤 `/analyze/text`를 통해 AI Engine에 분석을 요청합니다.

### Q21. 엔티티 수정 기능은 왜 넣었나요?

NER과 정규화가 항상 완벽하지 않기 때문입니다. 잘못 분류된 엔티티를 사용자가 이름이나 타입으로 보정할 수 있고, 같은 이름의 엔티티가 이미 있으면 수정하지 못하게 해 데이터 충돌을 막았습니다.

## 10. 발표용 짧은 설명 스크립트

OHARA는 국제 뉴스와 사용자가 추가한 URL, 텍스트, PDF 문서에서 국가, 기관, 인물을 추출해 관계 그래프로 보여주는 프로젝트입니다. 프론트엔드는 React로 그래프 탐색 UI를 만들었고, 백엔드는 Spring Boot로 인증, 워크스페이스, 문서 상태, Neo4j 조회 API를 제공합니다. AI Engine은 FastAPI와 spaCy를 사용해 문서 텍스트에서 엔티티를 추출하고, 같은 문장에 함께 등장한 엔티티를 관계로 만들어 Neo4j에 저장합니다.

DB는 역할에 따라 분리했습니다. MySQL은 사용자와 문서 상태 같은 정형 데이터를 저장하고, Neo4j는 엔티티 관계처럼 연결 탐색이 중요한 데이터를 저장합니다. URL 분석은 시간이 오래 걸리기 때문에 문서를 먼저 `PENDING`으로 저장하고, 트랜잭션 커밋 이후 `@Async`로 비동기 분석을 실행합니다. 분석 결과는 문서 상태와 엔티티 수로 MySQL에 반영되고, 그래프 관계는 Neo4j에 저장됩니다.

프론트엔드는 `react-force-graph-2d`로 그래프를 렌더링합니다. 노드 타입에 따라 색상을 다르게 하고, 연결 수가 많을수록 노드를 크게 표시합니다. 검색, 필터, 워크스페이스별 그래프 조회, 노드 상세 패널뿐 아니라 경로 찾기, 관계 출처 보기, 시간 필터, 스냅샷 저장, 엔티티 수정 기능으로 사용자가 관계를 더 깊게 탐색할 수 있게 했습니다.

## 11. 파일별 빠른 암기표

| 파일 | 역할 |
| --- | --- |
| `backend/src/main/java/com/ohara/OharaApplication.java` | Spring Boot 시작점, 비동기 활성화 |
| `backend/src/main/java/com/ohara/config/SecurityConfig.java` | BCrypt Bean, Security 기본 로그인 비활성화 |
| `backend/src/main/java/com/ohara/controller/AuthController.java` | 회원가입, 로그인, 로그아웃, 내 정보 API |
| `backend/src/main/java/com/ohara/service/AuthService.java` | 비밀번호 검증, 토큰 생성/검증 |
| `backend/src/main/java/com/ohara/controller/WorkspaceController.java` | 워크스페이스와 문서 API |
| `backend/src/main/java/com/ohara/service/WorkspaceService.java` | 소유권 확인, URL/텍스트/PDF 문서 저장, 분석 예약 |
| `backend/src/main/java/com/ohara/service/DocumentAnalysisService.java` | AI Engine URL/텍스트 분석 호출, 문서 상태 갱신 |
| `backend/src/main/java/com/ohara/controller/GraphController.java` | 그래프 조회, 검색, 경로 찾기, 엣지 출처, 엔티티 수정/삭제 API |
| `backend/src/main/java/com/ohara/service/GraphService.java` | Neo4j Cypher 조회, 시간 필터, shortestPath, DTO 변환 |
| `ai-engine/api.py` | FastAPI URL/텍스트 분석 API |
| `ai-engine/crawler/collector.py` | RSS 뉴스 수집 |
| `ai-engine/nlp/extractor.py` | spaCy NER, 문장 단위 관계 추출 |
| `ai-engine/nlp/normalizer.py` | 엔티티 이름/타입 정규화 |
| `ai-engine/processor/graph_writer.py` | Neo4j 노드/관계 저장 |
| `frontend/src/Root.jsx` | 로그인 상태 복원과 화면 전환 |
| `frontend/src/App.jsx` | 메인 그래프 화면, 시간 필터, 경로 찾기, 관계 출처, 스냅샷 |
| `frontend/src/api/*.js` | 백엔드 API 호출 모듈 |
| `frontend/src/components/SearchBar.jsx` | 검색 자동완성 |
| `frontend/src/components/ArticlePanel.jsx` | 노드 상세, 엔티티 수정, 삭제 |
| `frontend/src/components/WorkspacePanel.jsx` | 워크스페이스와 URL/텍스트/PDF 문서 관리 |
| `frontend/src/components/FilterPanel.jsx` | 엣지 타입 필터 |
