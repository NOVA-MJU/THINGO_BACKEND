# 통합검색(PostgreSQL FTS) 개선 보고서

> 대상: `GET /api/v2/search/detail` (명지대 통합검색 v2, PostgreSQL 전환본)
> 작성 목적: 무엇이 문제였고, 어떻게 접근·해결했으며, 결과가 어떻게 바뀌었는지를
> 개발/비개발 구성원 모두가 이해할 수 있도록 맥락과 함께 정리한다.
> 관련 문서: 상세 평가는 [eval-report.md](eval-report.md), 원시 결과는 [eval-results.md](eval-results.md).

---

## 0. 한눈에 보기 (TL;DR)

| 지표 | 개선 전 | 개선 후 | 효과 |
|------|---------|---------|------|
| 검색 응답 P95 | 4,201ms | 약 230ms | 약 18배 |
| 검색 응답 평균 | 약 3,980ms | 약 150ms | 약 26배 |
| 결과 없음(zero-result) | 15/72 (21%) | 2/72 (3%) | 잔여는 의미 없는 입력(ㅁㅈㄷ 등)뿐 |
| 노이즈 입력 응답 | 약 3,900ms | 약 20~60ms | DB 조회 없이 즉시 반환 |

핵심 한 줄: **"검색이 느리고(4초), 자주 결과가 안 나오던(21%)" 상태에서 "빠르고(0.2초) 거의 항상 결과가 나오는" 상태로 전환했다.**

---

## 1. 배경: 검색은 어떻게 동작하나 (비개발자용 설명)

명지대 통합검색은 공지사항·학사일정·학과공지·총학공지·학과일정·커뮤니티·명대뉴스·방송 8종을
하나의 표(`unified_search_index`)에 모아두고 검색한다. 표의 각 행(문서)에는 다음이 있다.

- `title` / `content`: 원문 제목·본문
- `search_tokens`: 제목·본문을 한국어 형태소 분석기(**Komoran**)로 잘게 쪼갠 "검색용 단어 모음"
- `search_vector`: PostgreSQL 전문검색(FTS)이 쓰는 색인 형태(tsvector)

두 가지 검색 기술이 쓰인다.

1. **FTS(전문검색)**: "이 단어가 들어간 문서"를 색인(GIN)으로 매우 빠르게 찾는다. 단어 단위 매칭.
2. **trigram(pg_trgm)**: 글자 3개 단위로 "비슷한 정도(similarity)"를 계산한다. 오타에 강하지만 느릴 수 있다.

비유하자면 FTS는 "책 뒤 색인에서 단어 찾기", trigram은 "철자가 비슷한 단어까지 일일이 비교하기"다.

---

## 2. 문제 (Problem)

검색 품질을 측정하기 위해 명지대 학생이 실제로 검색할 법한 72개 질의(8개 의도 × 9개 입력형:
정확키워드/자연어/띄어쓰기/오타/약어/영문혼용/동의어/모호/노이즈)를 실서버에 던져 결과를 수집했다.
세 가지 구조적 결함이 드러났다.

### 문제 1 — 모든 검색이 약 4초 (치명적)

- 결과 건수와 무관하게 키워드 검색이 일정하게 3,800~4,600ms 소요. (빈 검색만 80ms)
- 원인: 매칭 조건에 `similarity(search_tokens, 검색어) > 0.1` 이 들어 있었다.
  - 이 형태는 trigram **색인을 못 탄다.** 그래서 PostgreSQL이 **전체 행(3,975건)을 하나씩**
    훑으며 KB 크기의 `search_tokens` 문자열과 유사도를 계산했다(=Seq Scan).
  - 점수 계산식에도 같은 `similarity()`가 들어 있어, 매칭된 모든 행에 대해 한 번 더 계산했다.
- 명세 기준(P95 < 500ms)을 8배 초과. 데이터가 늘수록 선형으로 악화되는 구조.

### 문제 2 — 5건 중 1건은 결과가 안 나옴 (recall 21%)

- "출결 규정", "도서관 좌석", "학식 메뉴", "성적우수장학금", "졸업하려면 학점 얼마나 들어?" 등이
  결과 0건이었다.
- 원인 두 가지:
  1. **검색어를 형태소로 안 쪼갬**: 색인은 Komoran으로 잘게 쪼개 저장하는데(`search_tokens`),
     검색할 때는 사용자가 친 원문 그대로 매칭했다. 그래서 "성적우수장학금"처럼 붙여 쓴 복합어가
     색인의 쪼개진 단어들과 안 맞아 0건.
  2. **다어절은 전부 AND**: "출결 규정"은 내부적으로 `출결 AND 규정`으로 처리돼,
     두 단어를 모두 가진 문서가 없으면 0건. 자연어 질문은 불용어(얼마나/어떻게)까지 AND로 묶여 0건.

### 문제 3 — 엉뚱한 문서가 상위 (precision)

- "국가장학금" → 상위에 "명대신문사", "The…" / "총학생회" → "[자연캠퍼스…" 등 무관 문서.
- 원인: 색인(`search_vector`)이 제목·토큰·본문을 **가중치 없이** 합쳤다. 그래서 본문에 단어가
  잠깐 스친 문서가 제목에 정확히 들어간 문서를 누를 수 있었다.

---

## 3. 접근 방법 (Approach)

추측으로 고치지 않고 "측정 → 원인 → 수정 → 재측정"을 반복했다.

```
[1] 평가 하베스트 구축      query-bank.json (72개 질의)
        │                  run-search-eval.ps1 (엔드포인트 호출 → 결과/지연 수집)
        ▼                  convert-results.ps1 (사람이 읽는 표 + 지표 산출)
[2] 기준선(Before) 측정     zero 21%, P95 4.2s 확인 + 결과를 LLM으로 의도일치 판정
        ▼
[3] 코드 기반 원인 분석     similarity()>0.1 비-sargable, 쿼리 토큰화 누락, 가중치 없음
        ▼                  (운영 RDS 직접 EXPLAIN은 미승인 → 코드 분석으로 확정)
[4] 수정 → 재측정 반복      매 수정마다 동일 72질의 재실행, 지연/zero/상위결과 비교
        ▼
[5] 최종 검증              P95 230ms, zero 3%, 상위결과 의도일치 확인
```

측정을 자동화했기에 "고쳤다고 생각"이 아니라 "수치로 증명"할 수 있었고, 회귀(예: '전과' 누락)도
즉시 잡아냈다.

---

## 4. 해결 방법 (Solution)

### 4-1. 검색어도 색인과 똑같이 Komoran으로 쪼갠다 (문제 2 해결)

`KomoranTokenizerUtil`에 검색어 전용 토큰화 메서드 2개를 추가했다.

- `buildTsQuery(검색어)`: 내용 형태소를 뽑아 **OR(`|`)**로 묶은 tsquery 문자열 생성.
  - 예) `"성적우수장학금"` → `성적 | 우수 | 장학금 | 성적우수장학금`
  - 예) `"수강 신청"` → 공백 제거형 `수강신청`도 추가 → 띄어쓰기 변형 흡수
  - 완성형 음절·영숫자가 하나도 없으면(예: `ㅁㅈㄷ`) 빈 문자열 반환
- `buildTsQueryAnd(검색어)`: 같은 형태소를 **AND(`&`)**로 묶음. "모든 단어를 포함하는 문서"를
  골라 가산점(coverage)에 사용.

OR로 묶으니 한 단어만 맞아도 후보로 잡혀 recall이 살고, 정밀도는 아래 점수식으로 보정한다.

### 4-2. 매칭은 색인 타는 FTS 단독으로 (문제 1 해결)

```sql
-- 개선 전 (Seq Scan 유발)
WHERE search_vector @@ plainto_tsquery('simple', :keyword)
   OR similarity(coalesce(search_tokens,''), :keyword) > 0.1   -- 전체 행 스캔
   OR title ILIKE :likeKeyword

-- 개선 후 (GIN 색인 단독)
WHERE search_vector @@ to_tsquery('simple', :tsQuery)          -- idx_usi_search_vector
```

`similarity()>0.1`(색인 못 탐)을 제거하고 색인을 타는 FTS만 남겼다. 이것이 4초→0.2초의 핵심.

### 4-3. 노이즈 입력은 DB도 안 친다

`ㅁㅈㄷ`, `ㅎㅇ`처럼 의미 토큰이 없는 입력은 토큰화 결과가 비므로, **DB 조회 없이 빈 결과를 즉시 반환**한다.
(개선 전엔 이런 입력도 trigram 전체 스캔으로 약 3.9초 걸렸다.)

### 4-4. 제목 매칭을 우대하는 점수식 (문제 3 해결)

```
score = ts_rank(search_vector, OR쿼리) * 0.6
      + (제목에 검색어 토큰 중 하나라도 있으면)  +0.25
      + (제목에 모든 토큰이 있으면, AND쿼리)      +0.40   ← coverage 부스트
      + popularity * 0.0001
      + 카테고리 가중치 (general 0.10 … rule 0.00)
      + 타입 가중치     (NOTICE 0.10 … BROADCAST 0.01)
      + 인기검색어 부스트(실시간 top-10이 제목에 매칭 시 +0.05)
```

- 무거운 `similarity()`를 점수식에서 **제거**(지연 추가 해소).
- 제목 매칭/제목 전체커버리지 가산점으로, 본문에만 스친 문서보다 제목 정매칭 문서를 위로 올림.
- 이 가산점들은 **WHERE로 이미 좁혀진 소수 행에만** 계산되므로 성능 부담이 없다.

---

## 5. 변경 구조도 (Architecture)

### 검색 처리 흐름 (개선 후)

```
사용자 검색어
   │
   ▼
PgSearchController  (GET /api/v2/search/detail)
   │
   ▼
PgUnifiedSearchService   ── 인기검색어 부스트 패턴 조립, 타입/카테고리 정규화
   │
   ▼
UnifiedSearchIndexQueryRepositoryImpl
   │   1) KomoranTokenizerUtil.buildTsQuery   → OR  tsquery   (recall)
   │   2) KomoranTokenizerUtil.buildTsQueryAnd → AND tsquery   (coverage 가산점)
   │   3) 의미 토큰 없으면 → 빈 결과 즉시 반환 (DB 미조회)
   │   4) WHERE search_vector @@ to_tsquery   (GIN 색인)
   │   5) score = ts_rank + 제목부스트 + coverage + popularity + 가중치
   ▼
PostgreSQL (unified_search_index, GIN 색인)
   │
   ▼
SearchResultRow → SearchResponseDTO (하이라이트 포함)
```

### 변경/추가 파일

| 파일 | 변경 | 내용 |
|------|------|------|
| `config/elasticsearch/KomoranTokenizerUtil.java` | 수정 | `buildTsQuery`/`buildTsQueryAnd` 추가 (검색어 토큰화) |
| `domain/thingo/search/repository/UnifiedSearchIndexQueryRepositoryImpl.java` | 수정 | 매칭/점수식 재작성, 노이즈 단락, trigram 제거 |
| `resources/static/openapi/search-v2.json` | 수정 | v2 API 명세 갱신(매칭/점수/에러/성능) |
| `test/.../KomoranTokenizerUtilTsQueryTest.java` | 추가 | 토큰화 단위 테스트(복합어/띄어쓰기/노이즈/AND) |
| `docs/search-eval/*` | 추가 | 평가 하베스트·질의셋·리포트 |

> 인덱스 스키마(`search_vector` 트리거)는 **변경하지 않았다.** 제목 우대를 트리거(setweight)
> 대신 쿼리 점수식으로 구현해, 운영 데이터 재색인 없이 적용했다.

---

## 6. 트래킹 / 재현 방법 (How to reproduce)

평가는 누구나 재현할 수 있도록 스크립트화돼 있다.

```powershell
# 0) 인프라(ES/Redis 등)
$env:REDIS_PASSWORD='<redis-password>'; docker compose up -d   # 값은 application.yml 의 spring.data.redis.password 참고

# 1) 앱 기동
./gradlew bootRun        # 또는 .\gradlew.bat -p <repo> bootRun

# 2) 72개 질의 평가 실행 (앱 기동 후)
& docs/search-eval/run-search-eval.ps1 `
    -BankPath docs/search-eval/query-bank.json -OutDir docs/search-eval
#   → eval-results.json (원시), eval-results.md (사람이 읽는 표 + 지표)

# 3) 컴팩트 표/지표 재생성
& docs/search-eval/convert-results.ps1
```

- **질의 추가/수정**: `query-bank.json`의 `queries`에 `{id, query, intent, form, expectType, note}` 추가.
- **Before/After 비교**: 변경 전 `eval-results.json`을 `eval-results-before.json`으로 백업 → 변경 후
  재실행 → 두 파일 비교(zero-result 목록, 지연, 상위결과).
- **지표 자동 산출**: zero-result 수, 평균/ P95 지연, 타입 분포를 스크립트가 출력.

---

## 7. 최종 성과 (Results)

### 정량

- **지연**: 키워드 검색 P95 4,201ms → 약 230ms, 평균 약 150ms (warmup 후). 명세 P95<500ms 충족.
- **recall**: zero-result 15건 → 2건. 잔여 2건은 의미 없는 자모 입력(`ㅁㅈㄷ`, `ㅎㅇ`)으로 정상.
- **노이즈**: 약 3,900ms → 20~60ms.

### 정성 (상위 결과 의도일치)

- 개선됨: `총학생회`(→ 총학생회 기사), `전자출결`(→ 전자출결 사용법), `수강 신청`(→ 수강신청 공지),
  `대동제`/`학생식당`/`교환학생` 등 의도에 맞는 문서가 상위로.
- `성적우수장학금`·`출결 규정`·`도서관 좌석` 등 **이전 0건이 정상 노출**.

---

## 8. 남은 과제 (Future Work)

현재 한계는 대부분 **데이터/사전 영역**으로, 랭킹 미세조정이 아니라 다음 단계 과제다.

1. **동의어·약어 사전**: `대동제=축제`, `학식=학생식당`, `국장=국가장학금`, `LMS=학습관리시스템`.
   학생 체감이 가장 큰 개선. ES Nori용 `synonyms.txt`가 이미 있어 재활용 가능.
2. **희소어 가중(IDF)**: "기숙사 신청"에서 흔한 토큰 `신청`이 결과를 점령하는 문제. 드문 단어
   (`기숙사`)에 더 큰 가중을 주면 완화.
3. **학칙(rule) 도메인 커버리지**: 원문이 색인 본문에 충분히 들어와 있는지 점검 필요.
4. **정밀 지표화**: 현재는 LLM 휴리스틱 판정. 질의별 정답 라벨(gold set)을 만들면 P@k/nDCG로
   수치화하여 회귀 검증 가능.
5. **통합 테스트 재활성화**: `UnifiedSearchIndexQueryRepositoryImplIT`는 `@DataJpaTest` 슬라이스
   컨텍스트 wiring 미완성으로 `@Disabled` 상태. 슬라이스 설정 정리 후 재활성화.

---

## 부록. 변경 핵심 코드 (요약)

```java
// KomoranTokenizerUtil — 검색어를 OR tsquery 로
public static String buildTsQuery(String text) {
    // Komoran 내용 형태소(NN*/SL/SH/SN) + 공백제거 compact + 공백 토큰
    // → 의미 있는 항만(완성형 음절/영숫자) "a | b | c" 형태로 결합, 없으면 ""
}

// UnifiedSearchIndexQueryRepositoryImpl — 매칭/점수
// WHERE: search_vector @@ to_tsquery('simple', :tsQuery)         (GIN, seq scan 없음)
// SCORE: ts_rank*0.6 + 제목매칭 0.25 + 제목 coverage(AND) 0.4 + popularity + 가중치 + hotBoost
// 토큰 없는 노이즈 입력은 DB 조회 없이 빈 Page 즉시 반환
```
