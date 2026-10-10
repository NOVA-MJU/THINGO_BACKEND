# 학사안내문 검색 데이터 갱신

학사안내문은 공지 게시판 레코드로 복제하지 않는다. PDF를 페이지·제도 단위로
구조화해 `ACADEMIC_GUIDE` 검색 유형으로 저장하고, 기존 통합검색 인덱스에서 공지와
함께 조회한다.

## 갱신 절차

```powershell
python -m pip install pypdf pdfplumber
python scripts/academic-guide/import_academic_guide.py `
  "C:\path\to\학사안내문.pdf" `
  "src\main\resources\academic\academic_guide_2026_2.json"
```

새 학기 자료를 반영할 때는 스크립트의 `SOURCE_URL`, `PUBLISHED_AT`, `SECTIONS`와
검증 규칙(`curated_documents`)도 함께 갱신한다. 생성 후 다음 사항을 확인한다.

1. `documentCount`와 실제 `documents` 배열 크기가 같은지 확인한다.
2. 졸업요건 표의 적용 학번, 단과대·학과, 이수학점과 원문 쪽수를 대조한다.
3. 학문기초교양과 전공이해 기초교과목을 서로 다른 규칙으로 유지한다.
4. `POST /api/v1/search/sync/academic-guides`를 실행해 학사안내문만 통합검색 인덱스에 반영한다.
   `X-Sync-Token` 헤더에 `app.sync.search-token` 값(MJS-BACK-SECURITY)을 넣어야 한다.
5. `미휴 학기교`, `정외 법학 복전 학기교`, `2025학번 졸업학점`을 검색해 결과와
   원문 페이지를 확인한다.

구조화된 각 문서는 `admissionYearFrom/To`, `colleges`, `departments`, `keywords`,
`documentPage`, `sourceUrl`을 가진다. AI 요약은 이 메타데이터를 본문 앞부분에서
읽어 서로 다른 학번·학과 규정을 합치지 않도록 한다.
