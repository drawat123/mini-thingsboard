package com.minitb.device;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.minitb.common.CacheConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DeviceCredentialsCacheEvictor {

	private final CacheManager cacheManager;

	public DeviceCredentialsCacheEvictor(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	// Runs only AFTER the transaction that published the event commits. Evicting earlier would let another
	// instance re-read the old token from Postgres and cache it again before the new one is committed.
	@TransactionalEventListener
	public void onCredentialsChanged(DeviceCredentialsChangedEvent event) {
		try {
			Cache cache = cacheManager.getCache(CacheConfig.DEVICE_CREDENTIALS);
			if (cache != null) {
				cache.evict(event.oldAccessToken());
			}
		} catch (RuntimeException e) {
			// The DB change is already committed; the stale entry expires with its TTL.
			log.warn("Failed to evict device credentials from cache", e);
		}
	}
}
