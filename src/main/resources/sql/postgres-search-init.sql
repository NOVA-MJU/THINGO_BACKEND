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
--
-- 키워드 알림의 비로그인(게스트) 기기 소유자 확장 DDL 도 함께 적용한다(keyword_subscription.topic_id 와 같은 방식).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 제목 전용 tsvector 컬럼(JPA ddl-auto 가 만들지 못한 경우 대비, 멱등).
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS title_vector TSVECTOR;

-- 제목 Komoran 분해 토큰 컬럼(애플리케이션이 채움, 트리거가 title_vector 생성에 사용).
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS title_tokens TEXT;

-- 공지 의미 분류 메타데이터. 기존 검색 API 응답과 무관한 내부 인덱스 필드다.
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS topic_ids JSONB;
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS direct_topic_ids JSONB;
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS event_type VARCHAR(32);
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS audiences JSONB;
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS campuses JSONB;
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS classification_version INTEGER;
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS classification_source VARCHAR(16);
ALTER TABLE unified_search_index ADD COLUMN IF NOT EXISTS classification_confidence DOUBLE PRECISION;

-- 자동완성에서 선택한 표준 Topic 구독. null이면 기존 자유 키워드 구독이다.
ALTER TABLE keyword_subscription ADD COLUMN IF NOT EXISTS topic_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_keyword_subscription_topic
    ON keyword_subscription(topic_id) WHERE topic_id IS NOT NULL AND enabled = true;

-- 비로그인(게스트) 기기 키워드 알림.
-- 구독/알림함/FCM 토큰의 소유자를 회원(member_id) 또는 서버 발급 기기(device_id)로 확장한다.
-- ddl-auto=update 는 기존 컬럼의 NOT NULL 을 풀지 못하고 CHECK 도 만들지 않으므로 여기서 멱등 적용한다.
-- 기존 행은 모두 member_id 가 있고 device_id 는 NULL 이라 아래 CHECK 를 그대로 통과한다.
-- 유일 인덱스는 엔티티 @UniqueConstraint 와 같은 이름이라, Hibernate 가 먼저 만들었으면 건너뛴다.
-- (PostgreSQL 유일 제약은 NULL 을 서로 다른 값으로 보므로 회원 행끼리/기기 행끼리만 중복을 막는다)
CREATE TABLE IF NOT EXISTS device (
    device_id    UUID PRIMARY KEY,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

ALTER TABLE keyword_subscription ALTER COLUMN member_id DROP NOT NULL;
ALTER TABLE keyword_subscription ADD COLUMN IF NOT EXISTS device_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uk_keyword_subscription_device_keyword
    ON keyword_subscription(device_id, keyword);

ALTER TABLE notification_history ALTER COLUMN member_id DROP NOT NULL;
ALTER TABLE notification_history ADD COLUMN IF NOT EXISTS device_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_history_device_index
    ON notification_history(device_id, search_index_id);

ALTER TABLE device_token ALTER COLUMN member_id DROP NOT NULL;
ALTER TABLE device_token ADD COLUMN IF NOT EXISTS device_id UUID;
-- 기기 수신자 발송 시 게스트 토큰(member_id IS NULL) 조회용
CREATE INDEX IF NOT EXISTS idx_device_token_guest_device
    ON device_token(device_id) WHERE member_id IS NULL;

-- 소유자 규칙: 구독/알림함은 회원·기기 중 정확히 하나, 토큰은 최소 하나(로그인 토큰도 device_id 를 기록할 수 있음)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_keyword_subscription_owner') THEN
        ALTER TABLE keyword_subscription ADD CONSTRAINT ck_keyword_subscription_owner
            CHECK ((member_id IS NULL) <> (device_id IS NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_notification_history_owner') THEN
        ALTER TABLE notification_history ADD CONSTRAINT ck_notification_history_owner
            CHECK ((member_id IS NULL) <> (device_id IS NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_device_token_owner') THEN
        ALTER TABLE device_token ADD CONSTRAINT ck_device_token_owner
            CHECK (member_id IS NOT NULL OR device_id IS NOT NULL);
    END IF;
END;
$$;

ALTER TABLE unified_search_index ALTER COLUMN topic_ids SET DEFAULT '[]'::jsonb;
ALTER TABLE unified_search_index ALTER COLUMN direct_topic_ids SET DEFAULT '[]'::jsonb;
ALTER TABLE unified_search_index ALTER COLUMN audiences SET DEFAULT '[]'::jsonb;
ALTER TABLE unified_search_index ALTER COLUMN campuses SET DEFAULT '[]'::jsonb;
UPDATE unified_search_index SET topic_ids = '[]'::jsonb WHERE topic_ids IS NULL;
UPDATE unified_search_index SET direct_topic_ids = '[]'::jsonb WHERE direct_topic_ids IS NULL;
UPDATE unified_search_index SET audiences = '[]'::jsonb WHERE audiences IS NULL;
UPDATE unified_search_index SET campuses = '[]'::jsonb WHERE campuses IS NULL;

CREATE INDEX IF NOT EXISTS idx_usi_topic_ids
    ON unified_search_index USING GIN (topic_ids);

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
    -- title_vector 는 Komoran 분해 토큰(title_tokens)을 우선 사용해 복합어를 쪼갠다.
    -- 원문 title 도 함께 넣어(|| ) 정확 표기 lexeme 도 보존한다(재색인 전 title_tokens=NULL 이면 원문만).
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

-- 기존 행 backfill(트리거는 신규/변경 행만 채운다). title_vector IS NULL 가드로 멱등.
-- title_tokens 는 SQL 로 못 만든다(Komoran 필요) -> 재색인(/sync 또는 야간 reconcile) 시 채워진다.
-- 그 전까지는 원문 title 기준으로라도 title_vector 를 채워 검색이 비지 않게 한다.
UPDATE unified_search_index
   SET title_vector = to_tsvector('simple', coalesce(title, ''))
 WHERE title_vector IS NULL;
