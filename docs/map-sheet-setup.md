# 명지도 구글 시트 ↔ 백엔드 연동 가이드 (배너와 같은 프로젝트, 파일 분리)

운영팀이 구글 시트에서 명지도 데이터를 편집하고 **발행**하면 백엔드 DB에 upsert 된다.
배너와 **같은 스프레드시트 / 같은 Apps Script 프로젝트**에 두되, `.gs`는 `banner.gs` / `map.gs`로 분리한다.

> ⚠️ Apps Script는 파일이 달라도 **전역 스코프를 공유**한다. `map.gs`는 식별자에 `map` 접두사를 쓰고 `onOpen`을 두지 않는다. 메뉴는 `banner.gs`의 `onOpen`에 `addMapMenu();` 한 줄로 띄운다. 이미지 업로드도 map 전용(별도 html, `domain=MAP`).

## 1. 시트 탭 / 컬럼

탭은 `서버-` 접두사 + 한국어 이름. 헤더(1행)는 한국어이고, 각 헤더 셀에 마우스를 올리면 **설명 노트**가 뜬다.
`명지도 → ① 시트 초기 세팅`이 탭/헤더/설명/드롭다운/체크박스를 자동 생성한다. (Apps Script가 한국어 헤더를 서버 필드로 매핑)

**서버-카테고리그룹** (대분류)
| 코드 | 이름 | 노출순서 |
|---|---|---|
| food | 식사 (F&B) | 1 |

> 색상은 서버가 다루지 않는다. 프론트가 그룹 `코드`로 색을 매핑한다.
> 그룹/카테고리는 직접 입력하지 말고 `명지도 → ② 카테고리 기본값 채우기`로 한 번에 채운다(아래 6번).

**서버-카테고리** (칩)
| 코드 | 그룹코드 | 상위칩코드 | 이름 | 부제 | 툴팁 | 아이콘키 | 결과종류 | 퀵메뉴 | 노출순서 |
|---|---|---|---|---|---|---|---|---|---|
| daedong | food | (비움) | 대동명지도 | by. 명월 | 명월 Pick 맛집 | MyeongwolIcon | PLACE_LIST | ✓ | 1 |
| korean | food | daedong | 한식 | | | KoreanFoodIcon | PLACE_LIST | | 1 |

**서버-건물**
| 코드 | 카테고리코드 | 건물명 | 위도 | 경도 | 이미지URL | 추가정보 | 건물번호 | 강의실코드 |
|---|---|---|---|---|---|---|---|---|
| b-main | building | 종합관 | 37.5803 | 126.9223 | (업로드) | 구 본관 | 1 | S1XXX |

**서버-층**
| 건물코드 | 층 | 정렬순서 | 안내도URL |
|---|---|---|---|
| b-main | F1 | 1 | (업로드) |

**서버-장소** (비건물)
| 코드 | 카테고리코드 | 장소명 | 주소 | 이미지URL | 추가정보 | 소속건물코드 | 층 |
|---|---|---|---|---|---|---|---|
| p-happy | korean | 행복식당 | 서울 서대문구 거북골로 34 | (업로드) | 현금만 | | |
| p-printer | printer | 무한프린터 | | | 흑백 50원 | b-main | F1 |

> 장소 `주소`는 발행 시 자동으로 좌표 변환(Apps Script 내장 지오코딩). 건물 `위도/경도`는 핀이 겹치지 않게 개발팀이 직접 입력.

**서버-운영시간** (건물만)
| 건물코드 | 요일 | 여는시간 | 닫는시간 | 24시간 | 휴무 | 비고 |
|---|---|---|---|---|---|---|
| b-main | MONDAY | 09:00 | 18:00 | | | |
| b-main | SUNDAY | | | | ✓ | |

- 처리 순서: 카테고리그룹 → 카테고리 → 건물 → 층 → 장소 → 운영시간
- 하위 탭(한식)은 `상위칩코드`에 상위 칩 코드 · 내부 장소는 `소속건물코드`+`층`, 외부는 `주소`
- `여는/닫는시간`: "HH:mm" 텍스트. 자정 넘김은 닫는시간 `23:59`

## 2. `map.gs` (새 파일)

```javascript
// ===== 명지도 동기화 (map.gs). map 접두사 + onOpen 없음 (banner.gs와 공존) =====
const MAP_ENDPOINT_SYNC   = "https://api.thingo.kr/api/v1/sync/map";
const MAP_ENDPOINT_UPLOAD = "https://api.thingo.kr/api/v1/s3/upload";

// 탭: key=서버 섹션, sheet=시트 탭명(서버-한국어), cols=[{key:서버필드, header:한국어헤더, desc:설명}]
const MAP_TABS = [
  { key: "groups", sheet: "서버-카테고리그룹", cols: [
    { key: "code",         header: "코드",     desc: "그룹 고유 코드(영문). 예: food, study, convenience, guide" },
    { key: "name",         header: "이름",     desc: "그룹 표시명. 예: 식사 (F&B)" },
    { key: "displayOrder", header: "노출순서", desc: "작을수록 먼저(숫자). 예: 1" }
  ]},
  { key: "categories", sheet: "서버-카테고리", cols: [
    { key: "code",         header: "코드",       desc: "칩 고유 코드(영문). 예: daedong, korean, printer" },
    { key: "groupCode",    header: "그룹코드",   desc: "소속 그룹 코드(서버-카테고리그룹의 코드). 예: food" },
    { key: "parentCode",   header: "상위칩코드", desc: "하위 탭이면 상위 칩 코드, 최상위면 비움. 예: daedong" },
    { key: "label",        header: "이름",       desc: "칩 표시명. 예: 대동명지도, 한식" },
    { key: "subtitle",     header: "부제",       desc: "출처/설명(선택). 예: by. 명월" },
    { key: "tooltipText",  header: "툴팁",       desc: "메인 홈 안내 문구(선택)" },
    { key: "iconKey",      header: "아이콘키",   desc: "프론트 아이콘 식별자. 예: MyeongwolIcon" },
    { key: "resultType",   header: "결과종류",   desc: "PLACE_LIST(장소목록)/BUILDING_LIST(건물목록)/BUS(버스화면)" },
    { key: "quickMenu",    header: "퀵메뉴",     desc: "상단 퀵메뉴 노출 여부(체크)" },
    { key: "displayOrder", header: "노출순서",   desc: "작을수록 먼저(숫자)" }
  ]},
  { key: "buildings", sheet: "서버-건물", cols: [
    { key: "code",           header: "코드",       desc: "건물 고유 코드(영문). 예: b-main" },
    { key: "categoryCode",   header: "카테고리코드", desc: "건물 칩 코드. 보통 building" },
    { key: "name",           header: "건물명",     desc: "예: 종합관" },
    { key: "latitude",       header: "위도",       desc: "건물 핀 위치(개발팀이 지도에서 확인해 직접 입력 - 핀 겹침 방지). 예: 37.5803" },
    { key: "longitude",      header: "경도",       desc: "건물 핀 위치(개발팀 직접 입력). 예: 126.9223" },
    { key: "imageUrl",       header: "이미지URL",  desc: "건물 사진(메뉴 '이미지 업로드'로 자동 기입)" },
    { key: "infoText",       header: "추가정보",   desc: "기타 안내 텍스트(선택)" },
    { key: "buildingNumber", header: "건물번호",   desc: "건물 고유 번호(숫자). 예: 1" },
    { key: "classroomCode",  header: "강의실코드", desc: "예시 강의실 코드. 예: S1XXX" }
  ]},
  { key: "floors", sheet: "서버-층", cols: [
    { key: "buildingCode", header: "건물코드",   desc: "이 층이 속한 건물 코드(서버-건물의 코드)" },
    { key: "label",        header: "층",         desc: "층 라벨. 예: B1, F1" },
    { key: "floorOrder",   header: "정렬순서",   desc: "지하 음수/지상 양수(숫자). 예: B1=-1, 1F=1" },
    { key: "mapImageUrl",  header: "안내도URL",  desc: "층별 안내도 이미지(메뉴 '이미지 업로드')" }
  ]},
  { key: "places", sheet: "서버-장소", cols: [
    { key: "code",               header: "코드",         desc: "장소 고유 코드(영문). 예: p-happy" },
    { key: "categoryCode",       header: "카테고리코드", desc: "소속 카테고리 코드. 예: korean, printer" },
    { key: "name",               header: "장소명",       desc: "예: 행복식당" },
    { key: "address",            header: "주소",         desc: "외부 장소 도로명 주소. 발행 시 좌표 자동 변환(Apps Script 지오코딩). 내부 장소는 비움" },
    { key: "imageUrl",           header: "이미지URL",    desc: "장소 사진(메뉴 '이미지 업로드')" },
    { key: "infoText",           header: "추가정보",     desc: "기타 안내(선택)" },
    { key: "parentBuildingCode", header: "소속건물코드", desc: "내부 장소면 건물 코드, 외부면 비움(외부=주소 입력)" },
    { key: "floorLabel",         header: "층",           desc: "내부 장소면 층 라벨(서버-층의 층), 외부면 비움" }
  ]},
  { key: "operatingHours", sheet: "서버-운영시간", cols: [
    { key: "buildingCode", header: "건물코드", desc: "운영시간 대상 건물 코드(건물만)" },
    { key: "dayOfWeek",    header: "요일",     desc: "MONDAY ~ SUNDAY" },
    { key: "openTime",     header: "여는시간", desc: "HH:mm 텍스트. 예: 09:00" },
    { key: "closeTime",    header: "닫는시간", desc: "HH:mm 텍스트. 자정 넘김은 23:59" },
    { key: "always24h",    header: "24시간",   desc: "24시간 운영 여부(체크)" },
    { key: "closed",       header: "휴무",     desc: "해당 요일 휴무 여부(체크)" },
    { key: "note",         header: "비고",     desc: "예: 00:00~05:00 학생증 태그" }
  ]}
];

const MAP_TIME_KEYS = ["openTime", "closeTime"];

// 카테고리 기본값(프론트 칩 구성과 1:1). 코드/순서는 미리 채워둠.
// 색상은 서버가 안 다룸(프론트가 그룹 code로 매핑). iconKey/퀵메뉴/부제는 비워두고 운영자가 추후 채움.
const MAP_SEED_GROUPS = [
  { code: "food",        name: "식사 (F&B)",               displayOrder: 1 },
  { code: "study",       name: "학습 및 휴식 (Study/Rest)", displayOrder: 2 },
  { code: "convenience", name: "편의 (Convenience)",        displayOrder: 3 },
  { code: "guide",       name: "건물·이동 (Map Guide)",     displayOrder: 4 }
];

// resultType 기본값: 대부분 PLACE_LIST, 건물=BUILDING_LIST, 버스=BUS
const MAP_SEED_CATEGORIES = [
  // 식사 (F&B)
  { code: "daedong",      groupCode: "food",        label: "대동명지도",    resultType: "PLACE_LIST",    displayOrder: 1 },
  { code: "cafeteria",    groupCode: "food",        label: "학생식당",      resultType: "PLACE_LIST",    displayOrder: 2 },
  { code: "restaurant",   groupCode: "food",        label: "음식점",        resultType: "PLACE_LIST",    displayOrder: 3 },
  { code: "cafe",         groupCode: "food",        label: "카페",          resultType: "PLACE_LIST",    displayOrder: 4 },
  { code: "night-truck",  groupCode: "food",        label: "야식트럭",      resultType: "PLACE_LIST",    displayOrder: 5 },
  { code: "mart",         groupCode: "food",        label: "편의점·마트",   resultType: "PLACE_LIST",    displayOrder: 6 },
  // 대동명지도 하위탭 (parentCode=daedong). 명월 네이버 리스트 8개 분류와 1:1
  { code: "daedong-kr",      groupCode: "food", parentCode: "daedong", label: "한식",        resultType: "PLACE_LIST", displayOrder: 1 },
  { code: "daedong-jp",      groupCode: "food", parentCode: "daedong", label: "일식",        resultType: "PLACE_LIST", displayOrder: 2 },
  { code: "daedong-cn",      groupCode: "food", parentCode: "daedong", label: "중식",        resultType: "PLACE_LIST", displayOrder: 3 },
  { code: "daedong-snack",   groupCode: "food", parentCode: "daedong", label: "간편식·분식", resultType: "PLACE_LIST", displayOrder: 4 },
  { code: "daedong-meat",    groupCode: "food", parentCode: "daedong", label: "고기",        resultType: "PLACE_LIST", displayOrder: 5 },
  { code: "daedong-bar",     groupCode: "food", parentCode: "daedong", label: "주류",        resultType: "PLACE_LIST", displayOrder: 6 },
  { code: "daedong-cafe",    groupCode: "food", parentCode: "daedong", label: "카페·디저트", resultType: "PLACE_LIST", displayOrder: 7 },
  { code: "daedong-western", groupCode: "food", parentCode: "daedong", label: "양식·아시안", resultType: "PLACE_LIST", displayOrder: 8 },
  // 학습 및 휴식 (Study/Rest)
  { code: "lounge",       groupCode: "study",       label: "라운지",        resultType: "PLACE_LIST",    displayOrder: 1 },
  { code: "reading-room", groupCode: "study",       label: "열람실",        resultType: "PLACE_LIST",    displayOrder: 2 },
  { code: "study-room",   groupCode: "study",       label: "스터디룸",      resultType: "PLACE_LIST",    displayOrder: 3 },
  { code: "gym",          groupCode: "study",       label: "운동 시설",     resultType: "PLACE_LIST",    displayOrder: 4 },
  { code: "rest-area",    groupCode: "study",       label: "휴게실",        resultType: "PLACE_LIST",    displayOrder: 5 },
  { code: "terrace",      groupCode: "study",       label: "테라스",        resultType: "PLACE_LIST",    displayOrder: 6 },
  { code: "club-room",    groupCode: "study",       label: "동아리방",      resultType: "PLACE_LIST",    displayOrder: 7 },
  // 편의 (Convenience)
  { code: "printer",      groupCode: "convenience", label: "프린트",        resultType: "PLACE_LIST",    displayOrder: 1 },
  { code: "certificate",  groupCode: "convenience", label: "자동증명발급기", resultType: "PLACE_LIST",    displayOrder: 2 },
  { code: "bank-atm",     groupCode: "convenience", label: "은행·ATM",      resultType: "PLACE_LIST",    displayOrder: 3 },
  { code: "post",         groupCode: "convenience", label: "우편",          resultType: "PLACE_LIST",    displayOrder: 4 },
  { code: "smoking",      groupCode: "convenience", label: "흡연 부스",     resultType: "PLACE_LIST",    displayOrder: 5 },
  { code: "power-bank",   groupCode: "convenience", label: "보조배터리",    resultType: "PLACE_LIST",    displayOrder: 6 },
  { code: "restroom",     groupCode: "convenience", label: "화장실",        resultType: "PLACE_LIST",    displayOrder: 7 },
  // 건물·이동 (Map Guide)
  { code: "building",     groupCode: "guide",       label: "건물",          resultType: "BUILDING_LIST", displayOrder: 1 },
  { code: "parking",      groupCode: "guide",       label: "주차장",        resultType: "PLACE_LIST",    displayOrder: 2 },
  { code: "campus-gate",  groupCode: "guide",       label: "캠퍼스 출입구", resultType: "PLACE_LIST",    displayOrder: 3 },
  { code: "building-gate",groupCode: "guide",       label: "건물 출입구",   resultType: "PLACE_LIST",    displayOrder: 4 },
  { code: "passage",      groupCode: "guide",       label: "건물 통로",     resultType: "PLACE_LIST",    displayOrder: 5 },
  { code: "shortcut",     groupCode: "guide",       label: "지름길",        resultType: "PLACE_LIST",    displayOrder: 6 },
  { code: "bus",          groupCode: "guide",       label: "버스",          resultType: "BUS",           displayOrder: 7 }
];

// banner.gs의 onOpen() 안에서 호출:  addMapMenu();
function addMapMenu() {
  SpreadsheetApp.getUi().createMenu("명지도")
    .addItem("① 시트 초기 세팅", "mapSetupSheet")
    .addItem("② 카테고리 기본값 채우기", "mapSeedCategories")
    .addItem("이미지 업로드", "mapUpload")
    .addItem("발행 (DB 반영)", "mapSyncMap")
    .addToUi();
}

// ② 그룹 4개 + 카테고리 27개를 코드/순서까지 미리 채워 기입. 이후 운영자는 장소만 추가하면 됨.
function mapSeedCategories() {
  const ui = SpreadsheetApp.getUi();
  const ans = ui.alert("카테고리 기본값 채우기",
    "‘서버-카테고리그룹’ / ‘서버-카테고리’ 탭의 기존 행을 지우고 기본 4그룹·27칩으로 다시 채웁니다.\n계속할까요?",
    ui.ButtonSet.YES_NO);
  if (ans !== ui.Button.YES) return;
  try {
    mapWriteSeed_("groups", MAP_SEED_GROUPS);
    mapWriteSeed_("categories", MAP_SEED_CATEGORIES);
  } catch (e) { ui.alert("기본값 채우기 실패 - " + e.message); return; }
  ui.alert("기본값 채우기 완료\n그룹 " + MAP_SEED_GROUPS.length + "개, 카테고리 " + MAP_SEED_CATEGORIES.length + "개 기입.\n" +
    "아이콘키/퀵메뉴/부제는 비어 있으니 프론트와 합의해 채운 뒤 ‘발행’하세요.");
}

// 시드 객체 배열을 해당 탭 헤더 순서에 맞춰 2행부터 기입 (없는 키는 빈 칸, 체크박스 칸은 false)
function mapWriteSeed_(tabKey, seedRows) {
  const tab = MAP_TABS.filter(t => t.key === tabKey)[0];
  const sheet = SpreadsheetApp.getActive().getSheetByName(tab.sheet);
  if (!sheet) throw new Error("탭 없음: " + tab.sheet + " (먼저 ‘① 시트 초기 세팅’ 실행)");

  // 기존 데이터 행만 비움(헤더 1행 보존). 우측 ‘📌 입력 안내’ 영역은 건드리지 않음(탭 컬럼 수만큼만)
  const lastRow = sheet.getLastRow();
  if (lastRow > 1) sheet.getRange(2, 1, lastRow - 1, tab.cols.length).clearContent();

  const matrix = seedRows.map(row => tab.cols.map(c => {
    const v = row[c.key];
    if (v != null) return v;
    return (c.key === "quickMenu") ? false : ""; // 체크박스 칸은 false로 채워 유효성 유지
  }));
  if (matrix.length) sheet.getRange(2, 1, matrix.length, tab.cols.length).setValues(matrix);
}

function mapToken_() {
  return PropertiesService.getScriptProperties().getProperty("MAP_SYNC_TOKEN");
}

// 탭/헤더/설명노트/드롭다운/체크박스 자동 생성 + 보기 좋게 서식
function mapSetupSheet() {
  const ss = SpreadsheetApp.getActive();
  MAP_TABS.forEach(tab => {
    const sheet = ss.getSheetByName(tab.sheet) || ss.insertSheet(tab.sheet);
    const headers = tab.cols.map(c => c.header);
    const notes   = tab.cols.map(c => c.desc);

    const headerRange = sheet.getRange(1, 1, 1, headers.length);
    headerRange.setValues([headers]).setNotes([notes])
      .setFontWeight("bold").setFontColor("#ffffff").setBackground("#4a86e8")
      .setHorizontalAlignment("center").setVerticalAlignment("middle");
    sheet.setFrozenRows(1);
    sheet.setRowHeight(1, 32);

    // 컬럼별 입력 보조 (키로 위치 찾아 적용 → 순서 바뀌어도 안전)
    tab.cols.forEach((c, i) => {
      const colRange = sheet.getRange(2, i + 1, 1000, 1);
      if (c.key === "resultType") {
        colRange.setDataValidation(SpreadsheetApp.newDataValidation()
          .requireValueInList(["PLACE_LIST", "BUILDING_LIST", "BUS"], true).build());
      } else if (c.key === "dayOfWeek") {
        colRange.setDataValidation(SpreadsheetApp.newDataValidation()
          .requireValueInList(["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"], true).build());
      } else if (c.key === "quickMenu" || c.key === "always24h" || c.key === "closed") {
        colRange.insertCheckboxes();
      } else if (MAP_TIME_KEYS.indexOf(c.key) >= 0) {
        colRange.setNumberFormat("@"); // 텍스트(HH:mm 보존)
      }
    });
    // 가독성: 컬럼 너비 넉넉하게 (URL은 가장 넓게, 이름/주소/설명 계열은 넓게)
    const WIDE_KEYS = ["name", "label", "subtitle", "tooltipText", "infoText", "address", "classroomCode"];
    tab.cols.forEach((c, i) => {
      const width = (c.key === "imageUrl" || c.key === "mapImageUrl") ? 260
                  : WIDE_KEYS.indexOf(c.key) >= 0 ? 200 : 130;
      sheet.setColumnWidth(i + 1, width);
    });

    // 데이터 우측에 항상 보이는 입력 안내(범례) 블록 (동기화와 무관 - 우측 열은 안 읽음)
    const helpCol = headers.length + 2;            // 데이터와 한 칸 띄움
    sheet.getRange(1, helpCol).setValue("📌 입력 안내 (이 영역은 동기화와 무관)")
      .setFontWeight("bold").setBackground("#fff2cc").setVerticalAlignment("middle");
    const lines = tab.cols.map(c => "• " + c.header + " : " + c.desc);
    sheet.getRange(2, helpCol, lines.length, 1).setValues(lines.map(l => [l]))
      .setWrap(true).setVerticalAlignment("top").setBackground("#fffef5");
    sheet.setColumnWidth(helpCol, 460);
  });
  SpreadsheetApp.getUi().alert("명지도 시트 6개 탭 세팅 완료.\n헤더 셀 마우스오버 설명 + 우측 '📌 입력 안내' 범례를 참고하세요.");
}

// 한 탭을 객체 배열로 (한국어 헤더 → 서버 필드 매핑, 빈 셀 null, 빈 행 무시)
function mapReadTab_(tab) {
  const sheet = SpreadsheetApp.getActive().getSheetByName(tab.sheet);
  if (!sheet) throw new Error("탭 없음: " + tab.sheet + " (먼저 '① 시트 초기 세팅' 실행)");
  const values = sheet.getDataRange().getValues();
  if (values.length < 2) return [];
  const header = values[0];
  const colIndex = {};
  tab.cols.forEach(c => colIndex[c.key] = header.indexOf(c.header));

  const rows = [];
  for (let i = 1; i < values.length; i++) {
    const r = values[i];
    const obj = {};
    let hasValue = false;
    tab.cols.forEach(c => {
      let v = colIndex[c.key] >= 0 ? r[colIndex[c.key]] : "";
      if (v === "" || v === null) { obj[c.key] = null; return; }
      if (MAP_TIME_KEYS.indexOf(c.key) >= 0 && Object.prototype.toString.call(v) === "[object Date]") {
        v = Utilities.formatDate(v, Session.getScriptTimeZone(), "HH:mm");
      }
      obj[c.key] = v;
      hasValue = true;
    });
    if (hasValue) rows.push(obj);
  }
  return rows;
}

function mapSyncMap() {
  const ui = SpreadsheetApp.getUi();
  const token = mapToken_();
  if (!token) { ui.alert("스크립트 속성 MAP_SYNC_TOKEN 이 설정되지 않았습니다."); return; }

  const payload = {};
  try { MAP_TABS.forEach(tab => { payload[tab.key] = mapReadTab_(tab); }); }
  catch (e) { ui.alert("발행 취소 - " + e.message); return; }

  // 장소(외부) 주소 → 좌표 자동 변환. 건물은 시트의 위도/경도를 그대로 사용(직접 입력)
  const geoFails = [];
  (payload.places || []).forEach(row => {
    if (row.address && (row.latitude == null || row.longitude == null)) {
      const g = mapGeocode_(row.address);
      if (g) { row.latitude = g.lat; row.longitude = g.lng; }
      else geoFails.push("· " + (row.code || row.name || "") + " : " + row.address);
    }
  });
  if (geoFails.length) {
    const ans = ui.alert("주소 → 좌표 변환 실패 (주소 확인 필요)",
      geoFails.join("\n") + "\n\n좌표 없이 그대로 발행할까요?", ui.ButtonSet.YES_NO);
    if (ans !== ui.Button.YES) return;
  }

  const res = UrlFetchApp.fetch(MAP_ENDPOINT_SYNC, {
    method: "post", contentType: "application/json",
    headers: { "X-Sync-Token": token },
    payload: JSON.stringify(payload), muteHttpExceptions: true
  });

  if (res.getResponseCode() === 200) {
    const data = JSON.parse(res.getContentText()).data;
    ui.alert("발행 완료\n" +
      "카테고리그룹=" + data.groups + ", 카테고리=" + data.categories +
      ", 건물=" + data.buildings + ", 층=" + data.floors +
      ", 장소=" + data.places + ", 운영시간=" + data.operatingHours);
  } else {
    ui.alert("발행 실패 (반영 안 됨):\n" + res.getContentText());
  }
}

// 주소 → 좌표 (Apps Script 내장 지오코딩, 무료/키 불필요). 실패 시 null
function mapGeocode_(address) {
  try {
    const r = Maps.newGeocoder().setLanguage("ko").setRegion("kr").geocode(address);
    if (r.status === "OK" && r.results && r.results.length) {
      const loc = r.results[0].geometry.location;
      return { lat: loc.lat, lng: loc.lng };
    }
  } catch (e) { /* 변환 실패 → null */ }
  return null;
}

// 이미지 업로드 (명지도 전용, domain=MAP). 별도 html 파일 mapUpload 사용
function mapUpload() {
  const html = HtmlService.createHtmlOutputFromFile("mapUpload").setWidth(380).setHeight(210);
  SpreadsheetApp.getUi().showModalDialog(html, "명지도 이미지 업로드");
}

function mapUploadImage(base64, mimeType, filename) {
  const blob = Utilities.newBlob(Utilities.base64Decode(base64), mimeType, filename);
  const res = UrlFetchApp.fetch(MAP_ENDPOINT_UPLOAD + "?domain=MAP", {
    method: "post", payload: { file: blob }, muteHttpExceptions: true
  });
  if (res.getResponseCode() !== 200) throw new Error(res.getContentText());
  const url = JSON.parse(res.getContentText()).data;
  SpreadsheetApp.getActiveRange().setValue(url); // 선택된 셀에 URL 기입
  return url;
}
```

## 3. `mapUpload.html` (새 파일: + → HTML, 파일명 `mapUpload`)

```html
<!DOCTYPE html>
<html><body style="font-family:sans-serif;font-size:13px">
  <p>먼저 <b>이미지 셀</b>을 선택한 뒤 파일을 고르세요. (명지도)</p>
  <input type="file" id="f" accept="image/*"><br><br>
  <button onclick="up()">업로드</button>
  <p id="msg"></p>
  <script>
    function up(){
      var f=document.getElementById('f').files[0];
      if(!f){msg('파일을 선택하세요');return;}
      msg('업로드 중...');
      var reader=new FileReader();
      reader.onload=function(e){
        var b64=e.target.result.split(',')[1];
        google.script.run
          .withSuccessHandler(function(u){msg('완료'); google.script.host.close();})
          .withFailureHandler(function(err){msg('실패: '+err.message);})
          .mapUploadImage(b64, f.type, f.name);
      };
      reader.readAsDataURL(f);
    }
    function msg(t){document.getElementById('msg').textContent=t;}
  </script>
</body></html>
```

## 4. `banner.gs` — onOpen에 한 줄 추가

```javascript
function onOpen() {
  // ... 기존 배너 메뉴 ...
  addMapMenu();   // ← 추가
}
```

## 5. 토큰 (스크립트 속성)

Apps Script → ⚙️ 프로젝트 설정 → 스크립트 속성:
- `BANNER_SYNC_TOKEN` = 서브모듈 `app.sync.banner-token` (기존)
- `MAP_SYNC_TOKEN` = 서브모듈 `app.sync.map-token` (신규)

## 6. 운영 흐름

1. 시트 새로고침(F5) → 상단 `배너` + `명지도` 메뉴
2. `명지도 → ① 시트 초기 세팅` → `서버-` 탭 6개 생성(헤더에 설명 노트)
3. `명지도 → ② 카테고리 기본값 채우기` → 그룹 4개·칩 27개 자동 기입(코드/순서 포함). **운영자는 카테고리 코드를 직접 만들 필요 없음.** 이후엔 `서버-장소`/`서버-건물`에 행만 추가하고 카테고리코드 칸에 위 칩 코드를 적으면 됨
4. 이미지: 셀 선택 → `명지도 → 이미지 업로드` → 파일 선택 → URL 자동 기입 (S3 `static/images/map/`)
5. 입력 후 `명지도 → 발행 (DB 반영)` → 성공 시 섹션별 건수, 실패 시 어느 탭 몇 행이 틀렸는지 알림(반영 안 됨)

### 카테고리 코드 ↔ 라벨 (장소 입력 시 `카테고리코드` 칸에 이 코드 사용)

| 그룹 | 칩(라벨) → 코드 |
|---|---|
| 식사 (food) | 대동명지도 `daedong` · 학생식당 `cafeteria` · 음식점 `restaurant` · 카페 `cafe` · 야식트럭 `night-truck` · 편의점·마트 `mart` |
| 학습·휴식 (study) | 라운지 `lounge` · 열람실 `reading-room` · 스터디룸 `study-room` · 운동 시설 `gym` · 휴게실 `rest-area` · 테라스 `terrace` · 동아리방 `club-room` |
| 편의 (convenience) | 프린트 `printer` · 자동증명발급기 `certificate` · 은행·ATM `bank-atm` · 우편 `post` · 흡연 부스 `smoking` · 보조배터리 `power-bank` · 화장실 `restroom` |
| 건물·이동 (guide) | 건물 `building`(건물목록) · 주차장 `parking` · 캠퍼스 출입구 `campus-gate` · 건물 출입구 `building-gate` · 건물 통로 `passage` · 지름길 `shortcut` · 버스 `bus`(버스화면) |

> 코드는 프론트 칩 클릭 시 서버로 보내는 키다. 프론트가 별도 코드/아이콘키를 쓰고 있으면 위 코드·`아이콘키`를 프론트 값에 맞춰 수정한 뒤 발행할 것.

## 7. 동작 메모

- **upsert**: code(그룹/카테고리/핀) 또는 (건물+층 / 건물+요일) 키. 재발행해도 중복 없음.
- **즐겨찾기 보존**: 핀을 code로 갱신 → 사용자 즐겨찾기 유지.
- **v1은 삭제 미반영**: 시트 행 삭제가 DB 삭제로 이어지지 않음(추후 soft-delete).
- **운영시간은 건물만**. 장소는 위치·추가정보만.
- sync/upload 모두 HTTPS. sync는 `X-Sync-Token` 상수시간 비교 보호.
