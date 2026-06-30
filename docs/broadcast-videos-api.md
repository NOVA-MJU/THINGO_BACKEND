# 유튜브 영상 목록 API (방송국 + 공식)

명지대 관련 **유튜브 영상**을 최신순으로 제공한다. 채널 2개를 한 엔드포인트에서 `source` 칩으로 구분한다.

- **명대 방송국** (`BROADCAST`) — 기존 채널 `UCN1yxs3keLo78egYZSOHmcg`. UI 칩명은 "명대뉴스".
- **명지대 공식** (`OFFICIAL`) — `@mjuniv`. 신규.

> 용어 주의: 여기서 "명대뉴스"는 **명대 방송국 유튜브 채널**을 가리킨다. 뉴스 사이트 크롤러 `GET /api/v1/news`(news.mju.ac.kr 기사)와는 **전혀 별개**이며 이 작업으로 변경되지 않는다.

기존 `GET /api/v1/broadcast`를 그대로 확장했다. 새 엔드포인트(`/feed` 등)는 만들지 않았다.

---

## 1. 영상 목록 조회

```
GET /api/v1/broadcast?source=&page=&size=
```

### 칩 ↔ source

| 칩 (프론트)   | `source`        | 내용                              |
| ------------- | --------------- | --------------------------------- |
| 전체          | (미지정) / `ALL` | 방송국 + 공식, 최신순             |
| 명지대 공식   | `OFFICIAL`      | @mjuniv 영상만                    |
| 명대뉴스      | `BROADCAST`     | 명대 방송국 영상만 (별칭 `NEWS`)  |

### 쿼리 파라미터

| 이름     | 타입    | 기본값 | 설명                                                        |
| -------- | ------- | ------ | ----------------------------------------------------------- |
| `source` | string  | `ALL`  | `ALL` / `OFFICIAL` / `BROADCAST` (`NEWS`는 BROADCAST 별칭) |
| `page`   | integer | `0`    | 페이지 번호 (0부터)                                         |
| `size`   | integer | `9`    | 페이지당 항목 수                                            |

### 응답 아이템 (`BroadcastItem`)

| 필드           | 타입         | 설명                                            |
| -------------- | ------------ | ----------------------------------------------- |
| `source`       | enum         | `BROADCAST` / `OFFICIAL` — 어느 채널인지        |
| `title`        | string       | 제목                                            |
| `url`          | string(uri)  | 유튜브 watch URL                                |
| `thumbnailUrl` | string(uri)  | 썸네일                                          |
| `playlistTitle`| string\|null | 재생목록명 (공식/업로드영상은 null)             |
| `publishedAt`  | date-time    | 게시일 (정렬 기준)                              |

응답은 Spring `Page` 구조로 감싸진다(`content`, `totalElements`, `totalPages`, `number`, `size` …).

### 예시

```
GET /api/v1/broadcast?source=ALL&page=0&size=9
```

```json
{
  "status": "API 요청 성공",
  "data": {
    "content": [
      {
        "source": "OFFICIAL",
        "title": "명지대학교 2026학년도 신입생 모집 안내",
        "url": "https://www.youtube.com/watch?v=abcd1234",
        "thumbnailUrl": "https://i.ytimg.com/vi/abcd1234/hqdefault.jpg",
        "playlistTitle": null,
        "publishedAt": "2026-06-28T10:00:00"
      },
      {
        "source": "BROADCAST",
        "title": "[제36회 백마가요제] 참가자 팀 비하인드",
        "url": "https://www.youtube.com/watch?v=9WrW1sV28iA",
        "thumbnailUrl": "https://i.ytimg.com/vi/9WrW1sV28iA/hqdefault.jpg",
        "playlistTitle": null,
        "publishedAt": "2025-07-25T09:02:00"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 9,
    "first": true,
    "last": true,
    "numberOfElements": 2,
    "empty": false
  },
  "timestamp": "2026-06-30T20:48:58.1067"
}
```

---

## 2. 동기화

| 채널         | 엔드포인트                          | 비고                                  |
| ------------ | ----------------------------------- | ------------------------------------- |
| 명대 방송국  | `POST /api/v1/broadcast/sync`       | 기존. 전체 재생목록 + 3년치           |
| 명지대 공식  | `POST /api/v1/broadcast/official/sync` | 신규. 업로드 최신 50개만 유지       |

```json
{
  "status": "API 요청 성공",
  "data": "명지대학교 공식 유튜브 최신 영상 동기화 완료",
  "timestamp": "2026-06-30T20:48:58.1067"
}
```

- 스케줄러: 방송국은 기존 스케줄, 공식은 **매일 02:00**(`SchedulerService.scheduledSyncOfficialYoutube`).
- 공식 채널은 `youtube.official.handle`(기본 `@mjuniv`)을 YouTube Data API `forHandle`로 런타임 해석.

---

## 3. 동작/구현 메모

- 저장: `broadcast` 테이블 `source` 컬럼(`BROADCAST` / `OFFICIAL`)으로 두 채널 분리. 기존 행은 컬럼 DEFAULT `'BROADCAST'`로 자동 백필.
- 동기화 정리(delete) 는 `source`로 스코프 → 한 채널 동기화가 다른 채널 데이터를 지우지 않음.
- 조회: `source=ALL`은 `findAll`, 그 외는 `findBySource`. 단일 테이블 단일 쿼리 → DB 페이지네이션 정확.
- `GET /api/v1/news`(뉴스 사이트 기사)와 news 도메인은 **변경 없음**.
- 하위호환: `GET /api/v1/broadcast` 응답에 `source` 필드만 **추가**(기존 필드 유지). 기본 동작이 방송국만 → 방송국+공식으로 바뀌므로, 방송국만 원하면 `source=BROADCAST` 명시.
