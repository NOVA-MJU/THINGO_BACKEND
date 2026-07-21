// 명대신문(뉴스) 크롤링 결과 E2E (운영 서버 대상).
//
// 목적: "크롤러 의도 == 실제 저장/노출 결과" 인지 검증한다.
//  1) /api/v1/news 가 ApiResponse 래퍼 + 7개 필드를 약속대로 준다
//  2) 카테고리는 크롤러 수집 대상(REPORT/SOCIETY)만 노출된다
//  3) 썸네일 이미지가 실제로 로딩된다(원본 사이트에서 200 image/*)
//  4) [현재 깨짐] 이미지 URL 프로토콜이 저장소마다 다르다
//     - news RDB(/api/v1/news)     : http://  (og:image 그대로)
//     - PG 검색(/api/v2/search)     : http://  (현 RDB 재동기화분)
//     - ES 검색(/api/v1/search)     : https:// (폐기된 옛 크롤러 잔존 색인)
//     같은 응답 안에서도 link 는 https, imageUrl 은 http 로 갈린다.
//  5) 실제 브라우저에서 기사/썸네일이 렌더되는지 확인
//
// 4) 의 일관성 테스트는 "올바른 불변식"을 단언하므로 결함이 있으면 FAIL 한다.
//    수정(크롤러/색인 정규화) 후에는 그대로 PASS 가 되어야 한다.
const { test, expect } = require('@playwright/test');

const NEWS_HOST = 'news.mju.ac.kr';
const ALLOWED_CATEGORIES = ['REPORT', 'SOCIETY'];

async function fetchNews(request, params = 'size=20') {
  const res = await request.get(`/api/v1/news?${params}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return body;
}

test.describe('명대신문 조회 API 구조', () => {
  test('ApiResponse 래퍼 + Page 구조 + 7개 필드', async ({ request }) => {
    const body = await fetchNews(request);
    expect(body.status).toBe('API 요청 성공');
    expect(Array.isArray(body.data.content)).toBe(true);
    expect(body.data.content.length).toBeGreaterThan(0);

    const article = body.data.content[0];
    for (const field of ['title', 'date', 'reporter', 'imageUrl', 'summary', 'link', 'category']) {
      expect(article, `필드 누락: ${field}`).toHaveProperty(field);
    }
    expect(article.title).toBeTruthy();
    expect(article.link).toContain('articleView.html?idxno=');
  });

  test('카테고리는 수집 대상(REPORT/SOCIETY)만', async ({ request }) => {
    const body = await fetchNews(request, 'size=50');
    const categories = [...new Set(body.data.content.map((a) => a.category))];
    for (const category of categories) {
      expect(ALLOWED_CATEGORIES, `예상 밖 카테고리: ${category}`).toContain(category);
    }
  });

  test('정렬: 최신순(date DESC)', async ({ request }) => {
    const body = await fetchNews(request, 'size=10');
    const dates = body.data.content.map((a) => a.date);
    const sorted = [...dates].sort().reverse();
    expect(dates).toEqual(sorted);
  });
});

test.describe('썸네일 이미지 실제 로딩', () => {
  test('상위 5건 imageUrl 이 원본에서 200 image/* 로 응답', async ({ request }) => {
    const body = await fetchNews(request, 'size=5');
    for (const article of body.data.content) {
      const res = await request.get(article.imageUrl);
      expect(res.status(), `이미지 실패: ${article.imageUrl}`).toBe(200);
      expect(res.headers()['content-type']).toMatch(/^image\//);
    }
  });
});

test.describe('이미지 URL 프로토콜 일관성 (현재 결함 노출)', () => {
  test('news 응답: imageUrl 스킴이 link 스킴과 같아야 한다', async ({ request }) => {
    const body = await fetchNews(request, 'size=10');
    for (const article of body.data.content) {
      const imageScheme = new URL(article.imageUrl).protocol;
      const linkScheme = new URL(article.link).protocol;
      expect(imageScheme, `불일치 idx=${article.link}`).toBe(linkScheme);
    }
  });

  test('통합검색(v1=PG) 뉴스 imageUrl 전부 https', async ({ request }) => {
    const res = await request.get('/api/v1/search/detail?type=NEWS&size=30');
    expect(res.status()).toBe(200);
    const body = await res.json();
    const items = body.data.content.filter((x) => x.imageUrl && x.imageUrl.includes(NEWS_HOST));
    expect(items.length, 'news 이미지 결과 없음').toBeGreaterThan(0);
    const schemes = [...new Set(items.map((x) => new URL(x.imageUrl).protocol))];
    expect(schemes, `스킴 혼재/비https: ${schemes}`).toEqual(['https:']);
  });
});

test.describe('브라우저 렌더 검증', () => {
  test('기사 원문 + 썸네일이 실제 브라우저에서 로딩', async ({ request, browser }) => {
    const body = await fetchNews(request, 'size=1');
    const article = body.data.content[0];

    const context = await browser.newContext();
    const page = await context.newPage();
    // 기사 링크(https) 진입 - og:image 메타 확인
    const resp = await page.goto(article.link, { waitUntil: 'domcontentloaded' });
    expect(resp.ok()).toBe(true);
    const ogImage = await page.locator('meta[property="og:image"]').getAttribute('content');
    expect(ogImage).toContain(NEWS_HOST);

    // 썸네일을 https 페이지 컨텍스트에서 강제 로딩 → naturalWidth 로 렌더 확인
    const loaded = await page.evaluate(
      (src) =>
        new Promise((resolve) => {
          const img = new Image();
          img.onload = () => resolve(img.naturalWidth > 0);
          img.onerror = () => resolve(false);
          img.src = src;
        }),
      article.imageUrl,
    );
    expect(loaded, `브라우저 썸네일 로딩 실패: ${article.imageUrl}`).toBe(true);

    await context.close();
  });
});
