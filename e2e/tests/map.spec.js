// 명지도 API E2E (운영 서버 대상).
//
// 데이터가 비어있는 상태에서도 안전하게 확인 가능한 것만 검증한다(쓰기 없음):
//  - 조회 엔드포인트가 살아있고 공통 응답 래퍼(ApiResponse) 형태로 200을 준다
//  - 에러 경로가 약속된 ErrorResponse(error/message/status) 형태로 정확한 코드를 준다
//  - 인증이 필요한 즐겨찾기는 비로그인 시 차단된다
//
// 데이터 적재 후의 조회 왕복(sync → 목록/상세)은 Testcontainers 통합테스트(MapSyncE2EIT)가 담당한다.
const { test, expect } = require('@playwright/test');

test.describe('명지도 조회 API', () => {
  test('건물 목록 - 200 + 배열', async ({ request }) => {
    const res = await request.get('/api/v1/map/buildings');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('API 요청 성공');
    expect(Array.isArray(body.data)).toBe(true);
  });
});

test.describe('명지도 에러 경로', () => {
  test('없는 건물 상세 - 404 MAP_PIN_NOT_FOUND', async ({ request }) => {
    const res = await request.get('/api/v1/map/buildings/99999999');
    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.error).toBe('MAP_PIN_NOT_FOUND');
  });

  test('없는 장소 상세 - 404 MAP_PIN_NOT_FOUND', async ({ request }) => {
    const res = await request.get('/api/v1/map/places/99999999');
    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.error).toBe('MAP_PIN_NOT_FOUND');
  });

  test('없는 칩 목록 - 404 MAP_CATEGORY_NOT_FOUND', async ({ request }) => {
    const res = await request.get('/api/v1/map/categories/__nope__/pins');
    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.error).toBe('MAP_CATEGORY_NOT_FOUND');
  });
});

test.describe('명지도 인증', () => {
  test('비로그인 즐겨찾기 토글 - 403 차단', async ({ request }) => {
    const res = await request.post('/api/v1/map/favorites?pinId=1');
    expect(res.status()).toBe(403);
  });
});

test.describe('명지도 동기화 인증', () => {
  // 쓰기 없음: 잘못된 토큰은 본문 파싱 전에 차단되므로 데이터 변경 없이 인증 경로만 검증
  test('잘못된 토큰 동기화 - 401 MAP_SYNC_UNAUTHORIZED', async ({ request }) => {
    const res = await request.post('/api/v1/sync/map', {
      headers: { 'X-Sync-Token': 'wrong-token' },
      data: {},
    });
    expect(res.status()).toBe(401);
    const body = await res.json();
    expect(body.error).toBe('MAP_SYNC_UNAUTHORIZED');
  });
});
