# 대동명지도 장소 DB 적재 (명월 네이버 리스트)

`daedong-sync.json` = 명월(@myongji_world)이 네이버지도에 공유한 대동명지도 8개 카테고리 리스트에서
이름·**정확 좌표**·도로명주소를 추출해 만든 **sync API 페이로드**.

- groups 1 (food) · categories 9 (daedong + 하위탭 8) · **places 304** (좌표 누락 0, 전부 캠퍼스 범위 내)
- 출처: linktr.ee/myongji_world → 카테고리별 naver.me 공유폴더 → Naver MyPlace 공유 API
- place code = `dd-{네이버 place id(sid)}` (재발행해도 안정적인 upsert 키)

## 좌표는 시트에 안 넣는다 (기획용으로 시트 깨끗하게 유지)

`서버-장소` 시트엔 위도/경도 칸이 없다. 대신 **sync API JSON이 lat/lng를 직접 받는다**
(`MapSyncDTO.PlaceRow`에 latitude/longitude 존재). 그래서 304곳은 이 JSON으로 한 번에 DB 적재한다.

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
| 간편식/분식 | daedong-snack | 37 |
| 고기 | daedong-meat | 30 |
| 주류 | daedong-bar | 34 |
| 카페/디저트 | daedong-cafe | 96 |
| 양식/아시안 | daedong-western | 12 |

이 하위탭 9개는 `② 카테고리 기본값 채우기` 버튼도 시트에 생성한다(중복 upsert 무해).

## 앞으로 시트로 추가하는 외부 장소의 좌표

이 JSON 적재분은 좌표가 이미 박혀 있어 문제없음. **이후 운영자가 `서버-장소` 시트에 주소만 적고
발행**하는 외부 장소는 좌표 변환이 필요하다. 현재는 Apps Script가 변환한다. 이를 **백엔드(서버)에서
처리**하려면 sync 시 주소→좌표 지오코딩을 서버에 추가해야 한다(네이버 클라우드 지오코딩 키 필요, 별도 작업).

## 공개 장소 상세정보·사진 참조 수집

`scripts/map-seed/collect-daedong-details.mjs`는 `dd-{네이버 place id}`를 이용해
304곳의 공개 장소 페이지에서 카테고리, 전화번호, 영업시간, 메뉴, 리뷰 요약과 사진 참조를 수집한다.

```bash
node scripts/map-seed/collect-daedong-details.mjs
```

결과는 `docs/map-seed/generated/`에 생성된다.

- `daedong-place-details.json`: 사진별 출처를 포함한 전체 구조화 데이터
- `daedong-place-details.csv`: 운영팀 검수·시트 이관용
- `daedong-sync-enriched.json`: 업체 등록 대표사진과 한줄정보가 추가된 sync payload
- `COLLECTION-REPORT.md`: 성공/실패와 필드별 확보 현황

대표 이미지는 `mediaSource=business`인 업체 등록 사진만 자동 선택한다. 방문자 사진은 저작권과
개인정보 보호를 위해 내려받지 않고 원본 페이지 참조만 기록한다. 외부 URL과 변동 정보는 운영 반영 전
권리자 허락 및 최신성 검수가 필요하다.

## 교내 건물 대표사진

`building-images-sync.json`은 교내 건물 대표사진 8장을 명지도 건물 데이터에 연결하는 sync API
페이로드다. 앱의 `cover` 표시 규격에 맞춰 1600×900(16:9)로 만들되 별도의 블러나 여백을 넣지
않았다. 사진이 화면보다 클 때는 입구가 먼저 보이도록 아래쪽 기준으로 자른다. 직접 촬영한 원본은
`C:\Users\USER\Desktop\명지도 건물 외부`에 보관한다.

| 건물 | 선택한 원본 |
|---|---|
| 종합관 | `종합관\종합관 외부1.jpg` |
| 학생회관 | `학생회관\학생회관 외부.jpg` |
| 미래관 | `미래융합대학\미래융합대학 외부.jpg` |
| 국제관 | `국제관\국제관 외부2.jpg` |
| 행정동 | `행정동\행정동 외부2.jpg` |
| 생활관 | `기숙사\기숙사 외부.jpg` |
| 방목학술정보관 | 명지대학교 대학혁신지원사업 공식 사이트 메인 외관 사진 |
| MCC관 | `MCC\MCC 외부4.jpg` |

도서관 사진 출처:
`https://innov.mju.ac.kr/sites/innovation/atchmnfl_mngr/imageSlide/26/temp_1580711791240100.jpg`

직접 촬영한 원본 17장은 프로젝트에 중복 저장하지 않는다. 대표사진을 다시 반영할 때는 위의
대동명지도 적재 방법과 같이 `building-images-sync.json`을 `/api/v1/sync/map`으로 전송하면 된다.

## 흡연부스·프린터 대표사진

`place-images-sync.json`은 흡연 4곳·프린터 3곳의 대표사진을 핀에 연결하는 sync API 페이로드다.
사진은 원본 4000x3000(4:3)을 기존 핀 사진 규격인 **1080x810**으로 축소해
`docs/map-seed/generated/place-images/{place code}.jpg`로 생성한다(생성 스크립트는 일회성).
S3 `static/images/map/`에 **같은 파일명**으로 올리면 CSV의 URL이 그대로 맞는다.

sync는 핀 행 전체를 덮어쓰므로(`Pin.update`), 페이로드에는 `imageUrl` 말고도
이름·추가정보·좌표·소속건물·층을 `facility-sync.json` 값 그대로 담아 둔다. 일부만 보내면 나머지가 null이 된다.

| place code | 위치 | 선택한 원본 | 판단 근거 |
|---|---|---|---|
| p-smoking-ext-01 | 흡연부스 (a.k.a. 종합관 뒷골목) | `종합관\종합관 흡연부스.jpg` | 밀폐형 부스 + 옹벽 낀 뒷골목, 좌표가 종합관 |
| p-smoking-ext-02 | 흡연부스 (a.k.a. 담배나무) | `국제관\흡연부스.jpg` | 밀폐형 CLEAN ZONE 부스, 소나무 아래 주차장. 좌표(학생회관)에서 가장 가까운 부스 |
| p-smoking-ext-03 | 흡연구역 | `MCC\MCC 흡연부스.jpg` | 실제로는 부스가 아닌 생울타리 개방형 흡연구역, 좌표가 MCC |
| p-smoking-ext-04 | 흡연구역 | `방목학술정보관\방목학술정보관 흡연장.jpg` | 목재 루버 개방형 흡연구역, 좌표(행정동)에 인접 |
| p-printer-b-jonghap-01 | 종합관 F1 | `종합관\종합관 프린터실.jpg` | 사진 속 안내문 "24시 프린트카페 **본관 1층** 앞" (종합관 = 구 본관) |
| p-printer-b-student-02 | 학생회관 F4 | `학생회관\학생회관 4층 프린트.jpg` | 옆 호실 표기 **2404** 남학생휴게실 → 학생회관 4층 |
| p-printer-b-library-04 | 방목학술정보관 F1 | `방목학술정보관\프린트실.jpg` | 복사·문구점 간판. 도서관 복사실은 1층(층별안내) |

흡연 핀 4개의 좌표는 실측값이 아니라 **가장 가까운 건물 좌표를 그대로 복사한 자리표시자**다
(각각 종합관·학생회관·MCC관·행정동 좌표와 완전히 일치). 지도에서 건물 한가운데에 찍히므로
ext-02/ext-04의 사진 배정도 이 좌표를 근거로 삼았다. 실측 좌표를 받으면 사진 배정도 함께 재확인할 것.

## 장소 갤러리 사진 · 실측 좌표 (place-gallery-sync.json)

`place-gallery-sync.json`은 장소 상세 갤러리(`imageUrls`)와 직접 찍은 실측 좌표를 반영하는 sync API 페이로드다.
원본은 `명지도 추가 작업` 폴더(2026-10 촬영) 30장이며, 긴 변 1280px JPEG로 줄여 **업로드 API**
(`POST /api/v1/s3/upload?domain=MAP`)로 올렸다. 업로드 API는 파일 내용 해시로 키를 정하므로 URL은 해시 파일명이다.

- `imageUrls`를 생략하면 기존 갤러리를 유지하고, 빈 배열이면 지운다. 시트 발행은 이 필드를 보내지 않으므로 갤러리가 지워지지 않는다.
- 행 전체를 덮어쓰므로 각 행은 `facility-sync.json` → `floor-rooms-sync.json` → `place-images-sync.json` 순으로 마지막에 적용된 값을 기준으로 만들었다
  (휴게실 `indoorCode` S2404/S2408 유지). 반영 전 운영 API 값과 비교해 의도한 좌표 변경 외 차이가 없음을 확인했다.
- 흡연부스 4곳은 기존 대표사진(`p-smoking-ext-0N.jpg`)을 첫 장으로 두고 새 사진을 뒤에 붙였다.
- 주차장 출입구 사진은 실측 좌표가 없어 별도 핀 대신 `도서관 지하주차장`(4장)·`MCC 지상 주차장`(1장) 갤러리에 넣었다.
- `학생회관 스터디룸/학관 스룸.jpg`는 문 표지판이 "3번 휴게공간"이라 스터디룸이 아니라 `p-break-room-b-student-05 휴게공간3`에 넣었다.

| 신규 핀 | 좌표 근거 |
|---|---|
| p-shortcut-ext-02 생활관 지름길 | 직접 찍은 좌표 (사진 EXIF GPS 37.58055, 126.922455 근처) |
| p-building-entrance-ext-03 종합관 출입구 | 위성사진 기준 종합관 남쪽 정면 중앙 **추정값**. 실측으로 교체할 것 |

좌표를 직접 찍은 핀(실측)과 아직 건물 좌표를 복사한 자리표시자인 핀은 페이로드의 좌표로 구분하지 않으므로,
실측 여부는 위 표와 이 페이로드를 만든 시점의 기록을 따른다.

## 테마지도 장소 (theme-places-sync.json)

테마지도(`src/main/resources/map/theme_maps.json`) 리뉴얼 때 운영 DB에 없던 37곳을 등록한 sync API 페이로드다.
네이버 공유 링크의 place id로 `dd-{place id}` 코드를 만들고, 이름·좌표·도로명주소는 네이버 장소 페이지 값을 썼다.

- 산책 코스 7곳은 맞는 카테고리가 없어 `guide` 그룹에 `walk`(산책) 칩을 새로 만들었다.
- 스터디카페 4곳은 `study-room`, 나머지 식당·카페는 대동명지도 하위탭(`daedong-*`)에 넣었다.
- 홍제천 폭포·안산벚꽃길·홍제천 음악분수는 네이버 장소명(홍제폭포광장·안산자락길·홍제천 음악분수) 대신 기획 시트 이름을 썼다.

## 산책 코스 사진 (walk-photos-sync.json)

산책 코스 6곳 갤러리는 Wikimedia Commons의 자유 라이선스 사진(CC0·CC BY-SA·공공누리 제1유형)만 썼다.
네이버 사진은 이용약관(자동 수집 금지)과 저작권 문제로 쓰지 않는다. 긴 변 1280px JPEG로 줄여 업로드 API로 올렸다.
CC BY-SA·공공누리는 출처 표시가 조건이라 각 핀 `infoText`에 작가와 라이선스를 넣었다. 사진을 바꾸면 `infoText`도 같이 고칠 것.
홍제천 음악분수는 자유 라이선스 사진이 없어 비워 두었다.

| 장소 | 원본 | 작가 | 라이선스 |
|---|---|---|---|
| 홍제천 폭포 | [Hongjecheon Artificial Waterfall 2023-10-31.jpg](https://commons.wikimedia.org/wiki/File:Hongjecheon_Artificial_Waterfall_2023-10-31.jpg) | Huntsmanleader | CC0 |
| 홍제천 폭포 | [Hongjecheon Artificial Falls Frozen.jpg](https://commons.wikimedia.org/wiki/File:Hongjecheon_Artificial_Falls_Frozen.jpg) | Huntsmanleader | CC0 |
| 홍제천 폭포 | [Biking in Seoul (14012446781).jpg](https://commons.wikimedia.org/wiki/File:Biking_in_Seoul_(14012446781).jpg) | Michael Sean Gallagher from London, United Kingdom | CC BY-SA 2.0 |
| 안산벚꽃길 | [KOCIS Korea First Snowfall in Seoul 05 (10922179396).jpg](https://commons.wikimedia.org/wiki/File:KOCIS_Korea_First_Snowfall_in_Seoul_05_(10922179396).jpg) | Korea.net / Korean Culture and Information Service (Jeon Han) | CC BY-SA 2.0 |
| 안산벚꽃길 | [KOCIS Korea First Snowfall in Seoul 23 (10922164646).jpg](https://commons.wikimedia.org/wiki/File:KOCIS_Korea_First_Snowfall_in_Seoul_23_(10922164646).jpg) | Korea.net / Korean Culture and Information Service (Jeon Han) | CC BY-SA 2.0 |
| 안산벚꽃길 | [KOCIS Korea First Snowfall in Seoul 20 (10922235624).jpg](https://commons.wikimedia.org/wiki/File:KOCIS_Korea_First_Snowfall_in_Seoul_20_(10922235624).jpg) | Korea.net / Korean Culture and Information Service (Jeon Han) | CC BY-SA 2.0 |
| 안산벚꽃길 | [KOCIS Korea First Snowfall in Seoul 04 (10922180466).jpg](https://commons.wikimedia.org/wiki/File:KOCIS_Korea_First_Snowfall_in_Seoul_04_(10922180466).jpg) | Korea.net / Korean Culture and Information Service (Jeon Han) | CC BY-SA 2.0 |
| 안산벚꽃길 | [KOCIS Korea First Snowfall in Seoul 22 (10922103795).jpg](https://commons.wikimedia.org/wiki/File:KOCIS_Korea_First_Snowfall_in_Seoul_22_(10922103795).jpg) | Korea.net / Korean Culture and Information Service (Jeon Han) | CC BY-SA 2.0 |
| 불광천 | [Bulgwangcheon 20230403 009.jpg](https://commons.wikimedia.org/wiki/File:Bulgwangcheon_20230403_009.jpg) | Mobius6 | CC BY-SA 4.0 |
| 불광천 | [Bulgwangcheon 20230403 006.jpg](https://commons.wikimedia.org/wiki/File:Bulgwangcheon_20230403_006.jpg) | Mobius6 | CC BY-SA 4.0 |
| 불광천 | [Bulgwangcheon 20230403 018.jpg](https://commons.wikimedia.org/wiki/File:Bulgwangcheon_20230403_018.jpg) | Mobius6 | CC BY-SA 4.0 |
| 불광천 | [Bulgwangcheon 20230403 005.jpg](https://commons.wikimedia.org/wiki/File:Bulgwangcheon_20230403_005.jpg) | Mobius6 | CC BY-SA 4.0 |
| 불광천 | [Bulgwangcheon 20230403 015.jpg](https://commons.wikimedia.org/wiki/File:Bulgwangcheon_20230403_015.jpg) | Mobius6 | CC BY-SA 4.0 |
| 망원정터 | [서울 망원정 터.jpg](https://commons.wikimedia.org/wiki/File:%EC%84%9C%EC%9A%B8_%EB%A7%9D%EC%9B%90%EC%A0%95_%ED%84%B0.jpg) | 문화재청 | KOGL Type 1 |
| 망원 한강공원 | [Mangwon Hangang Park.png](https://commons.wikimedia.org/wiki/File:Mangwon_Hangang_Park.png) | Brit in Seoul | CC BY-SA 4.0 |
| 하늘공원 | [Haneul-Park.jpg](https://commons.wikimedia.org/wiki/File:Haneul-Park.jpg) | Vanessaliam | CC BY-SA 3.0 |
| 하늘공원 | [Cosmos in the Sky Park3.jpg](https://commons.wikimedia.org/wiki/File:Cosmos_in_the_Sky_Park3.jpg) | Ji-soo Seo | CC BY-SA 4.0 |
| 하늘공원 | [Haneul-Park--reeds.jpg](https://commons.wikimedia.org/wiki/File:Haneul-Park--reeds.jpg) | Vanessaliam | CC BY-SA 3.0 |
| 하늘공원 | [Haneul Park Seoul 13.jpg](https://commons.wikimedia.org/wiki/File:Haneul_Park_Seoul_13.jpg) | U2em | CC BY-SA 3.0 |
| 하늘공원 | [KOCIS Korea Autumn SkyPark 20 (10471401284).jpg](https://commons.wikimedia.org/wiki/File:KOCIS_Korea_Autumn_SkyPark_20_(10471401284).jpg) | Korea.net / Korean Culture and Information Service (Jeon Han) | CC BY-SA 2.0 |
