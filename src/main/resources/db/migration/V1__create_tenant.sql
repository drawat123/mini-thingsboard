CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    created_time BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    CONSTRAINT tenant_title_unq UNIQUE (title) -- table_what_type pattern
);