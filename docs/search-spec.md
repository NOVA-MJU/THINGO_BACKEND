# PostgreSQL 통합 검색 요구사항 명세서

## 1. 목적

기존 Elasticsearch 기반 통합 검색을 **PostgreSQL**로 대체하기 위한 통합 검색 모듈을 신규 구축한다.
ES 폴더의 Controller/Document 구조와 API 계약은 동일하게 유지하되, 저장소·인덱싱·검색 엔진을 PostgreSQL로 전환한다.

- 기존 ES 코드는 비교/대체 시점까지 병행 유지.
- 신규 모듈 패키지: `nova.mjs.domain.thingo.search.postgres` (가칭)

---

## 2. 범위

### 2.1 검색 대상 도메인 (ES `SearchType`과 동일)

| Enum | 의미 | 원본 도메인 |
|------|------|------------|
| NOTICE | 공지사항 | `notice` |
| MJU_CALENDAR | 학사일정 | `mjuCalendar` |
| DEPARTMENT_NOTICE | 학과 공지 | `departmentNotice` |
| STUDENT_COUNCIL_NOTICE | 총학 공지 | `studentCouncilNotice` |
| DEPARTMENT_SCHEDULE | 학과 일정 | `departmentSchedule` |
| COMMUNITY | 커뮤니티 게시글 | `community` |
| NEWS | 명대뉴스 | `news` |
| BROADCAST | 방송 | `broadcast` |

### 2.2 비기능 범위
- 한국어 형태소 분석: Komoran(기존 `KomoranTokenizerUtil`) 재사용. PG `tsvector`는 미리 토큰화된 문자열을 `simple` config로 저장한다.
- 자동완성·연관검색: 1차에서는 prefix + trigram 기반으로 한정.
- 운영 인덱스 동기화 API 유지.

---

## 3. API 요구사항

ES `SearchController`, `SuggestController`와 **동일한 경로/파라미터/응답 스키마**를 유지한다.

### 3.1 GET `/api/v1/search/detail`

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|-----|--------|------|
| keyword | String | N | `""` | 검색어 |
| type | String | N | null | `SearchType` 이름 (대소문자 무시) |
| category | String | N | null | 도메인 세부 카테고리 |
| order | String | N | `relevance` | `relevance` \| `latest` \| `oldest` |
| pageable | Pageable | N | size=10 | Spring Pageable |

응답: `ApiResponse<Page<SearchResponseDTO>>` (기존 DTO 그대로 재사용)

### 3.2 POST `/api/v1/search/sync`
- 통합 인덱스 전체 재구축. 운영 전용.
- 응답: `ApiResponse<String>`

### 3.3 GET `/api/v1/search/suggest`
- 입력: `keyword` (String, 필수)
- 응답: `ApiResponse<List<String>>`

---

## 4. 데이터 모델

### 4.1 통합 검색 엔티티 (신규)

테이블명: `unified_search_index`

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|-----|------|
| id | VARCHAR(64) | PK | `TYPE:ORIGINAL_ID` 형식 |
| original_id | VARCHAR(64) | NOT NULL, INDEX | 원본 도메인 PK |
| type | VARCHAR(32) | NOT NULL, INDEX | `SearchType` |
| category | VARCHAR(64) | NULL, INDEX | 세부 카테고리 |
| title | TEXT | NOT NULL | 원문 제목 |
| content | TEXT | NULL | 정규화된 본문 |
| author_name | VARCHAR(64) | NULL | 작성자명 |
| link | TEXT | NULL | 원문 링크 |
| image_url | TEXT | NULL | 썸네일 |
| like_count | INT | NULL | 좋아요 |
| comment_count | INT | NULL | 댓글 수 |
| popularity | DOUBLE PRECISION | NULL | 가중치 점수 |
| active | BOOLEAN | DEFAULT TRUE | 노출 여부 |
| date | TIMESTAMP WITH TIME ZONE | NOT NULL, INDEX | 원본 발행/정렬 시간 |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | 인덱스 갱신 시간 |
| search_tokens | TEXT | NULL | Komoran 토큰화 문자열 |
| search_vector | TSVECTOR | NULL | FTS 인덱스 대상 |

#### 인덱스
- `GIN(search_vector)` - FTS
- `GIN(search_tokens gin_trgm_ops)` - 유사도/부분일치 (`pg_trgm`)
- `BTREE(type, category, date DESC)` - 탭/카테고리/최신순
- `BTREE(date DESC)` - 전체 최신순

#### 트리거
- `BEFORE INSERT/UPDATE`: `search_vector = to_tsvector('simple', coalesce(search_tokens, ''))`

### 4.2 JPA 엔티티
- 이름: `UnifiedSearchIndex`
- 위치: `domain/thingo/search/postgres/entity/`
- 규칙: `@Setter` 금지, `@Builder`, 정적 팩토리 `of(SearchDocument)` 제공.
- `tsvector` 컬럼은 Hibernate Custom Type 또는 native query로만 접근.

---

## 5. 인덱싱

### 5.1 토큰화
- 기존 `KomoranTokenizerUtil` 재사용.
- 본문은 도메인별 `*ContentPreprocessor`를 그대로 호출하여 정규화.
- 최종 토큰 문자열은 공백 join 후 `search_tokens`에 저장.

### 5.2 이벤트 동기화
- 기존 `SearchIndexPublisher` / `EntityIndexEvent` 인프라를 재사용한다.
- 새 리스너: `UnifiedSearchPostgresIndexer` (가칭)
  - `EntityIndexEvent` 수신 → 도메인별 `SearchDocument` 변환 → `unified_search_index` upsert
- ES 리스너와 병행 운영 가능하도록 별도 빈으로 분리한다.

### 5.3 전체 재인덱싱
- `/api/v1/search/sync` 호출 시 모든 도메인 Repository에서 batch 조회 → `unified_search_index` truncate 후 bulk insert.
- 배치 사이즈: 1000.

---

## 6. 검색 동작

### 6.1 정렬
| order | 정렬 키 |
|-------|---------|
| relevance | `ts_rank(search_vector, query) * 0.6 + similarity(search_tokens, keyword) * 0.4 + popularity_boost` |
| latest | `date DESC` |
| oldest | `date ASC` |

### 6.2 필터
- `type != null` → `type = ?`
- `category != null` → `category = ?`
- `active = true` 강제
- `keyword` 공백 → 전체 최신순 fallback

### 6.3 매칭 전략
1. `keyword`를 Komoran 토큰화
2. `plainto_tsquery('simple', tokens)` 생성
3. WHERE `search_vector @@ query` OR `search_tokens % keyword`(trigram)
4. relevance 점수 계산
5. order, paging 적용

### 6.4 결과 변환
- 행 → `SearchResponseDTO`
- `highlightedTitle`, `highlightedContent`는 `ts_headline('simple', content, query)` 사용 (없으면 원문 fallback)

---

## 7. 자동완성

1차 구현:
- 입력 keyword prefix로 `title` LIKE 검색
- `pg_trgm`의 `word_similarity`로 보조 정렬
- 상위 10개 title 반환

확장 여지:
- 별도 `search_suggest` 테이블 (keyword, weight, last_seen) + 실시간 키워드 적재.

---

## 8. 마이그레이션 / 운영

- DDL: Flyway/Liquibase 미사용 시 `schema.sql` 또는 별도 SQL 스크립트.
- 확장: `CREATE EXTENSION IF NOT EXISTS pg_trgm;`
- 기존 ES 코드는 feature flag(`search.engine=es|pg`)로 라우팅 가능하도록 둔다(선택).

---

## 9. 비범위 (Out of Scope)

- 동적 동의어 사전 reload
- 형태소 분석기 교체 (Mecab 도입은 별도 과제)
- 분산 샤딩
- 실시간 집계 facet
- 하이라이팅 다중 스니펫

---

## 10. 수용 기준 (Acceptance Criteria)

1. ES Controller와 동일한 요청/응답 스키마로 동작한다.
2. 8개 `SearchType` 모두 인덱싱·검색된다.
3. `order` 3종 정렬이 동작한다.
4. `keyword`가 비어 있을 때 최신순 전체 결과를 반환한다.
5. 도메인 엔티티 변경 시 `EntityIndexEvent`를 통해 `unified_search_index`가 갱신된다.
6. `/sync` 호출 시 전체 재인덱싱이 성공한다.
7. 100만 건 기준 단일 keyword 검색 응답 P95 < 500ms (로컬 PG 16, 워밍업 후).
