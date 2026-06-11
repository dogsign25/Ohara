# OHARA

## 개요

OHARA는 뉴스 URL, 직접 입력한 텍스트, PDF/TXT/MD 파일에서 국가, 기관, 인물을 추출하고 관계 그래프로 시각화하는 애플리케이션입니다.

사용자는 회원가입과 로그인 후 워크스페이스를 만들고 문서를 등록할 수 있습니다. Spring Boot는 사용자 인증, 워크스페이스, 문서 상태와 API를 관리하고 FastAPI AI Engine에 분석을 요청합니다. AI Engine은 spaCy NER로 엔티티와 공동 등장 관계를 추출해 Neo4j에 저장합니다. React 프론트엔드는 저장된 그래프를 조회하고 검색, 경로 탐색, 관계 출처 확인, 노드 편집 기능을 제공합니다.

```text
React (3000)
  -> Spring Boot (8080)
       -> MySQL (3306): 사용자, 토큰, 워크스페이스, 문서 상태
       -> FastAPI (8001): URL 수집, 텍스트 분석, NER
            -> Neo4j Bolt (7687): 엔티티와 관계
```

### 주요 기능

- 회원가입, 로그인, 로그아웃과 세션·UUID 토큰 인증
- 사용자별 워크스페이스 생성, 수정, 삭제
- URL, 텍스트, PDF/TXT/MD 문서 등록
- 비동기 AI 분석과 `PENDING -> ANALYZING -> DONE/ERROR` 상태 관리
- 국가, 기관, 인물 NER 추출과 이름 정규화
- 워크스페이스별 Neo4j 관계 그래프 조회
- 엔티티 검색, 상세 조회, 수정, 삭제
- 두 엔티티의 최단 경로 탐색
- 관계 생성과 관계 출처 문서 조회

## 기술 스택

### Backend

| 기술 | 용도 |
|---|---|
| Java 21 | 백엔드 개발 언어 |
| Spring Boot 3.2.5 | API 서버와 애플리케이션 구성 |
| Spring MVC | REST API |
| Spring Data JPA | MySQL 데이터 접근 |
| Spring Data Neo4j / Neo4j Java Driver | Neo4j 연결과 Cypher 실행 |
| Spring Security | BCrypt, 보안 필터와 세션 정책 |
| Spring Validation | 요청 파라미터 검증 |
| Spring Async | AI 분석 비동기 실행 |
| Apache PDFBox 3.0.2 | PDF 텍스트 추출 |
| Gradle | 빌드와 의존성 관리 |

### AI Engine

| 기술 | 용도 |
|---|---|
| Python | AI 분석 서버 개발 언어 |
| FastAPI 0.111.0 | 분석 REST API |
| Uvicorn 0.29.0 | ASGI 서버 |
| spaCy 3.7.4 | NER 엔티티 추출 |
| `en_core_web_lg` | 영어 spaCy 모델 |
| Neo4j Python Driver 5.19.0 | 분석 결과 그래프 저장 |
| BeautifulSoup4 / lxml | URL HTML 본문 추출 |
| feedparser | RSS 뉴스 수집 |
| Requests | URL 요청 |
| schedule | 주기적 뉴스 수집 |

### Frontend

| 기술 | 용도 |
|---|---|
| React 18.3.1 | 사용자 인터페이스 |
| Vite 8 | 개발 서버와 빌드 |
| Axios | Spring API 요청 |
| react-force-graph-2d | 관계 그래프 시각화 |
| d3-force | 그래프 물리 효과 |
| Tailwind CSS 3.4.4 | 화면 스타일 |

### Database

| 저장소 | 저장 데이터 |
|---|---|
| MySQL | 사용자, 로그인 토큰, 워크스페이스, 문서와 분석 상태 |
| Neo4j | Country, Organization, Person, Article, Document 노드와 관계 |

## 프로젝트 구조

```text
ohara/
├── README.md
├── backend/
│   ├── build.gradle
│   ├── gradlew
│   └── src/main/
│       ├── java/com/ohara/
│       │   ├── OharaApplication.java
│       │   ├── config/
│       │   │   └── SecurityConfig.java
│       │   ├── controller/
│       │   │   ├── AuthController.java
│       │   │   ├── GraphController.java
│       │   │   └── WorkspaceController.java
│       │   ├── entity/
│       │   │   ├── Document.java
│       │   │   ├── User.java
│       │   │   ├── UserToken.java
│       │   │   └── Workspace.java
│       │   ├── model/
│       │   │   ├── AuthDto.java
│       │   │   └── GraphDto.java
│       │   ├── repository/
│       │   │   ├── DocumentRepository.java
│       │   │   ├── UserRepository.java
│       │   │   ├── UserTokenRepository.java
│       │   │   └── WorkspaceRepository.java
│       │   └── service/
│       │       ├── AuthService.java
│       │       ├── DocumentAnalysisService.java
│       │       ├── GraphService.java
│       │       └── WorkspaceService.java
│       └── resources/
│           └── application.yml
├── ai-engine/
│   ├── api.py
│   ├── main.py
│   ├── debug_run.py
│   ├── requirements.txt
│   ├── crawler/
│   │   └── collector.py
│   ├── nlp/
│   │   ├── extractor.py
│   │   └── normalizer.py
│   └── processor/
│       └── graph_writer.py
└── frontend/
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── main.jsx
        ├── Root.jsx
        ├── App.jsx
        ├── index.css
        ├── api/
        │   ├── auth.js
        │   ├── client.js
        │   ├── http.js
        │   └── workspace.js
        ├── components/
        │   ├── ArticlePanel.jsx
        │   ├── FilterPanel.jsx
        │   ├── GraphControls.jsx
        │   ├── Login.jsx
        │   ├── Register.jsx
        │   ├── SearchBar.jsx
        │   └── WorkspacePanel.jsx
        └── pages/
            ├── DashboardPage.jsx
            ├── GraphPage.jsx
            ├── Landing.jsx
            ├── SettingsPage.jsx
            ├── SourcesPage.jsx
            └── WorkspacePage.jsx
```

## 실행 방법

### 1. 사전 준비

다음 프로그램이 필요합니다.

- Java 21
- Python 3
- Node.js와 npm
- MySQL
- Neo4j

기본 연결 정보는 다음과 같습니다.

| 서비스 | 기본 주소 |
|---|---|
| Frontend | `http://localhost:3000` |
| Spring Boot | `http://localhost:8080` |
| FastAPI | `http://localhost:8001` |
| MySQL | `localhost:3306/ohara` |
| Neo4j Browser | `http://localhost:7474` |
| Neo4j Bolt | `bolt://localhost:7687` |

MySQL에 `ohara` 데이터베이스를 생성합니다.

```sql
CREATE DATABASE ohara CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 환경변수

기본값과 다른 계정 또는 주소를 사용한다면 실행 전에 환경변수를 설정합니다.

```bash
export DB_URL='jdbc:mysql://localhost:3306/ohara?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
export DB_USER='root'
export DB_PASSWORD='1234'

export NEO4J_URI='bolt://localhost:7687'
export NEO4J_USER='neo4j'
export NEO4J_PASSWORD='12345678'

export AI_ENGINE_URL='http://localhost:8001'
```

### 3. AI Engine 실행

```bash
cd ai-engine
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m spacy download en_core_web_lg
uvicorn api:app --host 0.0.0.0 --port 8001 --reload
```

상태 확인:

```bash
curl http://localhost:8001/health
```

### 4. Spring Boot 실행

다른 터미널에서 실행합니다.

```bash
cd backend
./gradlew bootRun
```

Spring Boot는 기본적으로 `8080` 포트를 사용합니다. 이미 사용 중이라면 기존 Java 프로세스를 종료하거나 다음처럼 다른 포트를 지정합니다.

```bash
./gradlew bootRun --args='--server.port=8081'
```

### 5. Frontend 실행

다른 터미널에서 실행합니다.

```bash
cd frontend
npm install
npm run dev
```

브라우저에서 `http://localhost:3000`에 접속합니다. Vite는 `/api` 요청을 `http://localhost:8080`으로 프록시합니다.

### 6. 빌드 확인

```bash
cd backend
./gradlew test
```

```bash
cd frontend
npm run build
```

```bash
cd ai-engine
python3 -m py_compile api.py main.py crawler/collector.py nlp/extractor.py nlp/normalizer.py processor/graph_writer.py
```

## Java 파일 역할

### 애플리케이션과 설정

| 파일 | 주요 역할 |
|---|---|
| `OharaApplication.java` | Spring Boot 시작점이며 `@EnableAsync`로 비동기 분석을 활성화합니다. |
| `SecurityConfig.java` | BCrypt Bean, CSRF, 세션 정책, 폼 로그인과 HTTP Basic 비활성화 규칙을 설정합니다. |

### Controller

| 파일 | 주요 역할 |
|---|---|
| `AuthController.java` | 회원가입, 로그인, 로그아웃, 현재 사용자 복원 API와 HttpSession을 처리합니다. |
| `WorkspaceController.java` | 워크스페이스 CRUD와 URL·텍스트·파일 문서 API를 제공합니다. |
| `GraphController.java` | 전체·워크스페이스 그래프, 검색, 상세, 최단 경로, 관계 출처, 노드·관계 편집 API를 제공합니다. |

### Service

| 파일 | 주요 역할 |
|---|---|
| `AuthService.java` | 사용자 검증, BCrypt 비밀번호 비교, UUID 토큰 생성·검증·삭제를 담당합니다. |
| `WorkspaceService.java` | 워크스페이스 소유권 검사, MySQL 트랜잭션, 문서 생성·삭제와 커밋 후 분석 예약을 담당합니다. |
| `DocumentAnalysisService.java` | FastAPI를 비동기로 호출하고 문서 상태를 `ANALYZING`, `DONE`, `ERROR`로 갱신합니다. |
| `GraphService.java` | Neo4j Cypher를 실행해 그래프 조회, 검색, 최단 경로, 출처 조회, 노드·관계 수정과 삭제를 수행합니다. |

### Entity

| 파일 | 주요 역할 |
|---|---|
| `User.java` | MySQL `users` 테이블 사용자 엔티티입니다. |
| `UserToken.java` | 로그인 UUID 토큰과 사용자의 연결을 저장하는 엔티티입니다. |
| `Workspace.java` | 사용자별 분석 공간과 하위 문서 관계를 저장합니다. |
| `Document.java` | URL·NOTE·PDF 문서와 분석 상태, 엔티티 개수를 저장합니다. |

### Repository

| 파일 | 주요 역할 |
|---|---|
| `UserRepository.java` | 사용자명 조회와 아이디·이메일 중복 검사를 수행합니다. |
| `UserTokenRepository.java` | 로그인 토큰 저장, 조회와 삭제를 수행합니다. |
| `WorkspaceRepository.java` | 사용자별 워크스페이스 조회와 소유권 검사를 수행합니다. |
| `DocumentRepository.java` | 문서 CRUD와 워크스페이스별 최신 문서 조회를 수행합니다. |

### DTO

| 파일 | 주요 역할 |
|---|---|
| `AuthDto.java` | 회원가입·로그인 요청과 인증 응답 record를 정의합니다. |
| `GraphDto.java` | 노드, 관계, 기사, 관계 출처, 그래프, 경로와 수정 요청 DTO를 정의합니다. |

## Python 파일 역할

| 파일 | 주요 역할 |
|---|---|
| `api.py` | FastAPI 서버입니다. URL·텍스트 분석 요청을 받고 spaCy 분석 후 Neo4j 저장 결과를 반환합니다. |
| `main.py` | RSS 수집, NER 분석, Neo4j 저장 파이프라인을 일정 주기로 실행하는 독립 배치 프로그램입니다. |
| `debug_run.py` | Neo4j 저장 없이 뉴스 수집과 NER 결과를 콘솔에서 확인하는 디버그 스크립트입니다. |
| `crawler/collector.py` | RSS 소스를 수집하고 국제정세 키워드로 기사를 필터링해 `Article`로 변환합니다. |
| `nlp/extractor.py` | `en_core_web_lg` 모델로 GPE, ORG, PERSON을 추출하고 같은 문장의 엔티티 쌍을 관계로 만듭니다. |
| `nlp/normalizer.py` | 국가·기관·인물 이름을 정리하고 프로젝트 타입인 Country, Organization, Person으로 변환합니다. |
| `processor/graph_writer.py` | Neo4j 제약조건을 만들고 Article, Document, 엔티티 노드와 관계를 Cypher `MERGE`로 저장합니다. |
| `crawler/__init__.py` | crawler 디렉터리를 Python 패키지로 인식하게 합니다. |
| `nlp/__init__.py` | nlp 디렉터리를 Python 패키지로 인식하게 합니다. |
| `processor/__init__.py` | processor 디렉터리를 Python 패키지로 인식하게 합니다. |

## JSX 파일 역할

### 최상위 화면

| 파일 | 주요 역할 |
|---|---|
| `main.jsx` | React 애플리케이션을 DOM에 마운트합니다. |
| `Root.jsx` | 저장된 토큰을 검증하고 랜딩, 로그인, 회원가입, 애플리케이션 화면을 전환합니다. |
| `App.jsx` | 로그인 후 상단 메뉴와 페이지 이동, 선택 워크스페이스 상태를 관리합니다. |

### Pages

| 파일 | 주요 역할 |
|---|---|
| `Landing.jsx` | 로그인 전 서비스 소개와 로그인·회원가입 진입 화면입니다. |
| `GraphPage.jsx` | 그래프 시각화, 검색, 필터, 최단 경로, 관계 출처, 관계 생성과 스냅샷을 담당합니다. |
| `DashboardPage.jsx` | 전체 그래프 통계와 주요 엔티티를 요약합니다. |
| `WorkspacePage.jsx` | 워크스페이스 생성, 선택과 URL 문서 관리를 독립 페이지로 제공합니다. |
| `SourcesPage.jsx` | 최근 그래프 관계와 기사 출처 정보를 요약합니다. |
| `SettingsPage.jsx` | 브라우저에 저장되는 그래프 표시 설정을 관리합니다. |

### Components

| 파일 | 주요 역할 |
|---|---|
| `Login.jsx` | 로그인 폼과 인증 API 호출을 담당합니다. |
| `Register.jsx` | 회원가입 폼과 가입 후 로그인 처리를 담당합니다. |
| `SearchBar.jsx` | 엔티티 검색 자동완성과 그래프 노드 선택을 담당합니다. |
| `GraphControls.jsx` | 노드 표시 제한과 최소 관계 강도를 조절합니다. |
| `FilterPanel.jsx` | 그래프 관계 표시 기준을 선택합니다. |
| `ArticlePanel.jsx` | 선택한 엔티티의 상세·관련 기사·수정·삭제·관계 연결 기능을 제공합니다. |
| `WorkspacePanel.jsx` | 그래프 화면의 워크스페이스 목록, 문서 추가·삭제와 그래프 선택 사이드 패널입니다. |

## JS 파일 역할

| 파일 | 주요 역할 |
|---|---|
| `api/http.js` | `/api`를 기본 주소로 사용하는 Axios 인스턴스와 공통 오류·응답 변환 함수를 제공합니다. |
| `api/auth.js` | 인증 토큰·사용자 저장과 회원가입, 로그인, 로그아웃, 사용자 복원 API를 제공합니다. |
| `api/workspace.js` | 인증 헤더를 포함한 워크스페이스와 문서 API 함수를 제공합니다. |
| `api/client.js` | 그래프 조회, 검색, 최단 경로, 관계 출처와 노드·관계 편집 API를 제공합니다. |
| `vite.config.js` | React 플러그인, 개발 포트 `3000`, Spring API 프록시를 설정합니다. |
| `postcss.config.js` | PostCSS와 Tailwind CSS 처리 플러그인을 설정합니다. |
| `tailwind.config.js` | Tailwind가 검색할 프론트 파일 경로와 테마 설정을 정의합니다. |

## 주요 데이터 흐름

### 문서 분석

```text
사용자 문서 등록
  -> WorkspaceController
  -> WorkspaceService
  -> MySQL Document PENDING 저장
  -> 트랜잭션 COMMIT
  -> DocumentAnalysisService 비동기 실행
  -> FastAPI /analyze/url 또는 /analyze/text
  -> spaCy NER 및 이름 정규화
  -> GraphWriter가 Neo4j 저장
  -> MySQL Document DONE 또는 ERROR 갱신
```

### 그래프 조회

```text
GraphPage
  -> GraphController
  -> GraphService
  -> Neo4j Cypher 조회
  -> GraphDto 응답
  -> react-force-graph-2d 시각화
```
