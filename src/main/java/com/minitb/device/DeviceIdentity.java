package com.minitb.device;

import java.util.UUID;

// What a device's access token resolves to. Both fields can never change for a device,
// so the only thing that makes a cached entry stale is the token itself changing.
public record DeviceIdentity(UUID deviceId, UUID tenantId) {
}
