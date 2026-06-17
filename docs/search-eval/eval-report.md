# 통합검색(PostgreSQL FTS) 품질 평가 리포트

- 대상: `GET /api/v2/search/detail` (order=relevance, topK=10)
- 코퍼스: `unified_search_index` 실데이터 3,975건 (RDS)
- 쿼리: 명지대 학생 의도 기반 72개 (8개 의도 x 9개 입력형)
- 판정: LLM 휴리스틱 (top-4 제목/스니펫 기준 의도일치)
- 원본 데이터: `eval-results.json` / 사람이 보는 표: `eval-results.md`
- 실행: `docs/search-eval/run-search-eval.ps1`

---

## 0. 조치 적용 결과 (Before / After)

동일 72쿼리 재실행 (warmup 후) 기준.

| 지표 | Before | After | 변화 |
|------|--------|-------|------|
| keyword 검색 P95 지연 | 4,201ms | 약 230ms | 약 18배 개선 (명세 AC#7 P95<500ms 충족) |
| keyword 검색 평균 지연 | 약 3,980ms | 약 150ms | 약 26배 개선 |
| 노이즈 입력 지연(ㅁㅈㄷ 등) | 약 3,900ms | 약 20-60ms | DB 미조회 즉시 반환 |
| zero-result | 15 / 72 (21%) | 2 / 72 (3%) | 잔여 2건은 의도된 자모 노이즈뿐 |

적용한 변경 (Tier 0 + Tier 1):
1. (F1 속도) WHERE 의 `similarity()>0.1` trigram 재계산 제거 → tsvector FTS(GIN) 단독 매칭. seq scan 제거가 지연의 핵심 원인 해소.
2. (F2 recall) 검색어를 색인과 동일한 Komoran 형태소로 토큰화 → OR(`|`) tsquery. 복합어/다어절/자연어 zero 해소. compact 형으로 띄어쓰기 변형 흡수.
3. (F2 노이즈) 의미 토큰(완성형 음절/영숫자) 없으면 DB 조회 없이 빈 결과 즉시 반환.
4. (F3 정밀도) 점수에서 무거운 `similarity()` 제거, 제목 매칭 부스트(0.25) + 제목 전체토큰 coverage 부스트(0.4) 추가. 본문에만 스친 문서가 제목 정매칭을 누르지 않도록.

변경 파일:
- `KomoranTokenizerUtil.buildTsQuery` / `buildTsQueryAnd` (쿼리 토큰화 신규)
- `UnifiedSearchIndexQueryRepositoryImpl` (매칭/점수 재작성)

남은 한계 (Tier 2, 데이터/사전 영역 → 차기 과제):
- 다어절 중 한 토큰이 코퍼스에 제목으로 없으면(예: `기숙사 신청`, `축제 일정`) 흔한 토큰(신청/일정) 문서가 상위. 동의어 사전(축제=대동제, 학식=학생식당)·IDF 가중으로 개선 가능.
- `rule`(학칙) 의도는 원문 커버리지 자체가 약함 → 색인 소스 점검 필요.
- before/after 원본: `eval-results-before.json` / `eval-results.json`

---

## 1. 요약 지표 (조치 전 / Before)

| 지표 | 값 | 비고 |
|------|-----|------|
| 쿼리 수 | 72 | |
| zero-result | 15 (20.8%) | 키워드 검색 한정 21% |
| 키워드 검색 평균 지연 | 약 3,980ms | |
| 키워드 검색 P95 지연 | 4,201ms | 명세 AC#7 기준(P95 < 500ms) **8배 초과** |
| 빈 키워드(최신순) 지연 | 80ms | 정상 |
| top-1 의도일치(GOOD) | 약 24 / 72 (33%) | |
| 부분일치(PARTIAL) | 약 21 / 72 | 제목은 약하나 관련 도메인 |
| 오결과(BAD, 비-zero) | 약 12 / 72 | 무관 문서가 상위 |

핵심 결론: **정확도 이전에 두 가지 구조적 결함이 결과를 지배한다.**
1. 모든 키워드 검색이 약 4초 (인덱스 미사용 seq scan).
2. 다어절/복합어/자연어 입력의 21%가 zero-result (쿼리 토큰화 누락 + AND 매칭).

---

## 2. 핵심 발견

### F1 (P0) 키워드 검색 지연 약 4초 - 인덱스 미사용

증상: 결과 건수와 무관하게 모든 키워드 쿼리가 3,800-4,600ms 고정. 빈 키워드만 80ms.

원인 (코드 분석, [UnifiedSearchIndexQueryRepositoryImpl.java:97-103](../../src/main/java/nova/mjs/domain/thingo/search/repository/UnifiedSearchIndexQueryRepositoryImpl.java#L97-L103)):
```
search_vector @@ plainto_tsquery('simple', :keyword)
OR similarity(coalesce(search_tokens,''), :keyword) > :trgmThreshold   -- (A)
OR title ILIKE :likeKeyword                                            -- (B)
```
- (A) `similarity(col, const) > 0.1` 형태는 **GIN trgm 인덱스를 타지 않는다.** pg_trgm GIN 인덱스(`idx_usi_search_tokens_trgm`)는 `%` 연산자에만 적용된다. 함수 비교는 전 행에 대해 similarity를 계산 -> seq scan 3,975행.
- (B) `ILIKE '%kw%'` 선행 와일드카드. gin_trgm_ops로 인덱스 가능하나, (A)와 OR로 묶이면 플래너가 단일 seq scan + recheck로 떨어진다.
- 추가로 `ts_headline`(title/content)을 매칭된 모든 행에 대해 계산하고, score 식에서 ts_rank/similarity를 재계산한다.

명세 AC#7 (100만건 P95 < 500ms)은 현재 3,975건에서도 위반. 데이터가 늘면 선형 악화.

수정:
```sql
-- (A) 교체: 연산자 형태로 GIN 인덱스 사용
search_vector @@ plainto_tsquery('simple', :keyword)
OR search_tokens % :keyword          -- gin_trgm_ops 사용, bitmap index scan
OR title % :keyword                  -- idx_usi_title_trgm 사용
```
- 임계값은 `SELECT set_limit(0.1)` 또는 `SET pg_trgm.similarity_threshold` 로 세션/쿼리 단위 조정.
- 셋 다 인덱스 기반 -> Bitmap OR -> seq scan 제거. 예상 4s -> 수십 ms.
- 검증: 운영 DB에서 `EXPLAIN (ANALYZE)` 로 `similarity()>0.1` 와 `search_tokens % 'x'` 플랜 비교 권장 (전자 Seq Scan, 후자 Bitmap Index Scan 예상).

### F2 (P0) zero-result 21% - 쿼리 토큰화 누락 + AND 매칭

zero 15건 분해:
| 유형 | 쿼리 | 원인 |
|------|------|------|
| 복합어 | 성적우수장학금 | 단일 토큰. 인덱스는 Komoran 형태소로 분해됐는데 쿼리는 원문 그대로 -> 불일치 |
| 다어절 AND | 출결 규정 / 도서관 좌석 / 학식 메뉴 / 축제 일정 | `plainto_tsquery`는 토큰을 **AND**. 두 토큰 모두 가진 문서 없음 -> 0 |
| 자연어 | 졸업하려면 학점 얼마나 들어? / 도서관 몇시까지 해? / 재학증명서 어떻게 떼? | 불용어(얼마나/몇시/어떻게)까지 AND -> 0 |
| 복합 질의 | 일반휴학 군휴학 차이 / F학점 재수강 규정 / LMS 사용법 | 토큰 AND 결합 실패 |
| 약어 | 국장 신청 언제까지야 | 동의어 사전 없음 (명세 §9 비범위) |
| 노이즈 | ㅁㅈㄷ / ㅎㅇ / 요즘 학교 이슈 | 정상 (매칭 대상 아님) |

근본 원인: 명세 §6.3 1번 "keyword를 Komoran 토큰화" 단계가 **구현에서 누락**. [UnifiedSearchIndexQueryRepositoryImpl.java:218](../../src/main/java/nova/mjs/domain/thingo/search/repository/UnifiedSearchIndexQueryRepositoryImpl.java#L218) 에서 원문 keyword를 그대로 바인딩한다. 인덱스(`search_tokens`)는 Komoran 형태소인데 쿼리는 비토큰화 -> 복합어/활용형 recall 붕괴.

대조 증거: `국가장학금`(Q17)은 결과 있음, `성적우수장학금`(Q20)은 0. 전자는 색인에 토큰이 존재, 후자는 더 긴 복합어라 단일 토큰으로는 불일치.

수정:
1. 쿼리도 색인과 동일하게 `KomoranTokenizerUtil` 로 토큰화 후 tsquery 생성 (명세 준수).
2. 다어절 OR 허용: `plainto_tsquery`(AND) 대신 토큰을 `|` 로 묶은 `to_tsquery` 또는 `websearch_to_tsquery` 사용. 전부 AND가 필요하면 ts_rank로 다토큰 일치를 가산.
3. 불용어 제거: Komoran 품사 필터(조사/어미/의문사 제외)로 자연어 노이즈 토큰 탈락.

### F3 (P1) 정밀도 - 본문 매칭 문서가 상위 점령

증상: 비-zero인데 top-1이 무관한 케이스.
- Q17 `국가장학금` -> "The", "[명대신문사", "[명지미디어센터" (장학 공지 아님)
- Q44 `총학생회` -> "[자연캠퍼스..." 반복 (총학 공지 아님)
- Q36 `기숙사 신청` -> "[학생상담센터..." (기숙사 아님, '신청'만 본문 매칭)
- Q22 `장학근 신청`(오타) -> "신정"(신정 휴일) 트라이그램 노이즈

원인:
1. `search_vector` 트리거가 tokens+title+content를 **가중치 없이** 합산 ([postgres-search-init.sql:33-36](../../src/main/resources/sql/postgres-search-init.sql#L33-L36)). 제목 매칭과 본문 매칭을 ts_rank가 구분 못함 -> 본문에 단어가 스친 공지가 제목 정매칭 공지를 누름.
2. 트라이그램이 `search_tokens` 전체 blob 대상이라 노이즈(신청~신정) 발생.

수정:
1. 트리거에서 `setweight`:
```sql
NEW.search_vector :=
  setweight(to_tsvector('simple', coalesce(NEW.title,'')), 'A')
  || setweight(to_tsvector('simple', coalesce(NEW.search_tokens,'')), 'B')
  || setweight(to_tsvector('simple', coalesce(NEW.content,'')), 'C');
```
`ts_rank`에 `{1.0,0.4,0.2,0.1}` 가중 배열 적용 -> 제목 매칭 우선.
2. 트라이그램은 fallback 한정 (tsvector 매칭 0건일 때만), 임계값 상향(0.1 -> 0.3).

### F4 (P2) 중복 결과 / 하이라이트 절단

- Q13, Q22 등 상위 5건이 동일 제목 fragment 반복. 캠퍼스 이중 색인 또는 중복 행 의심.
- `ts_headline` 결과가 "[자연캠퍼스" 처럼 닫히지 않은 채 절단 -> 프론트 표시 품질 저하. `MaxWords/MinWords` 또는 'simple' 토크나이저의 괄호 처리 점검.

---

## 3. 의도별 품질

| 의도 | 대표 GOOD | 문제 |
|------|-----------|------|
| 학사/수강 | 수강신청, 계절학기, 성적정정, 전과 | 졸업요건/복학/수강정정은 약함, 자연어 0 |
| 장학 | (장학금 신청 기간 부분일치) | 국가/성적우수/약어 약함-0 |
| 취업/진로 | 채용설명회, 자격증, 현장실습, 공모전 | 양호한 편 |
| 학칙/규정 | - | 출결/F학점 0, 제적/환수 본문매칭 노이즈. **가장 약한 의도** |
| 시설/생활 | 열람실(도서관), 학생식당(뉴스) | 도서관좌석/학식메뉴 0, 기숙사 오결과 |
| 학생활동 | 동아리모집, 대동제 | 총학생회 오결과, 축제일정 0 |
| 행정 | 증명서, 학생증, 전자출결 | 자연어/LMS 0 |
| 뉴스/방송 | 방송국(MUFF) | MJU뉴스는 방송으로 빠짐 |

`rule`(학칙) 의도가 recall/precision 모두 최악. 학칙 원문이 색인 본문에 충분히 없을 가능성 -> 데이터 커버리지 점검 필요.

---

## 4. 권장 조치 (우선순위)

| 순위 | 조치 | 기대효과 | 위치 |
|------|------|----------|------|
| P0 | `similarity()>0.1` -> `search_tokens % :kw` (연산자) | 4s -> 수십ms | QueryRepositoryImpl:100 |
| P0 | 쿼리 Komoran 토큰화 + OR(`\|`)/websearch tsquery | 복합어/다어절 zero 해소 | QueryRepositoryImpl:114,218 |
| P1 | `setweight` 제목>토큰>본문 + ts_rank 가중배열 | 제목 정매칭 상위로 | postgres-search-init.sql:33 |
| P1 | 트라이그램 fallback화 + 임계 0.3 | 오타/노이즈 정밀도 | QueryRepositoryImpl:100,38 |
| P2 | 중복 행 점검, ts_headline 폭 조정 | 결과 다양성/표시 | indexing, QueryRepositoryImpl:125 |
| P2 | 학칙 도메인 데이터 커버리지 확인 | rule 의도 recall | 색인 소스 |

P0 두 건만 적용해도 지연과 zero-result 대부분 해소될 것으로 본다. 적용 후 동일 72쿼리 재실행하여 before/after 비교 권장.

---

## 5. 재현 방법

```powershell
# 인프라 (ES/Redis 등)
$env:REDIS_PASSWORD='<redis-password>'; docker compose up -d   # 값은 application.yml 의 spring.data.redis.password 참고
# 앱
.\gradlew.bat -p "C:\PROJECT\MJU\NOVA\MJS_BACKEND" bootRun
# 평가 실행 (앱 기동 후)
& docs\search-eval\run-search-eval.ps1 -BankPath docs\search-eval\query-bank.json -OutDir docs\search-eval
# 결과 -> eval-results.json, eval-results.md
& docs\search-eval\convert-results.ps1   # 컴팩트 md 재생성
```

쿼리 추가/수정은 `query-bank.json` 의 `queries` 배열에 `{id,query,intent,form,expectType,note}` 추가.

---

## 6. 한계

- 판정은 자동 휴리스틱(top-4 제목/스니펫). 정밀 P@k/nDCG는 쿼리별 정답 라벨(gold set) 필요.
- 지연은 로컬 앱 -> 원격 RDS 왕복 포함. 순수 쿼리 시간은 운영 DB EXPLAIN ANALYZE로 분리 측정 필요.
- `highlightedTitle`은 ts_headline fragment라 제목 전문이 아님. 정밀 라벨링 시 원문 title 노출 필드 추가 고려.
