package edu.itc.shop.domain;

public enum AuditType {
    LOGIN_OK,
    LOGIN_FAILED,
    LOCKED,
    TOKEN_REFRESHED,
    REFRESH_REUSE_DETECTED,
    LOGOUT,
    ACCESS_DENIED,
    ROLE_CHANGED
}
