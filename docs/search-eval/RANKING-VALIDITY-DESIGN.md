# 통합검색 v2 — 유효성(기한) 기반 재랭킹 설계

> 목표: 검색 결과에서 **기한이 끝난(만료된) 공지는 후순위**로 내리고, **지금 유효한 공지**를
> 위로 올린다. **학칙처럼 기한이 없는 문서(evergreen)**는 만료로 보지 않고, 관련도가 맞으면
> 상위 노출되도록 한다. v1 API 와 **요청/응답 스키마는 100% 동일**하게 유지한다(순위만 바뀐다).

---

## 0. 핵심 결정 (확정)

| 항목 | 결정 |
|------|------|
| 유효성 신호 | `valid_until` 컬럼 신설. 소스 endDate(캘린더/학과일정) + 공지 본문 파싱 |
| 만료 처리 | `valid_until < now` → 점수 강한 감점(후순위) |
| 무기한(학칙) | `valid_until IS NULL` → 만료 아님. `category='rule'` 가중치 0.00 → evergreen 부스트로 상향 |
| 자동 정합성 | 야간 **reconcile**(차이만 upsert/비활성), truncate 안 함 → 빈 결과 구간 없음 |
| 성능 재측정 | Testcontainers IT 복구 → 격리 PG16 에서 EXPLAIN ANALYZE 로 seq-scan→index 증명 |

---

## 1. 스키마 변경 (응답 스키마 불변)

### 1.1 엔티티

`UnifiedSearchIndex` 에 컬럼 1개 추가. **응답 DTO(`SearchResponseDTO`)에는 추가하지 않는다**
→ 프론트 계약 불변, 순위 계산용 내부 컬럼.

```
+ private Instant validUntil;   // nullable. null = 무기한(만료 개념 없음)
```

- `of(...)` 정적 팩토리 시그니처에 `Instant validUntil` 추가.
- `updateFrom(source)` 에 `this.validUntil = source.validUntil` 추가.
- `ddl-auto=update` 가 `valid_until TIMESTAMPTZ NULL` 컬럼 생성.

### 1.2 인덱스 (postgres-search-init.sql, 멱등)

```sql
-- 만료 감점은 매칭된 소수 행에만 계산되므로 필수는 아니나,
-- 목록 조회/향후 필터 대비 partial index 추가(선택).
CREATE INDEX IF NOT EXISTS idx_usi_valid_until
    ON unified_search_index (valid_until);
```

트리거(`usi_update_search_vector`)는 `valid_until` 과 무관 → **변경 없음**.

---

## 2. valid_until 채우기 (인덱싱 단계)

### 2.1 SearchDocument 인터페이스에 default 추가

```java
default Instant getValidUntil() { return null; }   // 기본 무기한
```

도메인별 override:

| 타입 | getValidUntil() |
|------|-----------------|
| MJU_CALENDAR | `endDate` (소스 보유, 현재 버려짐) → 해당 날짜 23:59:59 |
| DEPARTMENT_SCHEDULE | `endDate` 동일 |
| NOTICE | `DeadlineExtractor.extract(content)` (본문 파싱, 실패 시 null) |
| NEWS / COMMUNITY / BROADCAST / STUDENT_COUNCIL_NOTICE | null (무기한 취급) |

### 2.2 DeadlineExtractor (신규 util)

`domain/thingo/search/indexing/DeadlineExtractor.java`

```java
Optional<Instant> extract(String content);
```

- 한국어 마감/기간 패턴 정규식:
  - `YYYY.MM.DD` / `YYYY-MM-DD` / `YYYY. M. D`
  - 기간 `... ~ 2026.03.15`, `2026.3.1 ~ 2026.3.15` → **끝 날짜** 채택
  - `마감`, `신청기간`, `까지`, `제출기한` 키워드 근처 날짜 우선
- 여러 날짜 → **가장 늦은 날짜**를 valid_until 로 (보수적: 기간 종료 시점).
- 연도 생략(`3월 15일`) → 발행연도 기준 추정(애매하면 null).
- 파싱 실패/모호 → `null` (무기한으로 취급, 감점 안 함 = 안전 측).

> best-effort. 틀려도 "감점 안 함"이 기본값이라 결과를 망가뜨리지 않는다.

### 2.3 매퍼

`PgUnifiedSearchMapper.from(doc)` 에서 `doc.getValidUntil()` 을 읽어
`UnifiedSearchIndex.of(..., validUntil)` 로 전달.

---

## 3. 랭킹 변경 (UnifiedSearchIndexQueryRepositoryImpl)

`order=relevance` 의 `scoreExpr` 에 두 항 추가. **WHERE/필터/응답은 불변.**

```
-- (신규) 만료 감점: 지금보다 과거에 끝난 문서는 강하게 후순위
+ CASE WHEN valid_until IS NOT NULL AND valid_until < now()
       THEN -0.50 ELSE 0 END

-- (신규) 유효 부스트: 아직 유효한(미래 마감) 문서 소폭 가점
+ CASE WHEN valid_until IS NOT NULL AND valid_until >= now()
       THEN +0.10 ELSE 0 END
```

evergreen(학칙) 처리 — 카테고리 가중치 표 수정:

```
[전] WHEN 'rule' THEN 0.00     -- 최하위 (학칙이 안 뜨던 원인)
[후] WHEN 'rule' THEN 0.08     -- evergreen 우대 (academic 급)
```

결과 우선순위(같은 관련도 기준):

```
유효한 공지(미래 마감)  >  무기한/학칙  >  만료된 공지
        +0.10                 0(+rule 0.08)        -0.50
```

- `-0.50` 은 ts_rank(0~1)+제목부스트(0.25/0.40) 합을 충분히 눌러 만료 문서를
  같은 키워드의 유효 문서 **아래**로 보낸다. 단, 만료라도 키워드가 강하게 맞으면
  완전히 사라지진 않음(후순위로만).
- `order=latest/oldest` 는 날짜 정렬 의미 유지 위해 **감점 미적용**(date 기준 그대로).
  → 스펙(정렬 의미) 보존. 문서에 명시.

> 상수(-0.50/+0.10/0.08)는 평가셋(72문항) 재실행으로 튜닝. 초기값은 보수적.

---

## 4. 자동 정합성 — 야간 reconcile

### 4.1 현재 상태

- 실시간: `PgUnifiedSearchIndexListener` 가 AFTER_COMMIT 으로 도메인 변경을 upsert (이미 자동).
- 수동: `POST /sync` = truncate + 전체 재적재 (운영자 호출).
- 문제: 이벤트 누락/실패 시 drift. 수동 sync 는 빈 결과 구간 발생.

### 4.2 신규 reconcile (truncate 없음)

`PgSearchIndexSyncService.reconcile()`:

```
1) 모든 도메인 소스 → 원하는 인덱스 상태 map<id, UnifiedSearchIndex> 구성 (syncAll 과 동일 변환)
2) 기존 인덱스 행 조회
3) diff:
   - 소스에 있고 인덱스에 없음/내용 다름 → upsert (updateFrom or save)
   - 인덱스 active=true 인데 소스에 없음 → deactivate() (삭제 아님, 안전)
4) truncate 안 함 → 검색 빈 구간 없음 (무중단)
```

변경 감지: 엔티티에 `boolean differsFrom(UnifiedSearchIndex other)` 추가
(title/content/category/date/validUntil/popularity/active 비교).

### 4.3 스케줄

`SchedulerService` 에 추가 (기존 패턴: CompletableFuture + try/catch):

```java
@Scheduled(cron = "0 0 4 * * *")   // 매일 04:00 (저트래픽)
public void scheduledReconcileSearchIndex() { ... syncService.reconcile() ... }
```

- 의존성 주입: `PgSearchIndexSyncService` 추가.
- ⚠️ 다중 인스턴스: 동시 실행 시 중복 작업(멱등이라 결과는 안전). 정식 분산락은
  ShedLock 도입을 후속 과제로(현재 단일 인스턴스 가정). 문서에 명시.

---

## 5. 성능 재측정 — Testcontainers IT (prod 무접촉)

### 5.1 왜 Testcontainers

- 운영 DB = 원격 AWS RDS, `ddl-auto=update`. 여기서 bootRun/sync = **운영 인덱스 truncate**.
  → 절대 직접 측정 금지.
- 격리 PG16 컨테이너는 우리가 소유 → **EXPLAIN ANALYZE 가능**(prod 에선 권한 막혀 못 했던 것).

### 5.2 측정 설계

`UnifiedSearchIndexQueryRepositoryImplIT` `@Disabled` 제거 + 보강:

1. PG16 + `pg_trgm` + `postgres-search-init.sql` 적용.
2. 샘플 N건 적재(공지/뉴스/캘린더 혼합, 만료/유효/학칙 섞기).
3. 두 쿼리 형태를 **같은 데이터**에 EXPLAIN ANALYZE:
   - before: `... OR similarity(search_tokens, :kw) > 0.1` (함수 비교 = seq scan)
   - after: `search_vector @@ to_tsquery(...)` (GIN index scan)
4. 단언:
   - before 플랜에 `Seq Scan` 존재, after 플랜에 `Bitmap Index Scan`/`Index Scan`.
   - after 실행시간 < before (비율 기록).
5. 랭킹 검증(신규):
   - 만료 공지가 동일 키워드 유효 공지보다 **아래** rank.
   - `category='rule'` 학칙이 관련 키워드에서 노출(0건 아님).

### 5.3 보고 방식 (정직)

- 절대 ms 는 로컬/컨테이너 기준 → prod 와 다름. **포터블 주장 = 플랜 차이(seq→index)와 비율**.
- 기존 보고서의 prod 엔드포인트 수치(4,201ms→230ms)는 그대로 두고,
  IT 결과는 "재현 가능한 근거(EXPLAIN)"로 병기.

---

## 6. API 명세서 재작성

- 신규: `docs/search-api-guide.md` — 풍부한 맥락 + 쉬운 언어.
  - 각 엔드포인트가 "무엇을/왜", 파라미터 평이한 예시, 응답 필드 의미,
    랭킹 규칙(유효성 포함) 설명, 에러, 동기화 동작.
- `openapi/search-v2.json` 설명에 유효성 랭킹 1단락 추가(머신 스펙 동기화).
- 응답 스키마/필드 **변경 없음** 재확인 문구 포함.

---

## 7. 단계별 구현 계획 (TDD, 각 단계 승인 게이트)

| 단계 | 내용 | 테스트(Red 먼저) |
|------|------|-----------------|
| P1 | `DeadlineExtractor` + 엔티티 `validUntil` | 파서 단위테스트(패턴별 케이스) |
| P2 | SearchDocument/도메인 Document `getValidUntil` + 매퍼 | 매퍼 테스트(캘린더 endDate, 공지 파싱) |
| P3 | 랭킹 SQL(만료 감점/유효 부스트/rule 상향) | IT: 만료<유효, rule 노출 |
| P4 | `reconcile()` + 스케줄 | 서비스 테스트(upsert/deactivate diff) |
| P5 | Testcontainers IT 복구 + 성능/EXPLAIN | IT 통과 + 수치 산출 |
| P6 | `search-api-guide.md` + openapi 갱신 | 문서 |

> 스펙 불변 보증: P1~P5 어디서도 `SearchResponseDTO` 필드/엔드포인트/파라미터를
> 추가·변경하지 않는다. 바뀌는 것은 **결과 순서**뿐.
