# 키워드 알림 API (06-2-3 키워드 알림 설정)

내가 등록한 키워드 또는 자동완성에서 선택한 표준 Topic에 맞는 **새 글이 올라오면 푸시(FCM)로 알려주는** 기능이다.
Topic을 선택하면 공지 분류 계층으로, 선택하지 않으면 기존 제목 키워드 방식으로 매칭한다.

> 모든 응답은 공통 래퍼로 감싼다: `{ "status": "...", "data": <실제값>, "timestamp": "..." }`
> 키워드 알림/알림함/기기 토큰 등록은 **로그인 없이도** 쓸 수 있다.
> 소유자 식별: 로그인 상태면 `Authorization: Bearer <accessToken>`(회원), 아니면 `X-Device-Id: <deviceId>`(기기).
> 로그인 상태에서는 `X-Device-Id` 가 있어도 회원 기준으로 동작한다.
> 둘 다 없거나 서버가 발급하지 않은 `X-Device-Id` 면 `401` + `DEVICE_ID_INVALID` → 앱은 기기 id 를 새로 발급받는다.

---

## 쓰는 순서 (앱 기준)

0. 앱 최초 실행 시 `POST /api/v1/devices` 로 `deviceId` 발급 → 저장 후 아래 요청마다 `X-Device-Id` 헤더로 전송
1. (선택) 로그인 → accessToken 확보. 로그인 직후 `POST /api/v1/keyword-alarms/merge-device` 로 비로그인 때 등록한 구독/알림함을 회원으로 병합
2. 앱이 FCM 토큰 발급 → `POST /api/v1/device-tokens` 로 등록 (이게 있어야 푸시가 감). 로그인/로그아웃 직후에도 다시 등록
3. 입력 중 `GET /api/v1/alarm-topics/autocomplete`로 표준 Topic 후보 표시
4. 사용자가 키워드와 선택한 `topicId` 등록 → `POST /api/v1/keyword-alarms`
5. 새 글/학식이 올라오면 서버가 알아서 매칭 → 푸시 발송
6. 앱에서 `GET /api/v1/notifications` 로 알림함 표시, 누르면 읽음 처리

---

## 카테고리 / 플랫폼 값

| 카테고리(`categories`) | 뜻 | 매칭 방식 |
| --- | --- | --- |
| `NOTICE` | 공지사항 | 키워드가 **글 제목**에 있으면 알림 |
| `MJU_CALENDAR` | 학사일정 | 키워드가 **제목**에 있으면 알림 |
| `COMMUNITY` | 게시판 | 키워드가 **제목**에 있으면 알림 |
| `CAFETERIA` | 학식 | 키워드 무관, **새 학식 올라오면** 구독자 전원 알림 |

| 플랫폼(`platform`) | 뜻 |
| --- | --- |
| `ANDROID` / `IOS` / `WEB` | 토큰이 발급된 기기 종류 |

> `topicId`가 없으면 **제목 기준 + 접두 일치**다. `topicId`가 있으면 분류된 Topic 계층으로 매칭한다.

## 기기 id 발급 (비로그인 사용)

```
POST /api/v1/devices
```

로그인 불필요, 본문 없음. 응답 `data`: `{ "deviceId": "3f1c...-uuid" }`

- id 는 서버가 만든 UUID 다. 앱이 임의로 만든 값은 `X-Device-Id` 로 인정되지 않는다(`401 DEVICE_ID_INVALID`).
- 앱 저장소에 보관하고 재설치 전까지 계속 쓴다.

## 로그인 시 기기 구독 병합

```
POST /api/v1/keyword-alarms/merge-device
Authorization: Bearer <accessToken>
X-Device-Id: <deviceId>
```

응답 `data`: `{ "mergedCount": 1, "duplicateCount": 1, "skippedCount": 0 }`

- 회원에게 같은 키워드(앞뒤 공백 무시, 대소문자 무시)가 있으면 **카테고리를 합치고**(둘 중 하나라도 켜져 있으면 켬) 기기 구독은 지운다 → `duplicateCount`
- 없으면 기기 구독을 회원 소유로 옮긴다 → `mergedCount`
- 회원 구독이 10개에 차면 더 옮기지 않는다(기기 쪽에 남음) → `skippedCount`
- 기기 알림함도 회원 알림함으로 옮긴다(회원이 이미 받은 같은 콘텐츠는 기기 쪽 기록을 지움)
- 옮길 것이 없거나 서버가 모르는 `deviceId` 면 모두 0 (여러 번 호출해도 안전). `X-Device-Id` 헤더 자체가 없으면 `400`.

---

## 0. 알림 Topic 자동완성

```http
GET /api/v1/alarm-topics/autocomplete?query=졸업&limit=8
```

응답의 `topicId`를 저장 요청에 사용한다. `type=GROUP`인 항목은 활성 하위 Topic 전체를 포함한다.
사용자가 후보를 선택하지 않으면 `topicId`를 보내지 않고 자유 키워드로 등록할 수 있다.

---

## 1. 키워드 등록

```
POST /api/v1/keyword-alarms
```

요청 본문:
```json
{
  "keyword": "졸업",
  "topicId": "GRADUATION",
  "categories": ["NOTICE"]
}
```

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `keyword` | string | **공백 제외 1~5글자** (띄어쓰기 불가) |
| `topicId` | string/null | 자동완성 항목을 선택한 경우 응답의 ID, 미선택 시 생략 |
| `categories` | string[] | 위 카테고리 값, **1개 이상** |

검증 실패 시 `400` + 메시지 `"올바른 형식의 키워드를 입력해 주세요."`
이미 등록한 키워드면 `400` + `"이미 등록된 키워드입니다."`
이미 10개를 등록했으면 `400` + `KEYWORD_SUBSCRIPTION_LIMIT_EXCEEDED` (`"키워드 알림은 최대 10개까지 등록할 수 있습니다."`, 회원/기기 각각)

성공 응답(`data`):
```json
{
  "id": 12,
  "keyword": "졸업",
  "topicId": "GRADUATION",
  "categories": ["NOTICE"],
  "enabled": true,
  "createdAt": "2026-06-30T19:40:00"
}
```

---

## 2. 내 키워드 목록

```
GET /api/v1/keyword-alarms
```

`data` 는 위 1번 응답(객체)의 배열. 최신 등록순.

---

## 3. 카테고리 수정

```
PATCH /api/v1/keyword-alarms/{id}
```

```json
{
  "keyword": "졸업",
  "topicId": "GRADUATION",
  "categories": ["NOTICE"]
}
```

응답은 수정된 구독 객체. 남의 구독이면 `404` `"키워드 알림 구독을 찾을 수 없습니다."`

---

## 4. 키워드 삭제

```
DELETE /api/v1/keyword-alarms/{id}
```

성공 시 `data` 없음. 남의 구독이면 `404`.

---

## 5. Topic 추천 칩 (신규 UI 권장)

```http
GET /api/v1/alarm-topics/recommended
```

```json
{
  "items": [
    {
      "keyword": "해외",
      "topicId": "GLOBAL_PROGRAM",
      "displayName": "해외·국제 프로그램",
      "description": "해외 일경험, 취업, 파견학업, 단기연수 및 봉사 프로그램 전체",
      "type": "GROUP"
    }
  ]
}
```

화면에는 `keyword`를 표시하고, 사용자가 칩을 누르면 `keyword`와 `topicId`를 함께 등록 요청에 보낸다.
`해외`는 일반 문자열이 아니라 `GLOBAL_PROGRAM`으로 저장되므로 WELL, WEST, 교환학생, 해외파견,
해외탐방, 연수, 봉사 등이 모두 포함된다.

현재 추천 순서:

```text
수강신청, 휴·복학, 기숙사, 졸업, 국가근로, 해외
```

## 5-1. 기존 추천 키워드 (레거시 호환)

```
GET /api/v1/keyword-alarms/recommended
```

```json
["수강신청", "기숙사", "졸업", "국가근로", "해외탐방", "해외봉사"]
```

이 API는 문자열만 반환하므로 기존 앱 호환용이다. 새 UI는 Topic 추천 API를 사용한다.

---

## 6. 기기 토큰 등록 (푸시 받으려면 필수)

```
POST /api/v1/device-tokens
```

```json
{ "fcmToken": "e1l_IePh...(앱이 발급)", "platform": "ANDROID" }
```

- 같은 토큰을 다시 보내면 **갱신**(멱등). 다른 계정에서 같은 기기면 현재 계정으로 재배정.
- 로그인 상태: 회원에게 연결. 유효한 `X-Device-Id` 가 있으면 함께 기록(없거나 틀려도 등록은 됨).
- 비로그인 상태: `X-Device-Id` 필수. 회원 연결을 끊고 기기(게스트) 토큰으로 등록한다.
  **로그아웃 후 이 요청을 보내야** 그 기기로 회원 알림이 더 가지 않고 게스트 구독 알림을 받는다.
- 비로그인 기기 구독 알림은 그 기기의 게스트 토큰(회원 연결 없는 토큰)으로만 간다.
- 성공 시 `data` 없음.

## 7. 기기 토큰 삭제 (로그아웃 시, 로그인 필요)

```
DELETE /api/v1/device-tokens?fcmToken=<토큰>
```

---

## 8. 알림함 (받은 알림 목록)

메인 화면의 알림 아이콘 빨간 점만 판단할 때는 목록 대신 아래 경량 API를 호출한다.

```
GET /api/v1/notifications/unread-status
```

응답 `data`:

```json
{
  "unreadCount": 3,
  "hasUnread": true
}
```

`hasUnread=true`면 빨간 점을 표시하고, `false`면 숨긴다. 로그인 직후나 메인 화면
진입 시 호출하며 알림 상세 화면에서는 아래 목록 API의 동일 필드를 사용한다.

```
GET /api/v1/notifications?page=0&size=20
```

`data.content`는 **미읽음 최신순 → 읽음 최신순**으로 정렬된다.
`data.unreadCount`는 페이지와 무관한 전체 미읽음 수이고, `data.hasUnread`로
화면의 `모두 읽음` 버튼 활성화 여부를 바로 판단할 수 있다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | 알림 id |
| `matchedKeyword` | string | 기존 클라이언트 호환용 걸린 키워드 |
| `keyword` | string\|null | 키워드 알림에만 노출(학식 방송형은 `null`) |
| `type` | string | 키워드/학식: `NOTICE` / `MJU_CALENDAR` / `COMMUNITY` / `WEEKLY_MENU`, 활동: `COMMUNITY_LIKE` / `COMMUNITY_COMMENT` / `REVIEW_LIKE` |
| `categoryCode` | string | `NOTICE` / `MJU_CALENDAR` / `COMMUNITY` / `CAFETERIA` |
| `category` | string | 화면 표시용 `공지사항` / `학사일정` / `게시판` / `학식` |
| `title` | string | 글 제목(학식은 안내 문구) |
| `link` | string\|null | 원문 링크 |
| `read` | boolean | 읽음 여부 |
| `sentAt` | date-time | 기존 클라이언트 호환용 ISO-8601 발송 시각 |
| `timestamp` | number | Unix epoch milliseconds. 상대/절대 표기는 프론트에서 계산 |

## 9. 알림 읽음 처리

```
PATCH /api/v1/notifications/{id}/read     # 단건 (갱신된 read=true 알림 반환)
PATCH /api/v1/notifications/read-all      # 전체 (updatedCount, unreadCount=0, hasUnread=false)
```

---

## 10. 관리자 수동 발송 (운영/데모용)

자동 매칭 파이프라인과 별개로, **특정 회원 + 특정 키워드**로 과거에 색인된 콘텐츠 1건을
골라 즉시 FCM 푸시를 보낸다. (예: '멘토' 키워드의 과거 공지 1건을 한 사용자에게 발송)

```http
POST /api/v1/admin/keyword-alarms/manual-send
```

- **권한**: 로그인 + `ADMIN` 또는 `OPERATOR` 롤
- **요청 바디**

```json
{ "email": "kimgusqls1@gmail.com", "keyword": "멘토", "searchIndexId": "NOTICE:12345" }
```

| 필드 | 뜻 | 제약 |
| --- | --- | --- |
| `email` | 발송 대상 회원 이메일 | 필수, 이메일 형식 |
| `keyword` | 알림 표기·스냅샷 키워드(`'멘토' 키워드 새 소식`) | 필수, 최대 5자 |
| `searchIndexId` | (선택) 발송할 과거 콘텐츠를 정확히 지정. `{TYPE}:{원본ID}` | 선택, 최대 64자 |

- **동작**
  1. 이메일로 회원을 찾는다(없으면 `MEMBER_NOT_FOUND`).
  2. 발송 콘텐츠 1건 결정:
     - `searchIndexId` 지정 시 → **그 활성 콘텐츠를 그대로 사용**(마케팅: 특정 캠페인 공지 지정).
     - 미지정 시 → 알림 대상 카테고리(`NOTICE`/`MJU_CALENDAR`/`COMMUNITY`)의 활성 콘텐츠 중
       **제목에 키워드가 포함된 가장 최근 1건** 자동 선택.
     - 어느 쪽도 없으면 `ALARM_SOURCE_NOT_FOUND`.
  3. 대상 회원의 기기 토큰을 모은다(없으면 `DEVICE_TOKEN_NOT_FOUND`).
  4. 알림함(`notification_history`)에 기록한다. 같은 회원+콘텐츠 기록이 이미 있으면
     새로 만들지 않고 재사용해 **재발송을 허용**한다.
  5. 자동 키워드 알림과 동일한 표기(`'멘토' 키워드 새 소식` / 본문=콘텐츠 제목)로 푸시한다.

- **응답 예시**

```json
{
  "status": "success",
  "data": {
    "email": "kimgusqls1@gmail.com",
    "keyword": "멘토",
    "searchIndexId": "NOTICE:12345",
    "matchedTitle": "2024-2 멘토링 프로그램 멘토 모집 안내",
    "matchedType": "NOTICE",
    "link": "https://www.mju.ac.kr/.../view.do?...",
    "historyId": 987,
    "tokenCount": 2,
    "pushDispatched": true
  }
}
```

```bash
curl -X POST https://<host>/api/v1/admin/keyword-alarms/manual-send \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"email":"kimgusqls1@gmail.com","keyword":"멘토"}'
```

> 보낼 콘텐츠의 `searchIndexId` 는 통합검색으로 찾는다:
> `GET /api/v1/search/detail?keyword=멘토&type=NOTICE&order=latest` → 응답의 `id` 값.
>
> FCM 자격증명이 설정돼 있어야 실제 푸시가 나간다(`fcm.enabled=true` + 서비스 계정 JSON).
> 미설정 환경에서는 알림함 기록만 남고 실제 발송은 생략된다(FcmSender no-op).

---

## 동작 메모 (헷갈리기 쉬운 부분)

- **키워드 푸시 시점**: 콘텐츠가 **처음 올라올 때만** 발송한다(수정으로는 재알림 안 함).
- **활동 인앱 알림**: 내 게시글 좋아요/댓글, 내 명지도 리뷰 좋아요가 알림함에 쌓인다. 본인 활동은 제외한다.
- **좋아요 집계**: 같은 게시글/리뷰의 좋아요는 알림 1건을 최신 인원·개수로 갱신하고 다시 미읽음 처리한다.
- **중복 방지**: 한 글이 한 사람(회원 또는 기기)의 키워드 여러 개에 걸려도 **알림은 1건**.
- **비로그인 범위**: 키워드/Topic/학식 알림과 알림함만 기기 단위로 지원한다. 활동 알림(좋아요/댓글)과 관리자 수동 발송은 회원 전용.
- **학식**: 크롤링은 같은 주를 여러 번 돌리지만, **메뉴 내용이 실제 바뀐 경우에만** 1건 발송.
- **푸시 안 와도 알림함은 쌓임**: 기기 토큰이 없거나 FCM 미설정이어도 `GET /notifications` 에는 기록된다.
- **에러 형식**: 실패는 `{ status, error, message }` 형태로 내려온다(전역 핸들러).

---

## 예시 (curl)

```bash
# 키워드 등록
curl -X POST https://<host>/api/v1/keyword-alarms \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"keyword":"장학","categories":["NOTICE"]}'

# 기기 토큰 등록
curl -X POST https://<host>/api/v1/device-tokens \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"fcmToken":"<APP_FCM_TOKEN>","platform":"ANDROID"}'

# 알림함
curl https://<host>/api/v1/notifications?page=0&size=20 \
  -H "Authorization: Bearer $TOKEN"

# 비로그인: 기기 id 발급 -> 키워드 등록
curl -X POST https://<host>/api/v1/devices
curl -X POST https://<host>/api/v1/keyword-alarms \
  -H "X-Device-Id: $DEVICE_ID" -H "Content-Type: application/json" \
  -d '{"keyword":"장학","categories":["NOTICE"]}'
```
