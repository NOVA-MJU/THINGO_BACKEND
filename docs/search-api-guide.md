# 통합검색 API 사용 가이드 (v2 / PostgreSQL)

> 이 문서는 프론트엔드/클라이언트 개발자가 **통합검색 v2 API**를 처음 봐도 바로 쓸 수 있도록
> 쉬운 말로 풀어 쓴 가이드입니다. 기계용 스펙(OpenAPI)은 `/openapi/search-v2.json`(스웨거의
> "검색 기능 (v2 / PostgreSQL FTS)" 탭)에 있고, 이 문서는 그 "친절한 설명서" 버전입니다.

---

## 0. 한눈에 보기

검색 API는 엔드포인트 3개가 전부입니다.

| 하는 일 | 메서드/경로 | 누가 쓰나 |
|---------|-------------|-----------|
| **검색 / 전체목록 보기** | `GET /api/v2/search/detail` | 프론트(메인 기능) |
| **자동완성** | `GET /api/v2/search/suggest` | 프론트(검색창 입력 중) |
| **색인 전체 재구축** | `POST /api/v2/search/sync` | 운영자만 |

> v1(`/api/v1/search/*`)과 **요청·응답 형식이 완전히 같습니다.** 엔진만 Elasticsearch에서
> PostgreSQL로 바뀌었습니다. 그래서 프론트는 경로의 `v1`을 `v2`로만 바꾸면 끝입니다(필드 변경 0).

---

## 1. 가장 중요한 한 가지: `detail`은 두 가지 모드다

`GET /api/v2/search/detail` 하나가 **검색**과 **전체목록 보기**를 겸합니다. 차이는 딱 하나,
**`keyword`를 줬느냐**입니다.

### 모드 A. 키워드 검색 (`keyword` 있음)

검색어와 관련 있는 글을 **관련도 순(`relevance`)**으로 돌려줍니다.

```
GET /api/v2/search/detail?keyword=장학금 신청
```

→ "장학금 신청"과 가장 잘 맞는 공지가 위로 옵니다.

### 모드 B. 전체검색 / 목록 보기 (`keyword` 비움)

검색어 없이 **필터(type/category)와 정렬만**으로 글 목록을 훑어볼 때 씁니다. 검색어가 없으니
관련도를 매길 수 없어서, 이때는 자동으로 **최신순(`latest`)**으로 정렬됩니다.

```
GET /api/v2/search/detail                       ← 전체를 최신순으로
GET /api/v2/search/detail?type=NEWS             ← 명대뉴스만 최신순으로
GET /api/v2/search/detail?type=NOTICE&category=scholarship   ← 장학 공지만 최신순으로
GET /api/v2/search/detail?type=NOTICE&order=oldest           ← 공지를 오래된 순으로
```

> 정리: **검색창에 글자를 치면 모드 A**, **탭/카테고리만 누르고 둘러보면 모드 B**.
> 같은 엔드포인트라 프론트에서 분기할 필요가 없습니다. `keyword`를 비워서 호출하면 됩니다.

---

## 2. 요청 파라미터 (전부 선택값)

| 파라미터 | 뜻 | 기본값 | 예시 / 주의 |
|----------|-----|--------|-------------|
| `keyword` | 검색어 | `""`(빈값) | 비우면 위의 **모드 B(목록)**. `장학금 신청` |
| `type` | 상위 탭(어느 도메인) | 없음=전체 | 아래 표 참고. **대소문자 무시**. 모르는 값 주면 400 |
| `category` | 세부 카테고리 | 없음=전체 | **대소문자 구분**. `scholarship` |
| `order` | 정렬 | `relevance` | `relevance`/`latest`/`oldest`. keyword 비면 자동 `latest` |
| `page` | 페이지 번호(0부터) | `0` | `0`=첫 페이지 |
| `size` | 페이지당 개수 | `10` | 최대 `100` |

### `type` 값 (검색 가능한 도메인 8종)

| type 값 | 무엇 |
|---------|------|
| `NOTICE` | 학교 공지사항 |
| `MJU_CALENDAR` | 학사일정 |
| `DEPARTMENT_NOTICE` | 학과 공지 |
| `STUDENT_COUNCIL_NOTICE` | 총학생회 공지 |
| `DEPARTMENT_SCHEDULE` | 학과 일정 |
| `COMMUNITY` | 커뮤니티 글 |
| `NEWS` | 명대신문(뉴스) |
| `BROADCAST` | 명지대 방송국 |

> 대소문자는 무시하므로 `notice`, `Notice`, `NOTICE` 모두 같습니다. 목록에 없는 값(`foo` 등)을
> 주면 `400` 오류가 납니다(아래 5장).

### `category` 값 (type별로 다름)

| type | 가능한 category |
|------|-----------------|
| `NOTICE` | `general`, `academic`, `scholarship`, `career`, `activity`, `rule` |
| `NEWS` | `REPORT`, `SOCIETY` |
| `COMMUNITY` | `FREE`, `NOTICE` |
| 그 외(MJU_CALENDAR 등) | 카테고리 없음(주지 마세요) |

---

## 3. 정렬(`order`) 3종

| 값 | 의미 | 언제 |
|----|------|------|
| `relevance` | 검색어와 잘 맞는 순(관련도) | 검색어 있을 때 기본 |
| `latest` | 최신 날짜 순 | 목록 둘러보기. keyword 비면 자동 적용 |
| `oldest` | 오래된 날짜 순 | 과거 글부터 보고 싶을 때 |

> `latest`/`oldest`로 정렬하면 응답의 `score` 값은 의미가 없습니다(날짜로 줄 세운 것이라).

---

## 4. 응답 형식

공통 봉투(`ApiResponse`) 안에 페이지(`Page`)가 들어 있습니다.

```json
{
  "status": "API 요청 성공",
  "data": {
    "content": [ /* 결과 글들 (아래 표) */ ],
    "totalElements": 149,   // 조건에 맞는 전체 개수
    "totalPages": 15,       // 전체 페이지 수
    "size": 10,             // 페이지당 개수
    "number": 0,            // 현재 페이지(0부터)
    "first": true,
    "last": false,
    "numberOfElements": 10, // 이 페이지에 담긴 개수
    "empty": false          // 결과 없으면 true
  },
  "timestamp": "2026-06-18T10:00:00.000000"
}
```

### `content[]` 안의 글 하나

| 필드 | 뜻 | 비고 |
|------|-----|------|
| `id` | 문서 ID | `TYPE:원본ID` 형식. 예 `NOTICE:13317` |
| `highlightedTitle` | 화면에 그대로 출력할 제목 | 검색어가 `<em>...</em>`로 강조됨. 매칭 없으면 원문 |
| `highlightedContent` | 화면에 그대로 출력할 본문 미리보기 | 위와 동일하게 강조 |
| `date` | 발행/정렬 기준 시각 | ISO8601(Instant) |
| `link` | 원문 링크(클릭 시 이동) | |
| `category` | 세부 카테고리 | 없을 수 있음(null) |
| `type` | 도메인 종류 | **소문자**로 내려감. 예 `notice`, `news` |
| `imageUrl` | 썸네일 | 없을 수 있음 |
| `score` | 관련도 점수 | `relevance`일 때만 의미. 정렬용 내부값 |
| `authorName` | 작성자 | 없을 수 있음 |
| `likeCount` | 좋아요 수 | 없을 수 있음 |
| `commentCount` | 댓글 수 | 없을 수 있음 |

> `highlightedTitle`/`highlightedContent`는 "그대로 출력하면 되는 최종 텍스트"입니다. 프론트에서
> 따로 자르거나 강조 처리할 필요 없이 `<em>` 태그만 스타일링하면 됩니다.

---

## 5. 오류

| 상황 | 응답 | 메시지 예 |
|------|------|-----------|
| `type`에 모르는 값 | `400 ILLEGAL_ARGUMENT` | `지원하지 않는 검색 타입입니다: foo` |
| 자동완성에 `keyword` 누락 | `400 BAD_REQUEST` | `Required request parameter 'keyword' ... is not present` |
| 서버 내부 오류 | `500 INTERNAL_SERVER_ERROR` | |

오류 형식:

```json
{ "status": 400, "error": "ILLEGAL_ARGUMENT", "message": "지원하지 않는 검색 타입입니다: foo" }
```

> 참고: **결과가 0건인 것은 오류가 아닙니다.** `200`에 빈 배열(`content: []`, `empty: true`)로 옵니다.
> 예를 들어 `ㅁㅈㄷ`처럼 의미 없는 입력은 곧바로 빈 결과를 돌려줍니다(서버가 헛고생하지 않도록).

---

## 6. 결과는 어떤 기준으로 정렬되나 (관련도 점수)

`relevance`일 때 글마다 점수를 매겨 높은 순으로 보여줍니다. 점수는 대략 이렇게 만들어집니다.

```
점수 = (검색어와 본문/제목이 얼마나 맞나)        … 기본 관련도(ts_rank)
     + (제목에 검색어가 하나라도 있으면 가점)
     + (제목에 검색어 단어가 전부 있으면 큰 가점)   … 가장 정확한 글을 위로
     + 인기도(좋아요·댓글, 최근 글일수록 ↑) 소폭
     + 도메인/카테고리 가중치 소폭
     + 실시간 인기검색어와 겹치면 보너스
     + 유효성(기한) 반영                          … 아래 설명
```

즉 **제목이 검색어와 딱 맞는 글**이 가장 위로 오고, 본문에 잠깐 스친 글은 아래로 갑니다.

### 유효성(기한) 반영

같은 키워드라면 **지금 유효한 공지가 먼저** 보이도록 기한을 점수에 반영합니다.

- **기한이 지난(마감된) 공지** → 후순위로 내려갑니다. (예: 신청 마감일이 지난 장학 공지)
- **아직 유효한(마감 전) 공지** → 소폭 우대해 위로 올립니다.
- **기한이 없는 문서(학칙 등)** → 만료로 보지 않습니다. 오히려 학칙처럼 "항상 유효하고 자주
  찾는" 문서는 관련 검색에서 잘 노출되도록 우대합니다.

기한은 학사일정·학과일정은 일정의 종료일을, 공지는 본문에서 마감일을 추정해 판단합니다.
추정이 안 되면 "기한 없음(무기한)"으로 보아 감점하지 않습니다(안전 측 기본값).

> 주의: 이 보정은 `relevance` 정렬에만 적용됩니다. `latest`/`oldest`(날짜순)는 날짜 의미를
> 그대로 유지하기 위해 기한 보정을 적용하지 않습니다. 또한 응답 필드는 그대로이며(스키마 불변),
> 바뀌는 것은 **순서**뿐입니다.

---

## 7. 자동완성 `GET /api/v2/search/suggest`

검색창에 입력하는 중에 추천어를 보여줄 때 씁니다.

```
GET /api/v2/search/suggest?keyword=장학
→ { "status": "API 요청 성공", "data": ["장학금 안내", "장학사업 변경", "장학 신청"], "timestamp": "..." }
```

- `keyword`는 **필수**(없으면 400).
- 앞글자 일치 + 비슷한 글자(오타 보정)로 최대 10개 제목을 돌려줍니다. 빈 입력이면 빈 배열.

---

## 8. 검색 데이터는 어떻게 최신으로 유지되나 (동기화)

- **평상시(자동):** 공지·뉴스 등 원본이 추가/수정/삭제되면, 그 변경이 커밋된 직후 검색 색인에
  자동 반영됩니다. 운영자가 따로 할 일이 없습니다.
- **전체 재구축(수동, 운영 전용):** `POST /api/v2/search/sync`. 색인을 통째로 다시 만듭니다.
  평상시엔 호출할 필요가 없고, 색인이 꼬였을 때만 운영자가 씁니다.

```
POST /api/v2/search/sync
→ { "status": "API 요청 성공", "data": "Success Indexing", "timestamp": "..." }
```

---

## 9. 자주 묻는 것

- **검색이랑 목록을 따로 호출해야 하나요?** 아니요. `detail` 하나로 됩니다. `keyword`를 비우면 목록 모드.
- **type을 소문자로 보내도 되나요?** 네. 대소문자 무시합니다. 단 `category`는 대소문자를 구분합니다.
- **`score`로 정렬 보장되나요?** `relevance`일 때만요. `latest`/`oldest`는 날짜순이라 `score`는 무시하세요.
- **v1이랑 응답이 다르나요?** 같습니다. 필드·형식 동일. 경로의 버전만 다릅니다.

---

## 부록. 빠른 예시 모음

```
# 키워드 검색(관련도순)
GET /api/v2/search/detail?keyword=수강신청

# 자연어로 검색
GET /api/v2/search/detail?keyword=졸업하려면 학점 얼마나 들어?

# 공지 탭에서 장학 카테고리만, 최신순 목록
GET /api/v2/search/detail?type=NOTICE&category=scholarship&order=latest

# 검색어 없이 전체 최신 목록 2페이지(11~20번째)
GET /api/v2/search/detail?page=1&size=10

# 자동완성
GET /api/v2/search/suggest?keyword=기숙

# (운영자) 색인 전체 재구축
POST /api/v2/search/sync
```
