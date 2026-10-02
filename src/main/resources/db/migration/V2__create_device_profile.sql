CREATE TABLE device_profile (
    id UUID PRIMARY KEY,
    created_time BIGINT NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    CONSTRAINT profile_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT profile_name_unq UNIQUE (tenant_id, name) -- list this tenant's device profiles
);