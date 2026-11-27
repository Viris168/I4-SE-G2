package edu.itc.shop.web.dto;

/**
 * The login / refresh answer: the JWT, how to send it, how many seconds it stays valid, and the
 * opaque refresh token with its lifetime in seconds.
 */
public record TokenResponse(String token, String tokenType, long expiresIn,
                            String refreshToken, long refreshExpiresIn) {
}
