CREATE TABLE device (
    id UUID PRIMARY KEY,
    created_time BIGINT NOT NULL,
    tenant_id UUID NOT NULL,
    device_profile_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    label VARCHAR(255),
    CONSTRAINT device_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT device_profile_fk FOREIGN KEY (device_profile_id) REFERENCES device_profile(id),
    CONSTRAINT device_name_unq UNIQUE (tenant_id, name) -- list this tenant's devices
);
CREATE INDEX device_profile_id_idx ON device (device_profile_id);