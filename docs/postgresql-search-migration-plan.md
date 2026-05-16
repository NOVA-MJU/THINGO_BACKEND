# Elasticsearch → PostgreSQL 통합 검색 마이그레이션 설계서

## 1) Phase 0 분석 요약표

| 기능 | 기존 ES 구현 방식 | PG 대체 시 고려사항 |
|---|---|---|
| 인덱스 매핑 | `UnifiedSearchDocument`에 `title/content/searchTokens/category`, `title_autocomplete(search_as_you_type)`, `suggest(completion)` 필드 구성. 날짜는 `epoch_millis`. | `thingo_search_document` 테이블 + `weighted_tsv` 컬럼으로 통합. completion/search_as_you_type는 `search_query_log + pg_trgm/prefix index` 조합으로 대체. |
| Analyzer | Nori analyzer 설정은 코드상 명시 없음. 애플리케이션 레벨에서 `KomoranTokenizerUtil`로 형태소/복합어 토큰 생성(`searchTokens`). | `to_tsvector` 기본 + 한국어 형태소(선택: mecab config/pg_bigm/앱 전처리 지속). 기존 `search_tokens` 재활용 권장. |
| 검색 쿼리 | `bool` + 다중 `should`: `match_phrase(title)`, `multi_match(title^6,category^boost,content^0.3,searchTokens^boost)`, `bool_prefix(title_autocomplete)` + expansion + negative strategy + freshness/popularity boost. | `ts_rank_cd` 중심 + `WHERE type/category` 필터. 가중치: A=title/search_tokens, B=category, D=content. 최신성/인기도는 보조 점수 컬럼 또는 SQL 가중식으로 추가. |
| 정렬 | relevance=`_score`(+date tie-breaker), latest/oldest=`date` 정렬. | relevance=`ts_rank_cd(weighted_tsv, tsquery)` + `created_at DESC`, latest/oldest는 `created_at` 인덱스 사용. |
| 동기화 | `SearchIndexSyncService`: Notice/Community(전처리 포함) + News/DepartmentSchedule/StudentCouncilNotice/Broadcast/MjuCalendar를 각 도메인 인덱스로 저장 후 unified 재구축. | 동일 도메인 원천 사용, ES 저장 대신 `thingo_search_document`로 bulk insert + trigger로 `weighted_tsv` 자동 갱신. |
| 응답 DTO | `SearchResponseDTO`: id, highlightedTitle, highlightedContent, date, link, category, type, imageUrl, score, authorName, likeCount, commentCount. | DTO 그대로 유지. 하이라이트는 1차적으로 원문 title/content 반환(추후 `ts_headline` 적용 가능). |
| 실시간 검색어 | Redis ZSET(`realtime_keywords`) + keyword별 timestamp LIST(`search:history{keyword}`), 3일 TTL, 1시간 스케줄 정리. | `search_query_log` 누적 + 윈도우 집계/MV + Redis 캐시 병행. 장애 시 Redis fallback 유지. |

### 필드 boost → `ts_rank_cd` weight 매핑
- ES의 주요 boost 상대강도 기반 권장 매핑:
  - **A**: `title`, `search_tokens` (exact/compact/token 신호 핵심)
  - **B**: `category`
  - **C**: `title_normalized`, `category_normalized` (선택)
  - **D**: `content`

가중 tsvector 예시:
```sql
setweight(to_tsvector('simple', coalesce(title,'')), 'A') ||
setweight(to_tsvector('simple', coalesce(search_tokens,'')), 'A') ||
setweight(to_tsvector('simple', coalesce(category,'')), 'B') ||
setweight(to_tsvector('simple', coalesce(content,'')), 'D')
```

## 2) ERD / 아키텍처 다이어그램

```text
[Client]
   |
   v
GET /api/v1/search/detail
POST /api/v1/search/sync
GET /api/v1/search/suggest
   |
   v
[SearchController]
   |-- UnifiedSearchService ------------------------------.
   |-- SearchIndexSyncService ---------------------.      |
   |-- RealtimeKeywordService ------------------.  |      |
   v                                              |  |      |
[PostgresUnifiedSearchRepository]                 |  |      |
   |                                              |  |      |
   |-- thingo_search_document (FTS + filter) <----'  |      |
   |-- search_query_log (autocomplete/realtime) ------'      |
   |-- mv_realtime_keywords (optional refresh)-------------'
   |
   '-- Redis (hot cache/fallback for top keywords)
```

## 3) 한국어 처리 대안

1. **mecab + custom text search config**
   - 장점: 형태소 품질 우수, 조사/어미 처리 강함.
   - 단점: 운영 환경 설치/빌드 난이도 높음.
2. **pg_bigm (bi-gram)**
   - 장점: 설정 간단, 오타/부분일치 강함.
   - 단점: 인덱스 커짐, 의미 기반 랭킹은 약함.
3. **앱 전처리 후 tsvector 저장 (현행 Komoran 확장)**
   - 장점: 현재 로직 재사용, 전환 리스크 낮음.
   - 단점: DB 단독 파이프라인보다 앱 의존 증가.

**추천안:** 단기에는 **(3)** + `pg_trgm` 보강, 중장기에 필요시 (1) 도입.

## 4) DDL + 인덱스

- 실제 SQL: `src/main/resources/db/search/postgres_search_schema.sql`
- 핵심:
  - `thingo_search_document` + `weighted_tsv` + GIN
  - `(type, category)` 복합 인덱스
  - `created_at` 정렬 인덱스
  - `title` prefix + trigram 인덱스
  - `search_query_log` + 집계용 인덱스 + MV

## 5) 추천 검색(Autocomplete/Suggest) SQL 랭킹

```sql
score = (
  personal_weight * 0.45
  + global_popularity * 0.35
  + title_hits * 0.20
) * EXP(-λ * EXTRACT(EPOCH FROM (now() - searched_at)))
```

- prefix 중심: `ILIKE :keyword || '%'` + B-Tree(prefix) 우선
- infix/fuzzy 보강: `pg_trgm GIN` (`%keyword%`/유사도)

## 6) 최신성 decay 정렬 예시

- Exponential:
```sql
relevance_score * EXP(-0.00002 * EXTRACT(EPOCH FROM (NOW() - created_at)))
```
- Linear:
```sql
relevance_score * GREATEST(0.2, 1 - EXTRACT(EPOCH FROM (NOW() - created_at))/2592000)
```
- Step:
```sql
relevance_score * CASE
  WHEN created_at >= NOW() - INTERVAL '7 days' THEN 1.15
  WHEN created_at >= NOW() - INTERVAL '30 days' THEN 1.05
  ELSE 1.0
END
```

## 7) 정확도/성능 검증 기준

- BM25(ES) vs ts_rank_cd(PG):
  - BM25는 TF saturation/문서 길이 정규화가 정교.
  - ts_rank_cd는 커버 밀도 기반으로 단순하며 길이 보정이 제한적.
- 보완 전략:
  - `custom_score = ts_rank_cd + recency + popularity + click-through` 조합.
  - offline metric: Precision@10, nDCG@10 (쿼리셋/정답셋 기반).

### 예상 성능 매트릭스 (인덱스/카디널리티에 따른 범위 추정)

| 규모 | tsvector+GIN | LIKE prefix | REGEXP | pg_trgm |
|---|---:|---:|---:|---:|
| 10만 row | 30~90ms | 10~40ms | 300ms+ | 40~120ms |
| 100만 row | 80~220ms | 30~120ms | 2s+ | 120~350ms |
| 1,000만 row | 250~600ms | 120~500ms | 10s+ | 400ms~1.2s |

> REGEXP는 대부분 full scan 유발로 기본 경로에서 제외 권장.

### SLA 판단
- 자동완성 200ms: prefix + hot cache(Redis) 시 충족 가능.
- 검색 결과 500ms: GIN + 필터 인덱스 + read replica 조건에서 충족 가능.

## 8) 마이그레이션 체크리스트

1. DDL 적용(`postgres_search_schema.sql`).
2. `SearchIndexSyncService`로 최초 full rebuild.
3. `/search/detail` 트래픽의 5~10%를 PG path로 canary.
4. Precision@10/nDCG 비교(ES vs PG).
5. p95 latency 점검(autocomplete 200ms, detail 500ms).
6. Redis 캐시 튜닝(hot query TTL).
7. ES write 중단 → read 중단 → 인프라 제거.
8. ES config/repository 의존성 제거 정리.
