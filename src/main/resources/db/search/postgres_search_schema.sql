-- PostgreSQL 15+ search schema for replacing Elasticsearch unified index.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- Optional Korean tokenizer extension (choose one by environment)
-- CREATE EXTENSION IF NOT EXISTS pg_bigm;

CREATE TABLE IF NOT EXISTS thingo_search_document (
    id                 varchar(120) PRIMARY KEY,
    original_id        varchar(64) NOT NULL,
    type               varchar(40) NOT NULL,
    title              text,
    title_normalized   text,
    content            text,
    content_normalized text,
    category           varchar(120),
    category_normalized text,
    search_tokens      text,
    link               text,
    image_url          text,
    created_at         timestamptz,
    updated_at         timestamptz,
    active             boolean NOT NULL DEFAULT true,
    popularity         double precision,
    like_count         integer,
    comment_count      integer,
    author_name        varchar(120),
    weighted_tsv       tsvector
);

CREATE INDEX IF NOT EXISTS idx_thingo_search_type_category
    ON thingo_search_document(type, category);

CREATE INDEX IF NOT EXISTS idx_thingo_search_created_at
    ON thingo_search_document(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_thingo_search_weighted_tsv
    ON thingo_search_document USING gin(weighted_tsv);

CREATE INDEX IF NOT EXISTS idx_thingo_search_title_prefix
    ON thingo_search_document (lower(title) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_thingo_search_title_trgm
    ON thingo_search_document USING gin (title gin_trgm_ops);

CREATE OR REPLACE FUNCTION update_thingo_search_tsvector()
RETURNS trigger AS $$
BEGIN
    NEW.weighted_tsv :=
          setweight(to_tsvector('simple', COALESCE(NEW.title, '')), 'A')
       || setweight(to_tsvector('simple', COALESCE(NEW.search_tokens, '')), 'A')
       || setweight(to_tsvector('simple', COALESCE(NEW.category, '')), 'B')
       || setweight(to_tsvector('simple', COALESCE(NEW.content, '')), 'D');

    NEW.updated_at := NOW();
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_thingo_search_tsvector ON thingo_search_document;

CREATE TRIGGER trg_thingo_search_tsvector
BEFORE INSERT OR UPDATE OF title, search_tokens, category, content
ON thingo_search_document
FOR EACH ROW
EXECUTE FUNCTION update_thingo_search_tsvector();

CREATE TABLE IF NOT EXISTS search_query_log (
    id          bigserial PRIMARY KEY,
    keyword     varchar(120) NOT NULL,
    user_id     bigint,
    search_type varchar(40),
    category    varchar(120),
    searched_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_search_query_log_keyword_time
    ON search_query_log(keyword, searched_at DESC);

CREATE INDEX IF NOT EXISTS idx_search_query_log_user_time
    ON search_query_log(user_id, searched_at DESC);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_realtime_keywords AS
SELECT keyword,
       COUNT(*) AS total_count,
       MAX(searched_at) AS last_searched_at
FROM search_query_log
WHERE searched_at >= NOW() - INTERVAL '3 days'
GROUP BY keyword;

CREATE INDEX IF NOT EXISTS idx_mv_realtime_keywords_rank
    ON mv_realtime_keywords(total_count DESC, last_searched_at DESC);
