CREATE TABLE device_credentials (
    id UUID PRIMARY KEY,
    created_time BIGINT NOT NULL,
    device_id UUID NOT NULL,
    access_token VARCHAR(64) NOT NULL,
    CONSTRAINT credentials_device_fk FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE,
    CONSTRAINT credentials_device_unq UNIQUE (device_id),
    -- one credentials row per device
    CONSTRAINT credentials_token_unq UNIQUE (access_token) -- token -> device lookup on every message
);
-- Devices created before this migration also need credentials.
INSERT INTO device_credentials (id, created_time, device_id, access_token)
SELECT gen_random_uuid(),
    (
        EXTRACT(
            EPOCH
            FROM now()
        ) * 1000
    )::BIGINT,
    d.id,
    replace(gen_random_uuid()::TEXT, '-', '')
FROM device d;