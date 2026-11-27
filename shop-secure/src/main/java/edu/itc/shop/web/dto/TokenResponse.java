package edu.itc.shop.web.dto;

/** The login answer: the JWT, how to send it, and how many seconds it stays valid. */
public record TokenResponse(String token, String tokenType, long expiresIn) {
}
