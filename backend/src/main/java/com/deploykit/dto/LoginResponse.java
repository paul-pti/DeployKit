package com.deploykit.dto;

/**
 * @param accessToken the JWT to send as {@code Authorization: Bearer <token>}
 * @param expiresIn   lifetime of the token in seconds
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
