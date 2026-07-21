// 신고/차단 API E2E (인증 + 쓰기). 운영 서버 대상.
// baseURL = playwright.config.js (https://api.thingo.kr)
//
// 필요 env (없으면 관련 테스트 skip):
//   THINGO_E2E_EMAIL / THINGO_E2E_PASSWORD  : 로그인용 테스트 계정 (필수)
//   THINGO_E2E_TARGET_UUID                  : 차단 대상 member UUID (있으면 차단/해제 전체 플로우)
//
// 주의: POST /reports 는 실제 관리자 메일을 발송한다. 실행 시 실제 메일이 나간다.
const { test, expect } = require('@playwright/test');
const crypto = require('crypto');

const EMAIL = process.env.THINGO_E2E_EMAIL;
const PASSWORD = process.env.THINGO_E2E_PASSWORD;
const TARGET_UUID = process.env.THINGO_E2E_TARGET_UUID;

let accessToken;
let myUuid;

function authHeaders() {
  return { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' };
}

test.beforeAll(async ({ request }) => {
  test.skip(!EMAIL || !PASSWORD, 'THINGO_E2E_EMAIL/PASSWORD 미설정');

  // 모바일 로그인 → body 로 accessToken 수령
  const res = await request.post('/api/v1/auth/login', {
    headers: { 'X-Client-Type': 'mobile', 'Content-Type': 'application/json' },
    data: { email: EMAIL, password: PASSWORD },
  });
  expect(res.status(), '로그인 성공').toBe(200);
  const body = await res.json();
  accessToken = body.accessToken ?? body.data?.accessToken;
  expect(accessToken, 'accessToken 존재').toBeTruthy();

  // 내 memberUuid (자기 차단 음성 테스트용). 실패해도 무시.
  try {
    const me = await request.get('/api/v1/members/me', { headers: authHeaders() });
    if (me.ok()) {
      const mb = await me.json();
      myUuid = (mb.data ?? mb)?.uuid ?? (mb.data ?? mb)?.memberUuid;
    }
  } catch (_) { /* optional */ }
});

test.describe('신고 API', () => {
  test('신고 접수 성공 → 201 (실제 메일 발송)', async ({ request }) => {
    const res = await request.post('/api/v1/reports', {
      headers: authHeaders(),
      data: {
        targetType: 'BOARD',
        targetUuid: crypto.randomUUID(),
        reason: 'ABUSE',
      },
    });
    expect(res.status(), '신고 201').toBe(201);
    const body = await res.json();
    const data = body.data ?? body;
    expect(data.reasonLabel).toBe('욕설/비하/인신공격');
    expect(data.reportUuid).toBeTruthy();
  });

  test('기타 사유인데 상세 없으면 → 400', async ({ request }) => {
    const res = await request.post('/api/v1/reports', {
      headers: authHeaders(),
      data: { targetType: 'COMMENT', targetUuid: crypto.randomUUID(), reason: 'ETC', etcDetail: '   ' },
    });
    expect(res.status()).toBe(400);
  });

  test('미인증이면 → 403', async ({ request }) => {
    const res = await request.post('/api/v1/reports', {
      headers: { 'Content-Type': 'application/json' },
      data: { targetType: 'BOARD', targetUuid: crypto.randomUUID(), reason: 'ABUSE' },
    });
    expect(res.status()).toBe(403);
  });
});

test.describe('차단 API', () => {
  test('차단 목록 조회 → 200 배열', async ({ request }) => {
    const res = await request.get('/api/v1/blocks', { headers: authHeaders() });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.data ?? body)).toBe(true);
  });

  test('자기 자신 차단 → 400', async ({ request }) => {
    test.skip(!myUuid, 'myUuid 확인 불가(/members/me)');
    const res = await request.post('/api/v1/blocks', {
      headers: authHeaders(),
      data: { targetMemberUuid: myUuid },
    });
    expect(res.status()).toBe(400);
  });

  test('없는 사용자 차단 → 404', async ({ request }) => {
    const res = await request.post('/api/v1/blocks', {
      headers: authHeaders(),
      data: { targetMemberUuid: crypto.randomUUID() },
    });
    expect(res.status()).toBe(404);
  });

  test('차단 → 목록 반영 → 해제 → 목록 제거 (전체 플로우)', async ({ request }) => {
    test.skip(!TARGET_UUID, 'THINGO_E2E_TARGET_UUID 미설정');

    // 차단
    const block = await request.post('/api/v1/blocks', {
      headers: authHeaders(),
      data: { targetMemberUuid: TARGET_UUID },
    });
    expect(block.status(), '차단 201').toBe(201);

    // 목록에 존재
    const after = await (await request.get('/api/v1/blocks', { headers: authHeaders() })).json();
    const listAfter = after.data ?? after;
    expect(listAfter.some((b) => b.memberUuid === TARGET_UUID), '차단 목록 반영').toBe(true);

    // 멱등: 재차단도 201
    const again = await request.post('/api/v1/blocks', {
      headers: authHeaders(),
      data: { targetMemberUuid: TARGET_UUID },
    });
    expect(again.status(), '멱등 차단 201').toBe(201);

    // 해제
    const unblock = await request.delete(`/api/v1/blocks/${TARGET_UUID}`, { headers: authHeaders() });
    expect(unblock.status(), '해제 200').toBe(200);

    // 목록에서 제거
    const final = await (await request.get('/api/v1/blocks', { headers: authHeaders() })).json();
    const listFinal = final.data ?? final;
    expect(listFinal.some((b) => b.memberUuid === TARGET_UUID), '차단 해제 후 제거됨').toBe(false);

    // 이미 해제된 상대 재해제 → 404
    const unblockAgain = await request.delete(`/api/v1/blocks/${TARGET_UUID}`, { headers: authHeaders() });
    expect(unblockAgain.status(), '없는 차단 해제 404').toBe(404);
  });
});
