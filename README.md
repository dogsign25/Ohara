# OHARA — World Relationship Intelligence Engine

뉴스를 수집해 국가·기관·인물 간 관계를 인터랙티브 그래프로 시각화하는 OSINT 플랫폼.

---

## 사전 준비

아래 항목이 설치되어 있어야 합니다.

| 항목 | 버전 | 확인 명령어 |
|------|------|-------------|
| Java | 21 이상 | `java -version` |
| Maven | 3.9 이상 | `mvn -version` |
| Python | 3.12 이상 | `python3 --version` |
| Node.js | 20 이상 | `node -v` |
| Neo4j Desktop | 최신 | — |

---

## 1단계 — Neo4j 실행

1. [Neo4j Desktop](https://neo4j.com/download/) 설치 후 실행
2. **New Project → Add Database → Create a Local Database** 클릭
3. 설정값 입력:
   - Name: `ohara`
   - Password: `ohara1234`
4. **Start** 버튼 클릭 → 상태가 `Running`이 될 때까지 대기
5. 브라우저로 확인: http://localhost:7474
   - Username: `neo4j`
   - Password: `ohara1234`

> Neo4j가 실행 중이 아니면 Python 엔진과 Spring Boot 모두 연결에 실패합니다.

---

## 2단계 — Python NLP 엔진 실행

터미널 1에서 실행합니다.

```bash
cd ohara/ai-engine

# 가상환경 생성 (처음 한 번만)
python3 -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate

# 패키지 설치 (처음 한 번만)
pip install -r requirements.txt
python -m spacy download en_core_web_lg

# Phase 1 확인 — Neo4j 없이 추출 결과만 콘솔로 출력
python debug_run.py

# 정상 확인 후 실제 파이프라인 실행 (5분마다 수집)
python main.py
```

실행되면 다음과 같은 로그가 출력됩니다:

```
2024-xx-xx [INFO] ohara: OHARA NLP Engine 시작 (주기: 300초)
2024-xx-xx [INFO] ohara: === 파이프라인 시작 ===
2024-xx-xx [INFO] crawler.collector: [Reuters] 30개 수집
2024-xx-xx [INFO] crawler.collector: [BBC] 25개 수집
...
2024-xx-xx [INFO] ohara: 추출: 180개 엔티티
2024-xx-xx [INFO] ohara: 저장 완료: 82/95
2024-xx-xx [INFO] ohara: === 파이프라인 완료 ===
```

> **첫 실행 후 최소 1분 기다린 뒤** 3단계로 넘어가세요.
> Neo4j 브라우저(http://localhost:7474)에서 `MATCH (n) RETURN n LIMIT 50` 을 실행해 노드가 생성됐는지 확인할 수 있습니다.

---

## 3단계 — Spring Boot API 실행

터미널 2에서 실행합니다.

```bash
cd ohara/backend

# 빌드 + 실행 (처음엔 Maven이 의존성 다운로드하므로 2~3분 걸림)
mvn spring-boot:run
```

실행되면 다음 로그가 보입니다:

```
Started OharaApplication in 3.2 seconds (JVM running for 3.8)
```

API 동작 확인:

```bash
curl http://localhost:8080/api/graph?limit=50
# → {"nodes":[...],"edges":[...],"totalNodes":50,"totalEdges":120}
```

---

## 4단계 — React 프론트엔드 실행

터미널 3에서 실행합니다.

```bash
cd ohara/frontend

# 패키지 설치 (처음 한 번만)
npm install

# 개발 서버 실행
npm run dev
```

브라우저에서 http://localhost:3000 접속

---

## 전체 실행 순서 요약

```
Neo4j Desktop 시작
       ↓
터미널 1:  cd ai-engine && python main.py
       ↓  (1분 대기)
터미널 2:  cd backend   && mvn spring-boot:run
       ↓
터미널 3:  cd frontend  && npm run dev
       ↓
브라우저:  http://localhost:3000
```

---

## API 엔드포인트

| 메서드 | URL | 설명 |
|--------|-----|------|
| GET | `/api/graph?limit=100&minStrength=1` | 전체 그래프 |
| GET | `/api/node/{name}` | 노드 상세 + 관련 기사 |
| GET | `/api/node/{name}/articles` | 관련 기사만 |
| GET | `/api/search?q=NATO&limit=10` | 검색 자동완성 |

**파라미터 설명**
- `limit`: 반환할 최대 노드 수 (기본 100, 최대 500). 너무 높으면 브라우저가 느려집니다.
- `minStrength`: 이 값 이상의 공동 등장 횟수를 가진 관계만 표시 (기본 1, 높일수록 강한 관계만).

---

## 프로젝트 구조

```
ohara/
├── README.md
│
├── ai-engine/                  # Python NLP 파이프라인
│   ├── main.py                 # 진입점 (스케줄러)
│   ├── debug_run.py            # Phase 1 확인용
│   ├── requirements.txt
│   ├── crawler/
│   │   └── collector.py        # RSS 수집
│   ├── nlp/
│   │   ├── normalizer.py       # 엔티티 정규화 (alias 테이블)
│   │   └── extractor.py        # spaCy NER
│   └── processor/
│       └── graph_writer.py     # Neo4j MERGE + strength 누적
│
├── backend/                    # Spring Boot API
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ohara/
│       │   ├── OharaApplication.java
│       │   ├── controller/GraphController.java
│       │   ├── service/GraphService.java
│       │   └── model/Dto.java
│       └── resources/
│           └── application.yml
│
└── frontend/                   # React (JSX)
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── main.jsx
        ├── App.jsx
        ├── api/client.js
        └── components/
            ├── SearchBar.jsx
            ├── ArticlePanel.jsx
            └── GraphControls.jsx
```

---

## 자주 겪는 문제

**Python — `ModuleNotFoundError`**
```bash
# 가상환경이 활성화됐는지 확인
source venv/bin/activate
```

**Python — `en_core_web_lg` 없음**
```bash
python -m spacy download en_core_web_lg
```

**Spring Boot — Neo4j 연결 실패**
```
Neo4j가 실행 중인지 확인: http://localhost:7474
비밀번호가 ohara1234인지 확인
```

**Spring Boot — 포트 충돌**
```bash
# 8080 포트 점유 프로세스 확인
lsof -i :8080
```

**프론트엔드 — 그래프가 안 나옴**
```
콘솔(F12)에서 API 에러 확인
Python 엔진이 최소 한 번 실행 완료됐는지 확인
```

---

## 개발 환경 설정

**IntelliJ IDEA (백엔드)**
1. `backend/` 폴더를 Maven 프로젝트로 열기
2. Java 21 SDK 설정
3. `OharaApplication.java` 실행

**VSCode (Python + 프론트엔드)**
```bash
# 추천 익스텐션
code --install-extension ms-python.python
code --install-extension esbenp.prettier-vscode
```
