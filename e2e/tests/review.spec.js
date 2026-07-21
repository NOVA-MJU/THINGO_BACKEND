// 명지도 리뷰 API E2E (운영 서버 대상).
// baseURL = playwright.config.js (https://api.thingo.kr)
//
// 공개 조회/에러 경로는 항상 검증. 작성/좋아요/삭제/프리사인은 인증 필요 → env 없으면 skip.
//
// 필요 env:
//   THINGO_E2E_EMAIL / THINGO_E2E_PASSWORD   : 로그인 계정 (작성/좋아요/삭제/프리사인)
//   THINGO_E2E_REVIEW_PIN_ID                 : 리뷰 작성 가능한 F&B 장소(PLACE) pinId (작성 플로우)
//
// 주의: 배포 전에는 리뷰 엔드포인트가 운영 서버에 없어 공개 GET도 실패한다(배포 후 검증).
const { test, expect } = require('@playwright/test');
const crypto = require('crypto');

const EMAIL = process.env.THINGO_E2E_EMAIL;
const PASSWORD = process.env.THINGO_E2E_PASSWORD;
const REVIEW_PIN_ID = process.env.THINGO_E2E_REVIEW_PIN_ID;

let accessToken;

function authHeaders() {
  return { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' };
}

test.beforeAll(async ({ request }) => {
  if (!EMAIL || !PASSWORD) return; // 공개 테스트는 토큰 없이 진행
  const res = await request.post('/api/v1/auth/login', {
    headers: { 'X-Client-Type': 'mobile', 'Content-Type': 'application/json' },
    data: { email: EMAIL, password: PASSWORD },
  });
  if (res.status() === 200) {
    const body = await res.json();
    accessToken = body.accessToken ?? body.data?.accessToken;
  }
});

test.describe('리뷰 공개 조회', () => {
  test('장소별 목록 - 200 + 페이지 형태', async ({ request }) => {
    const res = await request.get('/api/v1/reviews?pinId=1&page=0&size=10');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('API 요청 성공');
    expect(Array.isArray(body.data.content)).toBe(true);
    expect(typeof body.data.totalElements).toBe('number');
  });

  test('사진·영상 스트립 - 200 + 배열', async ({ request }) => {
    const res = await request.get('/api/v1/reviews/media?pinId=1&limit=10');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.data)).toBe(true);
  });

  test('키워드 카탈로그(전체) - 200 + 3개 그룹', async ({ request }) => {
    const res = await request.get('/api/v1/reviews/keywords');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.data.groups)).toBe(true);
    expect(body.data.groups.length).toBe(3);
    const groupNames = body.data.groups.map((g) => g.group);
    expect(groupNames).toEqual(expect.arrayContaining(['FOOD_PRICE', 'MOOD', 'ETC']));
  });
});

test.describe('리뷰 에러 경로', () => {
  test('없는 리뷰 상세 - 404 REVIEW_NOT_FOUND', async ({ request }) => {
    const res = await request.get(`/api/v1/reviews/${crypto.randomUUID()}`);
    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.error).toBe('REVIEW_NOT_FOUND');
  });

  test('없는 장소 키워드 카탈로그 - 404 MAP_PIN_NOT_FOUND', async ({ request }) => {
    const res = await request.get('/api/v1/reviews/keywords?pinId=99999999');
    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.error).toBe('MAP_PIN_NOT_FOUND');
  });

  test('pinId 없는 목록 - 400', async ({ request }) => {
    const res = await request.get('/api/v1/reviews');
    expect(res.status()).toBe(400);
  });
});

test.describe('리뷰 인증 차단', () => {
  test('비로그인 작성 - 403', async ({ request }) => {
    const res = await request.post('/api/v1/reviews', {
      headers: { 'Content-Type': 'application/json' },
      data: { pinId: 1, keywords: ['KIND'], content: '테스트' },
    });
    expect(res.status()).toBe(403);
  });

  test('비로그인 프리사인 - 403', async ({ request }) => {
    const res = await request.post('/api/v1/s3/presign', {
      headers: { 'Content-Type': 'application/json' },
      data: { domain: 'REVIEW_MEDIA', contentType: 'video/mp4', fileSize: 1024 },
    });
    expect(res.status()).toBe(403);
  });
});

test.describe('영상 프리사인 발급(인증)', () => {
  test('유효 요청 - 200 + uploadUrl/fileUrl', async ({ request }) => {
    test.skip(!accessToken, 'THINGO_E2E_EMAIL/PASSWORD 미설정');
    const res = await request.post('/api/v1/s3/presign', {
      headers: authHeaders(),
      data: { domain: 'REVIEW_MEDIA', contentType: 'video/mp4', fileSize: 1024 * 1024 },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.data.uploadUrl).toContain('http');
    expect(body.data.fileUrl).toContain('/static/images/reviews/');
  });

  test('지원하지 않는 형식 - 400 S3_PRESIGN_UNSUPPORTED_TYPE', async ({ request }) => {
    test.skip(!accessToken, 'THINGO_E2E_EMAIL/PASSWORD 미설정');
    const res = await request.post('/api/v1/s3/presign', {
      headers: authHeaders(),
      data: { domain: 'REVIEW_MEDIA', contentType: 'application/zip', fileSize: 1024 },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('S3_PRESIGN_UNSUPPORTED_TYPE');
  });

  test('용량 초과 - 400 S3_PRESIGN_SIZE_EXCEEDED', async ({ request }) => {
    test.skip(!accessToken, 'THINGO_E2E_EMAIL/PASSWORD 미설정');
    const res = await request.post('/api/v1/s3/presign', {
      headers: authHeaders(),
      data: { domain: 'REVIEW_MEDIA', contentType: 'video/mp4', fileSize: 60 * 1024 * 1024 },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('S3_PRESIGN_SIZE_EXCEEDED');
  });
});

test.describe('리뷰 작성 → 목록 → 좋아요 → 삭제 (전체 플로우, 인증)', () => {
  test('전체 플로우', async ({ request }) => {
    test.skip(!accessToken, 'THINGO_E2E_EMAIL/PASSWORD 미설정');
    test.skip(!REVIEW_PIN_ID, 'THINGO_E2E_REVIEW_PIN_ID 미설정');

    // 작성 (미디어 없이 텍스트+키워드)
    const create = await request.post('/api/v1/reviews', {
      headers: authHeaders(),
      data: { pinId: Number(REVIEW_PIN_ID), keywords: ['KIND'], content: 'E2E 테스트 리뷰' },
    });
    expect(create.status(), '작성 201').toBe(201);
    const created = (await create.json()).data;
    const reviewUuid = created.reviewUuid;
    expect(reviewUuid).toBeTruthy();

    // 목록에 노출
    const list = await (await request.get(`/api/v1/reviews?pinId=${REVIEW_PIN_ID}&size=50`)).json();
    expect(list.data.content.some((r) => r.reviewUuid === reviewUuid), '목록 반영').toBe(true);

    // 좋아요 토글(추가)
    const like = await request.post(`/api/v1/reviews/${reviewUuid}/like`, { headers: authHeaders() });
    expect(like.status()).toBe(200);
    const likeBody = (await like.json()).data;
    expect(typeof likeBody.likeCount).toBe('number');

    // 좋아요 취소
    await request.post(`/api/v1/reviews/${reviewUuid}/like`, { headers: authHeaders() });

    // 삭제 → 204
    const del = await request.delete(`/api/v1/reviews/${reviewUuid}`, { headers: authHeaders() });
    expect(del.status(), '삭제 204').toBe(204);

    // 삭제 후 상세 404
    const gone = await request.get(`/api/v1/reviews/${reviewUuid}`, { headers: authHeaders() });
    expect(gone.status(), '삭제 후 404').toBe(404);
  });

  test('비F&B 장소에 F&B 전용 키워드 - 400 (환경 있으면)', async ({ request }) => {
    test.skip(!accessToken, 'THINGO_E2E_EMAIL/PASSWORD 미설정');
    const nonFnbPin = process.env.THINGO_E2E_NON_FNB_PIN_ID;
    test.skip(!nonFnbPin, 'THINGO_E2E_NON_FNB_PIN_ID 미설정');
    const res = await request.post('/api/v1/reviews', {
      headers: authHeaders(),
      data: { pinId: Number(nonFnbPin), keywords: ['TASTY'], content: '가성비' },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('REVIEW_KEYWORD_NOT_ALLOWED_FOR_CATEGORY');
  });
});
