-- Test schema for unified_search_index (PG 16 + pg_trgm)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

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
    valid_until     TIMESTAMP WITH TIME ZONE,
    indexed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    search_tokens   TEXT,
    title_tokens    TEXT,
    search_vector   TSVECTOR,
    title_vector    TSVECTOR
);

CREATE INDEX IF NOT EXISTS idx_usi_search_vector
    ON unified_search_index USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_usi_search_tokens_trgm
    ON unified_search_index USING GIN (search_tokens gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_usi_title_trgm
    ON unified_search_index USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_usi_link
    ON unified_search_index (link);

CREATE OR REPLACE FUNCTION usi_update_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('simple', coalesce(NEW.search_tokens, ''))
        || to_tsvector('simple', coalesce(NEW.title, ''))
        || to_tsvector('simple', coalesce(NEW.content, ''));
    NEW.title_vector :=
        to_tsvector('simple', coalesce(NEW.title_tokens, ''))
        || to_tsvector('simple', coalesce(NEW.title, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_usi_update_search_vector ON unified_search_index;

CREATE TRIGGER trg_usi_update_search_vector
BEFORE INSERT OR UPDATE OF search_tokens, title, content, title_tokens
ON unified_search_index
FOR EACH ROW EXECUTE FUNCTION usi_update_search_vector();
