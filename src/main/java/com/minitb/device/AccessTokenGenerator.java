package com.minitb.device;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class AccessTokenGenerator {

	private static final int TOKEN_BYTES = 15; // 120 bits of randomness -> 20 URL-safe characters

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
