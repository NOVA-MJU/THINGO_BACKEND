-- PostgreSQL 통합 검색 인덱스 초기화 스크립트
-- 멱등 실행. 애플리케이션 기동 시 PgSearchSchemaInitializer 가 적용한다.
--
-- 적용 대상
--   1. pg_trgm 확장 (similarity / word_similarity / gin_trgm_ops)
--   2. GIN 인덱스 3종
--      - search_vector  (FTS)
--      - search_tokens  (trigram)
--      - title          (trigram)
--   3. BEFORE INSERT/UPDATE 트리거 (search_vector 자동 갱신)
--
-- 테이블 자체는 JPA (ddl-auto=update) 가 생성한다.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_usi_search_vector
    ON unified_search_index USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_usi_search_tokens_trgm
    ON unified_search_index USING GIN (search_tokens gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_usi_title_trgm
    ON unified_search_index USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_usi_type_category_date
    ON unified_search_index (type, category, date DESC);

CREATE INDEX IF NOT EXISTS idx_usi_date
    ON unified_search_index (date DESC);

-- valid_until: 만료 여부 정렬/필터 대비 (컬럼은 JPA ddl-auto 가 생성)
CREATE INDEX IF NOT EXISTS idx_usi_valid_until
    ON unified_search_index (valid_until);

-- link: 동일 원문 중복 collapse 조회용(findByLink)
CREATE INDEX IF NOT EXISTS idx_usi_link
    ON unified_search_index (link);

CREATE OR REPLACE FUNCTION usi_update_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('simple', coalesce(NEW.search_tokens, ''))
        || to_tsvector('simple', coalesce(NEW.title, ''))
        || to_tsvector('simple', coalesce(NEW.content, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_usi_update_search_vector ON unified_search_index;

CREATE TRIGGER trg_usi_update_search_vector
BEFORE INSERT OR UPDATE OF search_tokens, title, content
ON unified_search_index
FOR EACH ROW EXECUTE FUNCTION usi_update_search_vector();
