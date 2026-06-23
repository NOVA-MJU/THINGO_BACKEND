-- =====================================================================
-- PostgreSQL 통합 검색 테이블 DDL
--
-- 실행 시점: 앱 최초 기동 전 1회 (RDS psql)
-- Hibernate ddl-auto=update 환경에서 tsvector/GIN/trigger 자동 생성이
-- 불가능하므로 수동 실행한다.
-- =====================================================================

-- 1. trigram 확장 (유사도/오타 매칭)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. 통합 검색 테이블
CREATE TABLE IF NOT EXISTS unified_search_index (
    id              VARCHAR(64)              PRIMARY KEY,
    original_id     VARCHAR(64)              NOT NULL,
    type            VARCHAR(32)              NOT NULL,
    category        VARCHAR(64),
    title           TEXT                     NOT NULL,
    content         TEXT,
    author_name     VARCHAR(64),
    link            TEXT,
    image_url       TEXT,
    like_count      INTEGER,
    comment_count   INTEGER,
    popularity      DOUBLE PRECISION         DEFAULT 0.0,
    active          BOOLEAN                  NOT NULL DEFAULT TRUE,
    date            TIMESTAMP WITH TIME ZONE NOT NULL,
    indexed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    search_tokens   TEXT,
    search_vector   TSVECTOR,
    title_vector    TSVECTOR
);

-- 기존 배포 테이블에 컬럼 추가(신규 설치는 위 CREATE 로 이미 존재).
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS title_vector TSVECTOR;

-- 3. 인덱스
CREATE INDEX IF NOT EXISTS idx_usi_search_vector
    ON unified_search_index USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_usi_search_tokens_trgm
    ON unified_search_index USING GIN (search_tokens gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_usi_title_trgm
    ON unified_search_index USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_usi_type_category_date
    ON unified_search_index (type, category, date DESC);

CREATE INDEX IF NOT EXISTS idx_usi_date_desc
    ON unified_search_index (date DESC);

CREATE INDEX IF NOT EXISTS idx_usi_original_id
    ON unified_search_index (original_id);

-- 4. search_vector 자동 갱신 트리거
CREATE OR REPLACE FUNCTION usi_update_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('simple', coalesce(NEW.search_tokens, ''))
        || to_tsvector('simple', coalesce(NEW.title, ''))
        || to_tsvector('simple', coalesce(NEW.content, ''));
    NEW.title_vector := to_tsvector('simple', coalesce(NEW.title, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_usi_update_search_vector ON unified_search_index;

CREATE TRIGGER trg_usi_update_search_vector
BEFORE INSERT OR UPDATE OF search_tokens, title, content
ON unified_search_index
FOR EACH ROW EXECUTE FUNCTION usi_update_search_vector();

-- 기존 행 backfill(트리거는 신규/변경 행만 채운다). 1회 실행.
UPDATE unified_search_index
   SET title_vector = to_tsvector('simple', coalesce(title, ''))
 WHERE title_vector IS NULL;
