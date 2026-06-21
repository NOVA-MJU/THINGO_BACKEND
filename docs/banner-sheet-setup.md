# 배너 구글 시트 ↔ 백엔드 연동 가이드

운영팀이 구글 시트에서 배너를 편집하면 자동으로 백엔드 DB에 반영된다.
이미지는 시트 안에서 바로 업로드(파일 선택만) → URL 자동 기입.

## 1. 시트 구조

탭 이름: `배너` (다르면 아래 스크립트 `SHEET_NAME` 수정)

1행은 헤더(아래 이름 정확히, 순서는 무관 — 스크립트가 이름으로 찾음):

| 제목 | 한줄소개 | 이미지 | 카테고리 | 링크 | 순서 | 노출여부 | 노출시작 | 노출종료 |
|---|---|---|---|---|---|---|---|---|

- **제목**: 필수
- **이미지**: 메뉴 업로드가 자동 기입(직접 URL 붙여넣기도 가능)
- **카테고리**: 데이터 확인(드롭다운) 권장 — 오타 방지
- **링크**: 배너 클릭 시 이동 URL
- **순서**: 숫자, 작을수록 먼저(비우면 0)
- **노출여부**: 체크박스 TRUE/FALSE(비우면 TRUE)
- **노출시작/종료**: 날짜 `yyyy-MM-dd` 또는 빈 값(제한 없음). 기간 밖이면 앱에 안 보임

## 2. Apps Script 설치

시트 → 확장 프로그램 → Apps Script.

### Code.gs

```javascript
// API 서버는 api.thingo.kr (thingo.kr은 CDN이라 HTML을 돌려줌 - 절대 쓰지 말 것)
const ENDPOINT_SYNC   = "https://api.thingo.kr/api/v1/sync/banners";
const ENDPOINT_UPLOAD = "https://api.thingo.kr/api/v1/s3/upload";
const SHEET_NAME = "배너";
const HEADER = {
  title: "제목", oneLineIntro: "한줄소개", imageUrl: "이미지",
  category: "카테고리", linkUrl: "링크", displayOrder: "순서",
  active: "노출여부", startAt: "노출시작", endAt: "노출종료"
};
// 발행 전 반드시 채워져야 하는 필수 컬럼 (작성 시작한 행 기준)
const REQUIRED = ["title", "imageUrl", "category"];

function onOpen() {
  SpreadsheetApp.getUi().createMenu("배너")
    .addItem("이미지 업로드", "showUploadDialog")
    .addItem("발행 (검증 후 반영)", "syncBanners")
    .addToUi();
}

// 최초 1회만 실행 (메뉴에 없음 - Apps Script 편집기에서 직접 실행).
// 재실행하면 드롭다운/체크박스가 코드 기본값으로 덮어써지므로 세팅 후에는 호출하지 말 것.
// 탭/헤더/드롭다운/체크박스/날짜형식 자동 생성
function setupSheet() {
  const ss = SpreadsheetApp.getActive();
  let sheet = ss.getSheetByName(SHEET_NAME) || ss.insertSheet(SHEET_NAME);

  const headers = ["제목","한줄소개","이미지","카테고리","링크","순서","노출여부","노출시작","노출종료"];
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight("bold");
  sheet.setFrozenRows(1);

  // 카테고리 드롭다운 (아래 목록을 실제 카테고리로 교체)
  const categories = ["이벤트","공지","광고","프로모션"];
  const catRule = SpreadsheetApp.newDataValidation().requireValueInList(categories, true).build();
  sheet.getRange(2, 4, 1000, 1).setDataValidation(catRule);   // 카테고리 열

  sheet.getRange(2, 7, 1000, 1).insertCheckboxes();           // 노출여부 열
  sheet.getRange(2, 8, 1000, 2).setNumberFormat("yyyy-mm-dd"); // 노출시작/종료 열

  SpreadsheetApp.getUi().alert("배너 시트 세팅 완료. 카테고리 목록은 setupSheet의 categories 배열에서 수정하세요.");
}

function getToken_() {
  return PropertiesService.getScriptProperties().getProperty("BANNER_SYNC_TOKEN");
}

// 발행: 미완성 행이 있으면 반영하지 않고(기존 데이터 유지) 어디가 비었는지 알려줌
function syncBanners() {
  const ui = SpreadsheetApp.getUi();
  const sheet = SpreadsheetApp.getActive().getSheetByName(SHEET_NAME);
  const values = sheet.getDataRange().getValues();
  const header = values[0];
  const col = {};
  Object.keys(HEADER).forEach(k => col[k] = header.indexOf(HEADER[k]));
  if (col.title < 0) { ui.alert("'제목' 헤더를 찾을 수 없습니다"); return; }

  const get = (r, k) => col[k] >= 0 ? r[col[k]] : "";
  const isFilled = v => String(v == null ? "" : v).trim() !== "";
  const fmtDate = v => {
    if (!v) return "";
    if (Object.prototype.toString.call(v) === "[object Date]")
      return Utilities.formatDate(v, Session.getScriptTimeZone(), "yyyy-MM-dd");
    return String(v).trim();
  };

  const rows = [];
  const errors = [];
  for (let i = 1; i < values.length; i++) {     // i=0은 헤더
    const r = values[i];
    const started = REQUIRED.some(k => isFilled(get(r, k))); // 작성 시작한 행만 검사
    if (!started) continue;                                   // 완전 빈 행은 무시

    const missing = REQUIRED.filter(k => !isFilled(get(r, k))).map(k => HEADER[k]);
    if (missing.length) {                                     // 작성 중(미완성) 행 발견
      errors.push((i + 1) + "행: " + missing.join(", ") + " 비어있음");
      continue;
    }
    rows.push({
      title: get(r, "title"),
      oneLineIntro: get(r, "oneLineIntro"),
      imageUrl: get(r, "imageUrl"),
      category: get(r, "category"),
      linkUrl: get(r, "linkUrl"),
      displayOrder: Number(get(r, "displayOrder")) || 0,
      active: (function(){ const v = get(r,"active"); return v !== false && String(v).toUpperCase() !== "FALSE"; })(),
      startAt: fmtDate(get(r, "startAt")),
      endAt: fmtDate(get(r, "endAt"))
    });
  }

  // 미완성 행이 하나라도 있으면 발행 중단 (기존 데이터 그대로 유지)
  if (errors.length) {
    ui.alert("발행 취소 - 아래 항목을 채운 뒤 다시 발행하세요:\n\n" + errors.join("\n"));
    return;
  }
  // 0건이면 전체 삭제 의미 - 실수 방지로 확인 후 진행 (빈 배열 전송 → DB 전체 비움)
  if (rows.length === 0) {
    const yn = ui.alert("배너가 0건입니다. 앱에서 모든 배너를 내리시겠습니까?",
                        ui.ButtonSet.YES_NO);
    if (yn !== ui.Button.YES) return;
  }

  const res = UrlFetchApp.fetch(ENDPOINT_SYNC, {
    method: "post", contentType: "application/json",
    headers: { "X-Sync-Token": getToken_() },
    payload: JSON.stringify({ rows }),
    muteHttpExceptions: true
  });
  if (res.getResponseCode() === 200) {
    ui.alert("발행 완료: 배너 " + rows.length + "건 반영되었습니다.");
  } else {
    ui.alert("발행 실패 (반영 안 됨):\n" + res.getContentText());
  }
}

function showUploadDialog() {
  const html = HtmlService.createHtmlOutputFromFile("upload").setWidth(380).setHeight(200);
  SpreadsheetApp.getUi().showModalDialog(html, "배너 이미지 업로드");
}

// 다이얼로그에서 호출 (밑줄 없는 이름 = 클라이언트 호출 가능)
function uploadImage(base64, mimeType, filename) {
  const blob = Utilities.newBlob(Utilities.base64Decode(base64), mimeType, filename);
  const res = UrlFetchApp.fetch(ENDPOINT_UPLOAD + "?domain=BANNER", {
    method: "post", payload: { file: blob }, muteHttpExceptions: true
  });
  if (res.getResponseCode() !== 200) throw new Error(res.getContentText());
  const url = JSON.parse(res.getContentText()).data;
  SpreadsheetApp.getActiveRange().setValue(url); // 선택된 이미지 셀에 URL 기입
  return url;
}
```

### upload.html (파일 추가: + → HTML)

```html
<!DOCTYPE html>
<html><body style="font-family:sans-serif;font-size:13px">
  <p>먼저 <b>이미지 셀</b>을 선택한 뒤 파일을 고르세요.</p>
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
          .uploadImage(b64, f.type, f.name);
      };
      reader.readAsDataURL(f);
    }
    function msg(t){document.getElementById('msg').textContent=t;}
  </script>
</body></html>
```

## 3. 토큰 설정 (코드에 평문 금지)

Apps Script → 프로젝트 설정(⚙️) → 스크립트 속성 → 추가
- 이름: `BANNER_SYNC_TOKEN`
- 값: 백엔드 `application.yml`의 `app.sync.banner-token`과 동일한 시크릿

## 4. 발행 방식 (수동 발행 권장)

**기본은 수동 발행이다.** 운영팀이 자유롭게 작성/수정하는 동안에는 아무것도 반영되지 않고,
**메뉴 `배너 → 발행 (검증 후 반영)`** 을 누를 때만 검증 후 한 번에 반영된다.
- 작성 중(미완성) 데이터가 실수로 올라가는 일 없음
- 필수값(제목/이미지/카테고리) 안 채운 행이 있으면 어느 행인지 알려주고 **반영 취소**(기존 데이터 유지)
- 편집 빈도/타이밍 문제 없음 (발행 누를 때만 호출)

> 자동반영을 원하면 트리거(⏰ → 추가 → 함수 `syncBanners` / 소스 "스프레드시트에서" / 유형 "변경 시")를 걸 수 있다.
> 단 이 경우에도 미완성 행이 있으면 발행이 자동 취소되므로(검증 통과 시에만 반영) 깨진 데이터는 안 올라간다.
> 편집할 때마다 호출되는 점만 감수하면 됨. **권장은 수동 발행.**

## 5. 운영 흐름

1. 이미지 셀 선택 → 메뉴 `배너 → 이미지 업로드` → 파일 선택 → URL 자동 기입
2. 나머지 칸 입력(드롭다운/체크박스/날짜)
3. 다 채웠으면 메뉴 `배너 → 발행 (검증 후 반영)` 클릭 → 검증 통과 시 앱 반영
4. 미완성 행이 있으면 알림에 표시 → 채운 뒤 다시 발행

## 보안 메모

- `POST /api/v1/s3/upload`는 현재 무인증(permitAll). 누구나 S3 업로드 가능. 배너 한정으로 토큰 검증을 태울지는 별도 결정(기존 앱 업로드와 호환 영향 확인 필요).
- sync 엔드포인트는 `X-Sync-Token` 상수시간 비교로 보호. 토큰은 `MJS-BACK-SECURITY/application.yml`의 `app.sync.banner-token`에 보관(비공개 secrets 레포). HTTPS 필수.
