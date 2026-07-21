// 명지도 API E2E 설정. 운영 서버를 대상으로 순수 API 요청(브라우저 미사용)을 보낸다.
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 30000,
  reporter: [['list']],
  use: {
    baseURL: process.env.MAP_E2E_BASE_URL || 'https://api.thingo.kr',
    extraHTTPHeaders: { Accept: 'application/json' },
  },
});
