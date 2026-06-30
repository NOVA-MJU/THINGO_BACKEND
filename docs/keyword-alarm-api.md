# 키워드 알림 API (06-2-3 키워드 알림 설정)

내가 등록한 키워드가 들어간 **새 글이 올라오면 푸시(FCM)로 알려주는** 기능이다.
공지사항/학사일정/게시판은 **키워드로 매칭**하고, 학식은 **새 메뉴가 뜨면 구독자 전원에게 방송**한다.

> 모든 응답은 공통 래퍼로 감싼다: `{ "status": "...", "data": <실제값>, "timestamp": "..." }`
> 추천 키워드를 빼면 전부 **로그인 필요**(헤더 `Authorization: Bearer <accessToken>`).

---

## 쓰는 순서 (앱 기준)

1. 로그인 → accessToken 확보
2. 앱이 FCM 토큰 발급 → `POST /api/v1/device-tokens` 로 등록 (이게 있어야 푸시가 감)
3. 사용자가 키워드 등록 → `POST /api/v1/keyword-alarms`
4. 새 글/학식이 올라오면 서버가 알아서 매칭 → 푸시 발송
5. 앱에서 `GET /api/v1/notifications` 로 알림함 표시, 누르면 읽음 처리

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

> 매칭은 **제목 기준 + 접두 일치**다. 예: `장학` 등록 → `장학금`, `장학생` 제목도 잡힌다.

---

## 1. 키워드 등록

```
POST /api/v1/keyword-alarms
```

요청 본문:
```json
{
  "keyword": "장학",
  "categories": ["NOTICE", "MJU_CALENDAR"]
}
```

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `keyword` | string | **공백 제외 1~5글자** (띄어쓰기 불가) |
| `categories` | string[] | 위 카테고리 값, **1개 이상** |

검증 실패 시 `400` + 메시지 `"올바른 형식의 키워드를 입력해 주세요."`
이미 등록한 키워드면 `400` + `"이미 등록된 키워드입니다."`

성공 응답(`data`):
```json
{
  "id": 12,
  "keyword": "장학",
  "categories": ["NOTICE", "MJU_CALENDAR"],
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
{ "categories": ["NOTICE"] }
```

응답은 수정된 구독 객체. 남의 구독이면 `404` `"키워드 알림 구독을 찾을 수 없습니다."`

---

## 4. 키워드 삭제

```
DELETE /api/v1/keyword-alarms/{id}
```

성공 시 `data` 없음. 남의 구독이면 `404`.

---

## 5. 추천 키워드 (로그인 불필요, 고정값)

```
GET /api/v1/keyword-alarms/recommended
```

```json
["중간고사", "기말고사", "해외탐방", "해외봉사", "수강신청"]
```

---

## 6. 기기 토큰 등록 (푸시 받으려면 필수)

```
POST /api/v1/device-tokens
```

```json
{ "fcmToken": "e1l_IePh...(앱이 발급)", "platform": "ANDROID" }
```

- 같은 토큰을 다시 보내면 **갱신**(멱등). 다른 계정에서 같은 기기면 현재 계정으로 재배정.
- 성공 시 `data` 없음.

## 7. 기기 토큰 삭제 (로그아웃 시)

```
DELETE /api/v1/device-tokens?fcmToken=<토큰>
```

---

## 8. 알림함 (받은 알림 목록)

```
GET /api/v1/notifications?page=0&size=20
```

`data` 는 Spring `Page`(`content`, `totalElements`, `totalPages` …). `content` 한 건:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | 알림 id |
| `matchedKeyword` | string | 걸린 키워드(학식은 `"학식"`) |
| `type` | string | `NOTICE` / `MJU_CALENDAR` / `COMMUNITY` / `WEEKLY_MENU` |
| `title` | string | 글 제목(학식은 안내 문구) |
| `link` | string\|null | 원문 링크 |
| `read` | boolean | 읽음 여부 |
| `sentAt` | date-time | 발송 시각 |

## 9. 알림 읽음 처리

```
PATCH /api/v1/notifications/{id}/read     # 단건
PATCH /api/v1/notifications/read-all       # 전체 (data = 읽음 처리된 개수)
```

---

## 동작 메모 (헷갈리기 쉬운 부분)

- **언제 푸시가 가나**: 콘텐츠가 **처음 올라올 때만**(수정/좋아요로는 재알림 안 함).
- **중복 방지**: 한 글이 한 사람의 키워드 여러 개에 걸려도 **알림은 1건**.
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
```
