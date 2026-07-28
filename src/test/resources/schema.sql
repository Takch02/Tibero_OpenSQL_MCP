CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document (
    id        BIGSERIAL PRIMARY KEY,
    title     VARCHAR(255),
    content   TEXT,
    embedding vector(3)
);
