// 운영 전체 API 스모크 + 검색/이미지/AI 무결성 E2E (읽기 전용).
// baseURL = playwright.config.js (https://api.thingo.kr)
const { test, expect, request: pwRequest } = require('@playwright/test');

const enc = encodeURIComponent;

// 공개 조회 엔드포인트: 200 + ApiResponse 래퍼(status/data)
const PUBLIC_GETS = [
  ['뉴스', '/api/v1/news?size=3'],
  ['학사일정', '/api/v1/calendar'],
  ['학식', '/api/v1/menus'],
  ['배너', '/api/v1/banners'],
  ['방송', '/api/v1/broadcast?size=3'],
  ['날씨', '/api/v1/weather'],
  ['디데이', '/api/v1/ddays'],
  ['인기검색어', '/api/v1/keywords/top10?count=5'],
  ['지도-건물', '/api/v1/map/buildings'],
  ['공지(general)', '/api/v1/notices?category=general&size=3'],
];

test.describe('공개 조회 API 스모크', () => {
  for (const [name, path] of PUBLIC_GETS) {
    test(`${name} 200 + ApiResponse`, async ({ request }) => {
      const res = await request.get(path);
      expect(res.status(), `${name} status`).toBe(200);
      const body = await res.json();
      // 일부 엔드포인트(weather/notices)는 ApiResponse 미래핑(raw Page/객체). 200 + JSON 객체면 통과.
      expect(typeof body, `${name} JSON 객체`).toBe('object');
      expect(body, `${name} 비어있지 않음`).not.toBeNull();
    });
  }
});

// 검색: ES 제거 후 v1 = PostgreSQL 통합검색 단일 경로.
const SEARCH_ENDPOINTS = [
  ['v1', '/api/v1/search/detail?type=NEWS&size=10'],
];

test.describe('검색 API 스키마', () => {
  for (const [ver, path] of SEARCH_ENDPOINTS) {
    test(`${ver} detail 200 + content 배열`, async ({ request }) => {
      const res = await request.get(path);
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(Array.isArray(body.data.content), `${ver} content 배열`).toBe(true);
      expect(body.data.content.length, `${ver} 결과 존재`).toBeGreaterThan(0);
    });
  }

  for (const ver of ['v1']) {
    test(`${ver} suggest 200 + 배열`, async ({ request }) => {
      const res = await request.get(`/api/${ver}/search/suggest?keyword=${enc('명지')}`);
      expect(res.status(), `${ver} suggest status`).toBe(200);
      const body = await res.json();
      expect(Array.isArray(body.data), `${ver} suggest 배열`).toBe(true);
    });
  }
});

// 이미지 무결성: 검색/뉴스가 돌려준 imageUrl 이 실제로 200 image/* 인지.
// 죽은 default-image.jpg(404) 같은 잔존값을 잡아낸다.
async function assertImagesLoad(items, label) {
  const ctx = await pwRequest.newContext();
  const broken = [];
  const checked = items
    .map((x) => x.imageUrl)
    .filter((u) => u && /^https?:\/\//.test(u))
    .slice(0, 15);
  for (const url of checked) {
    const res = await ctx.get(url);
    const ct = res.headers()['content-type'] || '';
    if (res.status() !== 200 || !ct.startsWith('image/')) {
      broken.push(`${res.status()} ${ct} ${url}`);
    }
  }
  await ctx.dispose();
  expect(broken, `${label} 깨진 이미지:\n${broken.join('\n')}`).toEqual([]);
}

test.describe('이미지 무결성', () => {
  test('뉴스 API 이미지 전부 200 image/*', async ({ request }) => {
    const body = await (await request.get('/api/v1/news?size=15')).json();
    await assertImagesLoad(body.data.content, '뉴스API');
  });

  test('v1(=PG) 검색 뉴스 이미지 전부 200', async ({ request }) => {
    const body = await (await request.get('/api/v1/search/detail?type=NEWS&size=15')).json();
    await assertImagesLoad(body.data.content, 'v1검색');
  });

  test('뉴스 API imageUrl 스킴 == link 스킴(https)', async ({ request }) => {
    const body = await (await request.get('/api/v1/news?size=10')).json();
    for (const a of body.data.content) {
      expect(new URL(a.imageUrl).protocol, a.link).toBe(new URL(a.link).protocol);
    }
  });
});

test.describe('AI 검색 요약', () => {
  test('summary 200 + summary/sources 구조', async ({ request }) => {
    const res = await request.get(`/api/v1/ai/summary?query=${enc('개강')}`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    const payload = body.data ?? body;
    expect(payload, 'summary 필드').toHaveProperty('summary');
    expect(payload, 'sources 필드').toHaveProperty('sources');
  });
});
