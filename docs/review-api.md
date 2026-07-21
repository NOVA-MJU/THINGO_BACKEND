# 명지도 장소 리뷰 API 가이드

명지도 **장소**에 대한 리뷰(별점 없는 키워드 + 텍스트 + 사진/영상 후기) 기능입니다.
프론트엔드가 바로 붙일 수 있도록 요청/응답 예시, 필드 설명, 에러 케이스, 업로드 절차까지 담았습니다.

- 화면설계 기준: `05-2-3-2`(장소 상세 리뷰 영역), `05-2-5`(리뷰 작성), `05-2-6`(사진/영상 확대), `05-2-7`(리뷰 상세)
- OpenAPI(Swagger) 원문: `/openapi/review.json`
- 관련 문서: [신고/차단 API](report-block-api.md)

---

## 목차
1. [핵심 개념 30초 요약](#1-핵심-개념-30초-요약)
2. [공통 규칙(응답 래퍼 · 인증 · 에러 형태)](#2-공통-규칙)
3. [엔드포인트 한눈에 보기](#3-엔드포인트-한눈에-보기)
4. [리뷰 작성](#4-리뷰-작성)
5. [리뷰 목록 / 상세](#5-리뷰-목록--상세)
6. [리뷰 삭제](#6-리뷰-삭제)
7. [좋아요 토글](#7-좋아요-토글)
8. [사진·영상 스트립](#8-사진영상-스트립)
9. [키워드 카탈로그](#9-키워드-카탈로그)
10. [미디어 업로드 절차(이미지 · 영상)](#10-미디어-업로드-절차)
11. [키워드 전체 표](#11-키워드-전체-표)
12. [신고 · 차단 · 자동 숨김(모더레이션)](#12-신고--차단--자동-숨김)
13. [운영자 검토 API](#13-운영자-검토-api)
14. [에러 코드 카탈로그](#14-에러-코드-카탈로그)
15. [자주 묻는 질문](#15-자주-묻는-질문)

---

## 1. 핵심 개념 30초 요약

- 리뷰는 **키워드 태그(1~5개, 필수)** + **본문 텍스트(≤400자, 필수)** + **사진/영상(0~10개, 선택)** 으로 구성됩니다.
- 리뷰는 **장소(PLACE)** 에만 달 수 있어요. **동아리방·흡연부스는 리뷰가 없습니다**(대신 커뮤니티 영역).
- 키워드는 장소 성격에 따라 노출이 달라집니다. **음식점(F&B)** 이면 '음식/가격' 키워드가 보이고, 아니면 숨겨집니다.
- 좋아요는 게시판과 똑같이 토글됩니다. **좋아요 알림(푸시)은 없습니다.**
- 영상은 **앱 서버를 거치지 않고 S3로 직접 업로드**합니다(프리사인).
- 본문의 **비속어는 자동으로 마스킹**되고, 신고가 쌓이면 **자동으로 숨겨집니다**.

---

## 2. 공통 규칙

### 응답 래퍼
성공 응답은 항상 이 형태로 감쌉니다.
```json
{
  "status": "API 요청 성공",
  "data": { /* 실제 값 */ },
  "timestamp": "2026-07-14T12:30:05"
}
```
> 목록은 `data`가 Spring `Page` 객체(`content`, `totalElements`, `totalPages`, `number`, `size`)입니다.
> 삭제(204)처럼 본문이 없는 응답도 있습니다.

### 인증
| 구분 | 인증 | 설명 |
| --- | --- | --- |
| 조회(목록/상세/미디어/키워드) | 선택 | 비로그인도 가능. 로그인하면 `isLiked`/`isMine`이 채워짐 |
| 작성 / 삭제 / 좋아요 / 프리사인 | 필수 | 헤더 `Authorization: Bearer <accessToken>` |
| 운영자 검토(모더레이션) | 필수 + OPERATOR | OPERATOR 권한 계정만 |

로그인 없이 작성/삭제/좋아요를 호출하면 **403**이 반환됩니다.

### 에러 응답 형태
에러는 아래 형태로 **일관되게** 내려갑니다(래퍼가 아니라 이 형태 그대로).
```json
{
  "status": 400,
  "error": "REVIEW_KEYWORD_COUNT_INVALID",
  "message": "[MJS] 키워드는 1개 이상 5개 이하로 선택해야 합니다."
}
```
- `status`: HTTP 상태 코드(숫자)
- `error`: 프론트 분기용 **에러 코드 문자열**(안정적 키)
- `message`: 사용자 노출용 한국어 메시지

> 잘못된 JSON이나 잘못된 enum 값(예: 없는 키워드 코드)을 보내면 500이 아니라 **400 `MALFORMED_REQUEST_BODY`** 로 정돈되어 내려갑니다.

### Boolean 필드 이름
응답의 불리언 플래그는 커뮤니티와 동일하게 `is` 접두사로 나갑니다: **`isLiked`, `isMine`, `isFnb`**. (삭제 권한은 `canDelete`, 좋아요 토글 결과는 `liked`)

---

## 3. 엔드포인트 한눈에 보기

| 메서드 | 경로 | 인증 | 용도 |
| --- | --- | --- | --- |
| POST | `/api/v1/reviews` | 필수 | 리뷰 작성 |
| GET | `/api/v1/reviews?pinId=&page=&size=` | 선택 | 장소별 목록(최신순) |
| GET | `/api/v1/reviews/{reviewUuid}` | 선택 | 리뷰 상세 |
| DELETE | `/api/v1/reviews/{reviewUuid}` | 필수 | 리뷰 삭제(작성자/OPERATOR) |
| POST | `/api/v1/reviews/{reviewUuid}/like` | 필수 | 좋아요 토글 |
| GET | `/api/v1/reviews/media?pinId=&limit=` | 선택 | 사진·영상 스트립 |
| GET | `/api/v1/reviews/keywords?pinId=` | 선택 | 키워드 카탈로그 |
| POST | `/api/v1/s3/presign` | 필수 | 영상 프리사인 발급 |
| GET | `/api/v1/reviews/moderation/hidden` | OPERATOR | 숨김 리뷰 목록 |
| PATCH | `/api/v1/reviews/moderation/{reviewUuid}/restore` | OPERATOR | 숨김 해제 |

> 신고는 기존 [`POST /api/v1/reports`](report-block-api.md)(`targetType=REVIEW`), 차단은 [`POST /api/v1/blocks`](report-block-api.md)를 그대로 씁니다. 리뷰 전용 신고/차단 엔드포인트는 없습니다.

---

## 4. 리뷰 작성

```
POST /api/v1/reviews        (로그인 필요)
```

요청 본문:
```json
{
  "pinId": 123,
  "keywords": ["TASTY", "VALUE", "KIND"],
  "content": "가성비 최고. 학식보다 낫다.",
  "media": [
    { "url": "https://thingo.kr/static/images/reviews/ab12.png", "mediaType": "IMAGE" },
    { "url": "https://thingo.kr/static/images/reviews/cd34.mp4", "mediaType": "VIDEO" }
  ]
}
```

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `pinId` | O | 장소(Pin) id. **PLACE 타입 + 리뷰 가능 카테고리**여야 함 |
| `keywords` | O | **1~5개**. 값은 [키워드 코드](#11-키워드-전체-표). '적절없음'은 단독만, 비F&B는 fbOnly 키워드 불가 |
| `content` | O | **1~400자**. 비속어는 서버가 자동 마스킹(`*`) |
| `media` | X | **0~10개**. 각 `url`은 미리 업로드된 CloudFront URL, `mediaType`은 `IMAGE`/`VIDEO` |

성공: **201**, `data`는 [리뷰 상세](#5-리뷰-목록--상세)와 동일한 형태(작성 직후이므로 `isMine=true`, `canDelete=true`, `likeCount=0`, `isLiked=false`).

거부 케이스:

| 상황 | 상태 | error |
| --- | --- | --- |
| pin 없음 | 404 | `MAP_PIN_NOT_FOUND` |
| 건물이거나 동아리방(`club-room`)/흡연부스(`smoking`) | 400 | `REVIEW_NOT_ALLOWED_FOR_CATEGORY` |
| 키워드 0개 또는 6개 이상 | 400 | `REVIEW_KEYWORD_COUNT_INVALID` |
| '적절없음'을 다른 키워드와 함께 선택 | 400 | `REVIEW_KEYWORD_COMBINATION_INVALID` |
| 비F&B 장소인데 음식/가격·어른식사대접 키워드 선택 | 400 | `REVIEW_KEYWORD_NOT_ALLOWED_FOR_CATEGORY` |
| 미디어 11개 이상 | 400 | `REVIEW_MEDIA_LIMIT_EXCEEDED` |
| content 공백/400자 초과 | 400 | `VALIDATION_FAILED` |
| 잘못된 JSON/enum | 400 | `MALFORMED_REQUEST_BODY` |
| 비로그인 | 403 | (Access Denied) |

> 미디어는 **먼저 업로드**해서 URL을 확보한 뒤 이 요청에 담습니다. 자세한 절차는 [10. 미디어 업로드](#10-미디어-업로드-절차).

---

## 5. 리뷰 목록 / 상세

### 목록
```
GET /api/v1/reviews?pinId=123&sort=latest&page=0&size=10
```
| 파라미터 | 기본값 | 설명 |
| --- | --- | --- |
| `pinId` | (필수) | 장소 id |
| `sort` | `latest` | 현재 최신순만(확장 대비) |
| `page` / `size` | 0 / 10 | 페이지네이션 |

성공(200), `data`는 `Page<Summary>`:
```json
{
  "content": [
    {
      "reviewUuid": "aaaabbbb-cccc-dddd-eeee-ffff00001111",
      "author": { "nickname": "길동", "profileImageUrl": null },
      "keywords": [
        { "code": "TASTY", "emoji": "🤤", "label": "맛있음" },
        { "code": "VALUE", "emoji": "🐷", "label": "가성비" }
      ],
      "content": "가성비 최고. 학식보다 낫다.",
      "media": [
        { "url": "https://thingo.kr/static/images/reviews/ab12.png", "mediaType": "IMAGE", "sortOrder": 0 }
      ],
      "likeCount": 3,
      "isLiked": false,
      "isMine": false,
      "createdAt": "2026-07-14T12:30:00"
    }
  ],
  "totalElements": 42,
  "totalPages": 5,
  "number": 0,
  "size": 10
}
```

동작 규칙:
- **최신순**(`createdAt` 내림차순).
- **자동 숨김된 리뷰는 제외**됩니다.
- 로그인 시 **차단한/차단당한 사용자**의 리뷰는 목록에서 빠집니다(양방향).
- `keywords`는 표시용입니다. **'적절없음'만 선택한 리뷰는 빈 배열** `[]`로 내려갑니다(태그 미노출).
- `isLiked`: 로그인 사용자가 좋아요한 리뷰면 true. `isMine`: 내가 쓴 리뷰면 true(메뉴에서 신고/차단 vs 삭제 분기용).

> `createdAt`은 ISO 형식입니다. 화면의 `YY.MM.DD.요일` 표기는 프론트에서 포맷하세요.

### 상세
```
GET /api/v1/reviews/{reviewUuid}
```
성공(200), `data`는 목록 항목 + `pinId` + `canDelete`:
```json
{
  "reviewUuid": "aaaabbbb-cccc-dddd-eeee-ffff00001111",
  "pinId": 123,
  "author": { "nickname": "길동", "profileImageUrl": null },
  "keywords": [ /* ... */ ],
  "content": "가성비 최고. 학식보다 낫다.",
  "media": [ /* ... */ ],
  "likeCount": 3,
  "isLiked": false,
  "isMine": false,
  "canDelete": false,
  "createdAt": "2026-07-14T12:30:00"
}
```
- 존재하지 않거나, **자동 숨김**됐거나, **차단 관계**인 작성자의 리뷰는 **404 `REVIEW_NOT_FOUND`** 로 숨깁니다.
- `canDelete`: 작성자 본인이거나 OPERATOR면 true.

---

## 6. 리뷰 삭제

```
DELETE /api/v1/reviews/{reviewUuid}     (로그인 필요)
```
- **작성자 또는 OPERATOR**만 가능. 아니면 **403 `REVIEW_FORBIDDEN`**.
- 첨부 미디어는 S3에서 정리되고 리뷰는 하드 삭제됩니다.
- 성공: **204 No Content**(본문 없음).
- 없는 리뷰: 404 `REVIEW_NOT_FOUND`.

---

## 7. 좋아요 토글

```
POST /api/v1/reviews/{reviewUuid}/like     (로그인 필요)
```
- 좋아요가 없으면 추가(+1), 있으면 취소(-1). 게시판 좋아요와 동일.
- **알림(푸시) 없음** — 정책상 알림은 학교 공식 정보 전용입니다.

성공(200):
```json
{ "liked": true, "likeCount": 4 }
```
- `liked`: 토글 후 상태(true=좋아요됨, false=취소됨)
- `likeCount`: 반영된 최신 좋아요 수

> 화면에서 좋아요 수가 0이면 숫자를 숨기는 건 프론트 처리입니다(서버는 항상 실제 수를 반환).

---

## 8. 사진·영상 스트립

장소 상세 상단(사진·영상 영역)에서 리뷰 미디어만 모아 보여줄 때 씁니다.
```
GET /api/v1/reviews/media?pinId=123&limit=10
```
- 최신 리뷰부터 미디어를 순서대로 모아 `limit`개까지 반환.
- 자동 숨김/차단 사용자 미디어는 제외.

성공(200), `data`는 배열:
```json
[
  { "reviewUuid": "aaaabbbb-...", "url": "https://thingo.kr/static/images/reviews/cd34.mp4", "mediaType": "VIDEO", "sortOrder": 0 }
]
```
> 항목 클릭 시 해당 `reviewUuid` 상세(05-2-6)로 이동하면 됩니다.

---

## 9. 키워드 카탈로그

리뷰 작성 화면(05-2-5)이 보여줄 키워드 목록을 그룹별로 내려줍니다.
```
GET /api/v1/reviews/keywords?pinId=123
```
- `pinId`가 **F&B 장소**면 F&B 전용 키워드까지 포함, **비F&B**면 제외합니다.
- `pinId`를 **생략하면 전체**(F&B 포함) 카탈로그를 줍니다.

성공(200):
```json
{
  "isFnb": true,
  "groups": [
    { "group": "FOOD_PRICE", "label": "음식/가격",
      "keywords": [ { "code": "TASTY", "emoji": "🤤", "label": "맛있음" } ] },
    { "group": "MOOD", "label": "분위기", "keywords": [ /* ... */ ] },
    { "group": "ETC", "label": "기타", "keywords": [ /* ... */ ] }
  ]
}
```
- `isFnb`: 이 장소가 음식점(F&B)인지 여부.
- 비F&B면 `FOOD_PRICE` 그룹의 `keywords`는 빈 배열, `ETC`에서도 '어른 식사 대접'이 빠집니다.

---

## 10. 미디어 업로드 절차

리뷰 작성 요청에는 **이미 업로드된 URL**만 담습니다. 업로드는 별도로 먼저 진행합니다.

### 이미지 (소용량) — 서버 업로드
```
POST /api/v1/s3/upload?domain=REVIEW_MEDIA
Content-Type: multipart/form-data
form field: file=<이미지파일>
```
응답 `data`는 CloudFront URL 문자열. 이 URL을 `media[].url`(`mediaType: "IMAGE"`)로 사용.

### 영상 (대용량) — 프리사인 직접 업로드
서버로 영상 바이트를 보내지 않고 **S3에 직접** 올립니다.

**1단계**: 프리사인 발급
```
POST /api/v1/s3/presign        (로그인 필요)
```
```json
{ "domain": "REVIEW_MEDIA", "contentType": "video/mp4", "fileSize": 12582912 }
```
응답:
```json
{
  "uploadUrl": "https://s3-thingo.s3.ap-northeast-2.amazonaws.com/static/images/reviews/uuid.mp4?X-Amz-...",
  "fileUrl": "https://thingo.kr/static/images/reviews/uuid.mp4",
  "expiresInSeconds": 300
}
```

**2단계**: 반환된 `uploadUrl`로 영상 바이트를 직접 PUT
```
PUT <uploadUrl>
Content-Type: video/mp4        (발급 시 보낸 contentType과 동일하게)
body: <영상 바이너리>
```

**3단계**: 리뷰 작성 요청 `media[]`에 `{ "url": fileUrl, "mediaType": "VIDEO" }` 포함.

허용 형식 / 제한:
| 항목 | 값 |
| --- | --- |
| Content-Type | `video/mp4`, `video/quicktime`(mov), `video/webm`, `image/jpeg`, `image/png`, `image/webp` |
| 최대 용량 | 50MB |
| 프리사인 유효시간 | 300초(5분) |
| 잘못된 형식 | 400 `S3_PRESIGN_UNSUPPORTED_TYPE` |
| 용량 초과/0 이하 | 400 `S3_PRESIGN_SIZE_EXCEEDED` |

권장(프론트): 영상은 30초 이내로 유도, 목록/스트립에는 **첫 프레임 포스터 이미지**를 함께 올려 표시하면 로딩이 가볍습니다(포스터는 이미지 업로드 경로 사용).

---

## 11. 키워드 전체 표

`code`가 API에 보내는 값입니다. `fbOnly=O`는 **음식점(F&B)에서만** 선택 가능합니다.

### 음식/가격 (FOOD_PRICE) — 전부 fbOnly
| code | emoji | label |
| --- | --- | --- |
| `TASTY` | 🤤 | 맛있음 |
| `REVISIT` | 🍚 | 또갈집 |
| `VALUE` | 🐷 | 가성비 |
| `GENEROUS` | 🥩 | 양 혜자 |
| `FRESH` | 🥬 | 재료 신선 |
| `NOT_BAD` | 🐣 | 낫배드 |

### 분위기 (MOOD) — 공통
| code | emoji | label |
| --- | --- | --- |
| `CLEAN_LOOK` | ✨ | 깔끔함 |
| `COZY` | 🚙 | 아늑함 |
| `GOOD_VIBE` | 🎧 | 느좋 |
| `LUXURIOUS` | 🔥 | 고급짐 |
| `FOCUS` | 📖 | 집중 굿 |
| `SOLO_DINING` | 👤 | 혼밥 굿 |

### 기타 (ETC)
| code | emoji | label | fbOnly |
| --- | --- | --- | --- |
| `KIND` | 🥰 | 친절함 | |
| `HYGIENIC` | 🧼 | 청결함 | |
| `CLEAN_RESTROOM` | 🚻 | 화장실 깨끗 | |
| `GROUP_OK` | 👥 | 단체 가능 | |
| `ADULT_MEAL` | 🙇🏻 | 어른 식사 대접 | O |
| `NONE_APPROPRIATE` | | 적절한 키워드 없음 | (단독 선택, 표시 안 됨) |

규칙 정리:
- 리뷰당 **1~5개** 선택(필수).
- `NONE_APPROPRIATE`는 **단독**으로만 선택 가능(다른 키워드와 조합 불가). 저장은 되지만 화면 태그로는 렌더하지 않습니다.
- 비F&B 장소에서는 `fbOnly` 키워드(음식/가격 6개 + `ADULT_MEAL`)를 선택할 수 없습니다.
- `emoji`는 서버가 참고용으로 함께 내려줍니다. 프론트가 자체 아이콘을 써도 됩니다.

---

## 12. 신고 · 차단 · 자동 숨김

리뷰는 커뮤니티와 동일한 안전장치를 갖습니다.

### 신고 (기존 API 재사용)
```
POST /api/v1/reports
{ "targetType": "REVIEW", "targetUuid": "<리뷰 uuid>", "reason": "ABUSE" }
```
사유/규칙은 [신고/차단 API 문서](report-block-api.md) 참고.

### 차단 (기존 API 재사용)
```
POST /api/v1/blocks
{ "targetMemberUuid": "<차단할 회원 uuid>" }
```
차단하면 상대의 리뷰가 **내 목록/상세/미디어 스트립에서 즉시 사라집니다**(양방향). 상대 상세를 직접 열면 404.

### 자동 보호(모더레이션)
- **비속어 마스킹(L1)**: 리뷰 작성 시 본문의 사전 등록 비속어가 `*`로 자동 치환되어 저장됩니다("시발" → "**"). 공백·기호 우회("시.발")도 잡습니다.
- **신고 임계 자동 숨김(L2)**: 서로 다른 신고자가 **5명** 이상이면 해당 리뷰가 자동으로 숨겨집니다. 숨겨진 리뷰는 목록/상세/미디어 어디에도 노출되지 않고(상세는 404), 운영자만 검토/복원할 수 있습니다.

---

## 13. 운영자 검토 API

OPERATOR 권한 전용. 자동 숨김된 리뷰를 확인하고 정상 리뷰면 복원합니다.

### 숨김 리뷰 목록
```
GET /api/v1/reviews/moderation/hidden      (OPERATOR)
```
`data`는 숨김 리뷰 요약 배열(최신순).

### 숨김 해제
```
PATCH /api/v1/reviews/moderation/{reviewUuid}/restore    (OPERATOR)
```
복원하면 다시 정상 노출됩니다. 없는 리뷰면 404 `REVIEW_NOT_FOUND`.

권한 없는 계정이 호출하면 403.

---

## 14. 에러 코드 카탈로그

프론트는 `error`(문자열 코드)로 분기하세요. `message`는 그대로 노출 가능한 한국어입니다.

| error | status | 발생 지점 |
| --- | --- | --- |
| `REVIEW_NOT_FOUND` | 404 | 상세/삭제/좋아요/복원 대상 없음, 또는 숨김/차단으로 숨겨진 상세 |
| `REVIEW_FORBIDDEN` | 403 | 작성자·OPERATOR 아닌데 삭제 시도 |
| `REVIEW_NOT_ALLOWED_FOR_CATEGORY` | 400 | 건물/동아리방/흡연부스에 작성 시도 |
| `REVIEW_KEYWORD_COUNT_INVALID` | 400 | 키워드 0개 또는 6개 이상 |
| `REVIEW_KEYWORD_COMBINATION_INVALID` | 400 | '적절없음'을 다른 키워드와 함께 선택 |
| `REVIEW_KEYWORD_NOT_ALLOWED_FOR_CATEGORY` | 400 | 비F&B에서 fbOnly 키워드 선택 |
| `REVIEW_MEDIA_LIMIT_EXCEEDED` | 400 | 미디어 11개 이상 |
| `MAP_PIN_NOT_FOUND` | 404 | 없는 장소 id |
| `S3_PRESIGN_UNSUPPORTED_TYPE` | 400 | 허용 안 된 Content-Type |
| `S3_PRESIGN_SIZE_EXCEEDED` | 400 | 50MB 초과 또는 0 이하 |
| `VALIDATION_FAILED` | 400 | Bean Validation 실패(예: content 공백/초과, pinId 누락) |
| `MALFORMED_REQUEST_BODY` | 400 | 깨진 JSON / 잘못된 enum 값 |
| `MISSING_PARAMETER` | 400 | 필수 쿼리 파라미터 누락(예: `pinId`) |
| `INVALID_UUID` | 400 | 경로의 UUID 형식 오류 |

---

## 15. 자주 묻는 질문

**Q. 리뷰 수정은 없나요?**
네. 화면설계상 수정이 없어 **생성/삭제만** 지원합니다. 내용을 바꾸려면 삭제 후 다시 작성하세요.

**Q. 정렬은 최신순만 되나요?**
현재는 최신순(`createdAt DESC`)만입니다. `sort` 파라미터는 향후 확장을 위해 열어뒀습니다.

**Q. `isLiked`가 목록엔 있는데 로그인 안 하면요?**
비로그인 조회에서는 항상 `false`입니다(차단 필터도 적용 안 됨).

**Q. 좋아요 알림은 왜 없나요?**
알림은 학교 공식 정보 전용 정책이라 리뷰 좋아요는 푸시를 보내지 않습니다.

**Q. 영상 목록이 무거워요.**
목록/스트립에는 영상 대신 **첫 프레임 포스터 이미지**를 올려 표시하고, 상세에서만 영상을 재생하도록 권장합니다.

**Q. 건물에도 리뷰를 달 수 있나요?**
v1은 **장소(PLACE)** 만 지원합니다. 건물은 대상이 아닙니다.
