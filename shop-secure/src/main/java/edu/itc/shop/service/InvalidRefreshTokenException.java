package edu.itc.shop.service;

/** Unknown, expired, revoked or reused refresh token. The reason is for logs only, never for the client. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String reason) {
        super(reason);
    }
}
