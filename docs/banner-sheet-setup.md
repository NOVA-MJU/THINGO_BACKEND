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
const ENDPOINT_SYNC   = "https://thingo.kr/api/v1/sync/banners";
const ENDPOINT_UPLOAD = "https://thingo.kr/api/v1/s3/upload";
const SHEET_NAME = "배너";
const HEADER = {
  title: "제목", oneLineIntro: "한줄소개", imageUrl: "이미지",
  category: "카테고리", linkUrl: "링크", displayOrder: "순서",
  active: "노출여부", startAt: "노출시작", endAt: "노출종료"
};

function onOpen() {
  SpreadsheetApp.getUi().createMenu("배너")
    .addItem("① 시트 초기 세팅", "setupSheet")
    .addItem("이미지 업로드", "showUploadDialog")
    .addItem("지금 동기화", "syncBanners")
    .addToUi();
}

// 한 번만 실행: 탭/헤더/드롭다운/체크박스/날짜형식 자동 생성
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

function syncBanners() {
  const sheet = SpreadsheetApp.getActive().getSheetByName(SHEET_NAME);
  const values = sheet.getDataRange().getValues();
  const header = values[0];
  const col = {};
  Object.keys(HEADER).forEach(k => col[k] = header.indexOf(HEADER[k]));
  if (col.title < 0) throw new Error("'제목' 헤더를 찾을 수 없습니다");

  const get = (r, k) => col[k] >= 0 ? r[col[k]] : "";
  const fmtDate = v => {
    if (!v) return "";
    if (Object.prototype.toString.call(v) === "[object Date]")
      return Utilities.formatDate(v, Session.getScriptTimeZone(), "yyyy-MM-dd");
    return String(v).trim();
  };

  const rows = values.slice(1)
    .filter(r => String(r[col.title]).trim() !== "")
    .map(r => ({
      title: get(r, "title"),
      oneLineIntro: get(r, "oneLineIntro"),
      imageUrl: get(r, "imageUrl"),
      category: get(r, "category"),
      linkUrl: get(r, "linkUrl"),
      displayOrder: Number(get(r, "displayOrder")) || 0,
      active: (function(){ const v = get(r,"active"); return v !== false && String(v).toUpperCase() !== "FALSE"; })(),
      startAt: fmtDate(get(r, "startAt")),
      endAt: fmtDate(get(r, "endAt"))
    }));

  const res = UrlFetchApp.fetch(ENDPOINT_SYNC, {
    method: "post", contentType: "application/json",
    headers: { "X-Sync-Token": getToken_() },
    payload: JSON.stringify({ rows }),
    muteHttpExceptions: true
  });
  const ok = res.getResponseCode() === 200;
  SpreadsheetApp.getActive().toast(
    ok ? "동기화 완료" : "동기화 실패: " + res.getContentText(),
    ok ? "배너" : "오류", ok ? 3 : 6);
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
- 값: 백엔드 환경변수 `BANNER_SYNC_TOKEN`과 동일한 시크릿

## 4. 자동반영 트리거

Apps Script → 트리거(⏰) → 트리거 추가
- 함수: `syncBanners`
- 이벤트 소스: 스프레드시트에서
- 이벤트 유형: **변경 시(On change)** (단순 onEdit는 외부 호출 불가 — 반드시 설치형)
- 저장 후 최초 1회 권한 승인

메뉴 `배너 → 지금 동기화`로 수동 실행도 가능.

## 5. 운영 흐름

1. 이미지 셀 선택 → 메뉴 `배너 → 이미지 업로드` → 파일 선택 → URL 자동 기입
2. 나머지 칸 입력(드롭다운/체크박스/날짜)
3. 셀 수정 시 onChange가 자동 동기화 → 앱 반영

## 보안 메모

- `POST /api/v1/s3/upload`는 현재 무인증(permitAll). 누구나 S3 업로드 가능. 배너 한정으로 토큰 검증을 태울지는 별도 결정(기존 앱 업로드와 호환 영향 확인 필요).
- sync 엔드포인트는 `X-Sync-Token` 상수시간 비교로 보호. prod는 `BANNER_SYNC_TOKEN`을 환경변수로 주입(HTTPS 필수).
