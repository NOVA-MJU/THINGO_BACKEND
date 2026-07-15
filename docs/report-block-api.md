# 신고 / 차단 API

게시글·댓글·명지도 리뷰를 **신고**하고, 특정 사용자를 **차단**하는 기능이다.

- **신고**: 선택한 사유를 관리자 메일로 발송하고 신고 이력을 DB에 남긴다.
  - **자가 숨김(L1.5)**: 신고 접수 즉시, **신고한 본인 화면에서만** 해당 콘텐츠가 숨겨진다(다른 사용자에게는 그대로 보임).
  - **전체 숨김(L2)**: 서로 다른 신고자 수가 임계(기본 5명) 이상이면 **모든 사용자**에게 자동 숨김된다.
- **차단**: 상대가 작성한 게시글/댓글이 내 화면에서 **즉시 숨겨진다(양방향)**. 상대에게는 통지되지 않는다.

> 모든 응답은 공통 래퍼로 감싼다: `{ "status": "...", "data": <실제값>, "timestamp": "..." }`
> 전부 **로그인 필요**(헤더 `Authorization: Bearer <accessToken>`).
> OpenAPI 명세: `static/openapi/auth.json` (인증·회원 명세 최상단에 신고/차단 포함)
> 게시글 응답 스키마(`authorUuid` 포함)는 `static/openapi/community.json`의 `BoardSummary` 참고.

---

## 1. 신고 접수

```
POST /api/v1/reports
```

요청 본문:
```json
{
  "targetType": "BOARD",
  "targetUuid": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "reason": "ABUSE",
  "etcDetail": null
}
```

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `targetType` | enum | `BOARD`(게시판 글) / `COMMENT`(댓글) / `REVIEW`(명지도 리뷰, 예약값) |
| `targetUuid` | uuid | 신고 대상의 UUID |
| `reason` | enum | 아래 6개 사유 중 1개 |
| `etcDetail` | string | `reason=ETC`일 때 **필수**, 최대 400자. 그 외에는 무시(저장 안 함) |

### 신고 사유(`reason`)

| 값 | 항목 | 설명 |
| --- | --- | --- |
| `COMMERCIAL_AD` | 상업적 광고 및 홍보성 | 영리 목적의 홍보·판매, 타 서비스나 사이트 가입 유도 |
| `INAPPROPRIATE` | 주제 및 서비스 성격에 부적절함 | 게시판 주제나 장소와 무관한 내용, 무의미한 초성·도배·낚시 |
| `ABUSE` | 욕설/비하/인신공격 | 특정인이나 단체에 대한 비방, 명예훼손, 학우 간 분란 조장 |
| `OBSCENE` | 음란성/불건전한 내용 | 선정적인 내용, 불건전한 만남 유도, 불법촬영물 등 유통 |
| `PRIVACY_SCAM` | 개인정보 노출 및 사칭/사기 | 개인 실명·연락처·SNS ID 노출, 관리자 사칭, 사기 의심 |
| `ETC` | 기타 | 상세 사유 직접 입력(최대 400자) |

기타 사유인데 상세가 비어 있으면 `400` + `"[MJS] 구체적인 기타 사유를 입력해 주세요."`

성공 응답(`201`, `data`):
```json
{
  "reportUuid": "aaaabbbb-cccc-dddd-eeee-ffff00001111",
  "targetType": "BOARD",
  "targetUuid": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "reason": "ABUSE",
  "reasonLabel": "욕설/비하/인신공격",
  "etcDetail": null,
  "createdAt": "2026-07-10T14:00:00",
  "message": "신고가 정상적으로 접수되었습니다."
}
```

> 동작: DB에 신고 이력 저장 → **같은 트랜잭션에서 신고자 본인 자가 숨김(L1.5) 적용** → 임계 도달 시 전체 자동 숨김(L2) → **트랜잭션 커밋 후** 관리자 메일 발송(운영 계정으로 자기 발송).
> 메일 발송이 실패해도 신고 접수 자체는 성공 처리되며 이력은 남는다.
> `targetType=REVIEW`(명지도 리뷰)는 신고 이력만 저장되고 자가 숨김/자동 숨김은 아직 백엔드 미구현이다.

---

## 2. 사용자 차단

```
POST /api/v1/blocks
```

요청 본문:
```json
{ "targetMemberUuid": "2c3d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed" }
```

> `targetMemberUuid`는 차단하려는 게시글/댓글 작성자의 회원 UUID다.
> 게시글 목록·상세 응답(`SummaryDTO`/`DetailDTO`)의 `authorUuid` 필드 값을 그대로 넣으면 된다.

성공 응답(`201`, `data` 없음).

| 상황 | 결과 |
| --- | --- |
| 정상 차단 | 상대의 게시글/댓글이 내 목록·상세·댓글에서 즉시 사라짐(양방향) |
| 이미 차단한 상대 | 멱등 처리(중복 저장 안 함), 그대로 `201` |
| 자기 자신 | `400` + `"[MJS] 자기 자신은 차단할 수 없습니다."` |
| 없는 사용자 | `404` + `"[MJS] 회원 정보를 찾을 수 없습니다."` |

**양방향 의미**: A가 B를 차단하면 A는 B의 글/댓글을, B는 A의 글/댓글을 못 본다. B에게는 차단 사실을 알리지 않는다.

---

## 3. 차단 해제

```
DELETE /api/v1/blocks/{targetMemberUuid}
```

성공 응답(`200`, `data` 없음). 해제하면 상대 콘텐츠가 다시 보인다.
차단 내역이 없으면 `404` + `"[MJS] 차단 내역을 찾을 수 없습니다."`

---

## 4. 내가 차단한 목록

```
GET /api/v1/blocks
```

성공 응답(`200`, `data`, 최근 차단순):
```json
[
  {
    "memberUuid": "2c3d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
    "nickname": "길동",
    "name": "홍길동",
    "profileImageUrl": null,
    "blockedAt": "2026-07-10T14:00:00"
  }
]
```

---

## 콘텐츠 숨김 적용 범위

기존 게시글/댓글 조회 API(`GET /boards`, `GET /boards/{uuid}`, `GET /boards/{uuid}/comments`, `GET /boards/hot`)는 **요청/응답 스펙 변경 없이** 서버 내부에서 아래 숨김을 적용한다. 프론트는 별도 처리 없이 응답에 안 보이는 콘텐츠는 없는 것으로 취급하면 된다.

| 화면 | 차단(양방향) 숨김 | 신고 자가 숨김(L1.5, 신고자 본인만) |
| --- | --- | --- |
| 게시글 목록(`GET /boards`) | O (차단 사용자 글 제외) | O (내가 신고한 글 제외) |
| 게시글 상세(`GET /boards/{uuid}`) | O (차단 사용자 글은 `404` 처리) | O (내가 신고한 글은 `404` 처리) |
| 댓글 목록(`GET /boards/{uuid}/comments`) | O (차단 사용자의 댓글/대댓글 제외) | O (내가 신고한 댓글/대댓글 제외) |
| HOT 게시글(`GET /boards/hot`) | O (로그인 시 차단 사용자 글 제외, 비로그인은 전체 노출) | O (로그인 시 내가 신고한 글 제외) |
| 명지도 리뷰 목록(`GET /reviews`) | O (차단 사용자 리뷰 제외, 양방향) | X (리뷰 신고는 백엔드 미구현) |
| 명지도 리뷰 상세(`GET /reviews/{uuid}`) | O (차단 사용자 리뷰는 `404 REVIEW_NOT_FOUND`) | X |
| 명지도 리뷰 미디어 스트립(`GET /reviews/media`) | O (차단 사용자 미디어 제외) | X |

> 신고 임계(기본 5명, 서로 다른 신고자 기준) 도달 시에는 신고자가 아닌 **전체 사용자**에게도 동일하게 숨김 처리된다(L2, 위 1번 섹션 참고).
> 목록의 `totalElements`는 숨김 반영 전 기준이라 실제 노출 개수와 소폭 차이가 날 수 있다(페이지네이션 정책상 허용).
