# 대동명지도 장소 DB 적재 (명월 네이버 리스트)

`daedong-sync.json` = 명월(@myongji_world)이 네이버지도에 공유한 대동명지도 8개 카테고리 리스트에서
이름·**정확 좌표**·도로명주소를 추출해 만든 **sync API 페이로드**.

- groups 1 (food) · categories 9 (daedong + 하위탭 8) · **places 305** (좌표 누락 0, 전부 캠퍼스 범위 내)
- 출처: linktr.ee/myongji_world → 카테고리별 naver.me 공유폴더 → Naver MyPlace 공유 API
- place code = `dd-{네이버 place id(sid)}` (재발행해도 안정적인 upsert 키)

## 좌표는 시트에 안 넣는다 (기획용으로 시트 깨끗하게 유지)

`서버-장소` 시트엔 위도/경도 칸이 없다. 대신 **sync API JSON이 lat/lng를 직접 받는다**
(`MapSyncDTO.PlaceRow`에 latitude/longitude 존재). 그래서 305곳은 이 JSON으로 한 번에 DB 적재한다.

## 적재 방법 (한 번)

```bash
curl -X POST https://api.thingo.kr/api/v1/sync/map \
  -H "X-Sync-Token: <MAP_SYNC_TOKEN>" \
  -H "Content-Type: application/json" \
  --data @daedong-sync.json
```

`<MAP_SYNC_TOKEN>` = 보안 서브모듈 `app.sync.map-token`. Postman으로 보내도 됨.

- sync는 **upsert**(code 키)라 여러 번 보내도 중복 없음. 다른 그룹/카테고리/장소는 건드리지 않음.
- 처리 순서: groups → categories(daedong 먼저, 하위탭 나중) → places. 참조 안 깨짐.
- places는 외부 장소(소속건물 없음)로 들어가고 **lat/lng가 이미 있으므로 지오코딩 안 함**.

## 카테고리 매핑 (대동명지도 하위탭)

| 네이버 리스트 | categoryCode | 곳수 |
|---|---|---|
| 한식 | daedong-kr | 55 |
| 일식 | daedong-jp | 19 |
| 중식 | daedong-cn | 21 |
| 간편식/분식 | daedong-snack | 38 |
| 고기 | daedong-meat | 30 |
| 주류 | daedong-bar | 34 |
| 카페/디저트 | daedong-cafe | 96 |
| 양식/아시안 | daedong-western | 12 |

이 하위탭 9개는 `② 카테고리 기본값 채우기` 버튼도 시트에 생성한다(중복 upsert 무해).

## 앞으로 시트로 추가하는 외부 장소의 좌표

이 JSON 적재분은 좌표가 이미 박혀 있어 문제없음. **이후 운영자가 `서버-장소` 시트에 주소만 적고
발행**하는 외부 장소는 좌표 변환이 필요하다. 현재는 Apps Script가 변환한다. 이를 **백엔드(서버)에서
처리**하려면 sync 시 주소→좌표 지오코딩을 서버에 추가해야 한다(네이버 클라우드 지오코딩 키 필요, 별도 작업).
