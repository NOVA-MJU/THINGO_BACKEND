-- 명대신문 이미지 URL http -> https 정규화 (일회성).
--
-- 배경: 원본(news.mju.ac.kr)이 og:image 를 http 로 내려줘 RDB 에 http 로 적재됨.
--       동일 경로가 https 로도 정상 서빙되며(인증서 정상), https 프론트에서 일관되게 노출하려고 통일.
--       애플리케이션은 News.getImageUrl() 에서 read 시점 정규화도 하므로 이 SQL 은 컬럼 값 자체 청소용.
--
-- 적용 대상:
--   - news.image_url
--   - unified_search_index.image_url (NEWS 문서; 재색인해도 동일 결과지만 즉시 반영용)

BEGIN;

UPDATE news
SET image_url = replace(image_url, 'http://', 'https://')
WHERE image_url LIKE 'http://%';

UPDATE unified_search_index
SET image_url = replace(image_url, 'http://', 'https://')
WHERE image_url LIKE 'http://%';

COMMIT;

-- 검증
-- SELECT count(*) FROM news WHERE image_url LIKE 'http://%';                  -- 기대: 0
-- SELECT count(*) FROM unified_search_index WHERE image_url LIKE 'http://%';  -- 기대: 0
