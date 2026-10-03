package com.minitb.common;

import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.minitb.device.DeviceIdentity;
import com.minitb.deviceprofile.DeviceProfile;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

	public static final String DEVICE_CREDENTIALS = "deviceCredentials";
	public static final String DEVICE_PROFILES = "deviceProfiles";

	@Bean
	public RedisCacheManagerBuilderCustomizer redisCacheCustomizer() {
		return builder -> builder
				.withCacheConfiguration(DEVICE_CREDENTIALS, cacheConfig(DeviceIdentity.class, Duration.ofMinutes(5)))
				.withCacheConfiguration(DEVICE_PROFILES, cacheConfig(DeviceProfile.class, Duration.ofMinutes(30)));
	}

	private static RedisCacheConfiguration cacheConfig(Class<?> valueType, Duration ttl) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(ttl)
				.disableCachingNullValues()
				// Store values as JSON of one known type: readable in redis-cli, and no polymorphic type info.
				.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(valueType)));
	}

	// If Redis is down, log and carry on as if it were a cache miss: requests fall back to Postgres
	// instead of failing.
	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler();
	}

}
