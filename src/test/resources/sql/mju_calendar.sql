CREATE TABLE IF NOT EXISTS mju_calendar (
    id          BIGSERIAL PRIMARY KEY,
    year        INTEGER,
    start_date  DATE,
    end_date    DATE,
    description VARCHAR(1000)
);
