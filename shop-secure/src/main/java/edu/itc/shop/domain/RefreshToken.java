package edu.itc.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One opaque refresh token. Only the SHA-256 hash is stored, so a leaked table cannot be replayed.
 * All tokens issued from one login share a {@code familyId}; rotation links each token to its successor.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    /** Id of the token this one was rotated into; null if revoked any other way. */
    private Long replacedBy;

    protected RefreshToken() { }   // for JPA

    public RefreshToken(String tokenHash, String username, UUID familyId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.username = username;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired() { return !Instant.now().isBefore(expiresAt); }

    public void revoke(Long replacedBy) {
        this.revokedAt = Instant.now();
        this.replacedBy = replacedBy;
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public String getUsername() { return username; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Long getReplacedBy() { return replacedBy; }

    @Override
    public String toString() {   // never the hash
        return "RefreshToken{id=" + id + ", username=" + username + ", familyId=" + familyId
                + ", expiresAt=" + expiresAt + ", revokedAt=" + revokedAt + ", replacedBy=" + replacedBy + '}';
    }
}
