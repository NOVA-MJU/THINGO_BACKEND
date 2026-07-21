# Search v2 API 호출 가이드

PostgreSQL FTS 기반 검색 v2 API 실제 호출 방법 및 예시.

## Postman 사용

1. Postman → Import → File → `docs/postman/search-v2.postman_collection.json` 선택
2. Environment 변수 설정
   - `baseUrl`: `http://localhost:8080` (로컬) 또는 `https://api.thingo.kr` (운영)
   - `jwtToken`: 액세스 토큰 (인증 필요한 엔드포인트만)
3. 컬렉션 좌측 패널 → 원하는 요청 → Send
4. 응답 확인

## curl 예시

### 1. 인덱스 전체 재구축
```bash
curl -X POST "http://localhost:8080/api/v2/search/sync"
```

### 2. 기본 검색
```bash
curl "http://localhost:8080/api/v2/search/detail?keyword=장학금"
```

### 3. type 필터
```bash
curl "http://localhost:8080/api/v2/search/detail?keyword=장학금&type=NOTICE"
```

### 4. type + category + 최신순
```bash
curl "http://localhost:8080/api/v2/search/detail?keyword=장학금&type=NOTICE&category=scholarship&order=latest"
```

### 5. 페이지 이동
```bash
curl "http://localhost:8080/api/v2/search/detail?keyword=기숙사&page=1&size=10"
```

### 6. keyword 비우고 목록 조회
```bash
curl "http://localhost:8080/api/v2/search/detail?type=NEWS&order=latest&size=20"
```

### 7. 자동완성
```bash
curl "http://localhost:8080/api/v2/search/suggest?keyword=장학"
```

## 로컬 서버 기동

```bash
./gradlew bootRun
```

기동 후 Swagger UI:
- v1 (ES): `http://localhost:8080/api/docs` → "검색 기능"
- v2 (PG): `http://localhost:8080/api/docs` → "검색 기능 (v2 / PostgreSQL FTS)"

## 응답 스키마

```json
{
  "status": "API 요청 성공",
  "data": {
    "content": [
      {
        "id": "NOTICE:13317",
        "highlightedTitle": "2025학년도 8월 <em>장학금</em> 안내",
        "highlightedContent": "...장학금 신청 대상자 일정...",
        "date": "2025-05-19T01:39:00Z",
        "link": "https://www.mju.ac.kr/...",
        "category": "scholarship",
        "type": "notice",
        "imageUrl": "",
        "score": 0.7821,
        "authorName": "",
        "likeCount": 0,
        "commentCount": 0
      }
    ],
    "totalElements": 42,
    "totalPages": 5,
    "size": 10,
    "number": 0,
    "first": true,
    "last": false,
    "numberOfElements": 10,
    "empty": false
  },
  "timestamp": "2026-05-19T10:00:00.000000"
}
```
