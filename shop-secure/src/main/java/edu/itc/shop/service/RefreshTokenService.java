package edu.itc.shop.service;

import edu.itc.shop.domain.AuditType;
import edu.itc.shop.domain.RefreshToken;
import edu.itc.shop.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation. Each use hands out a successor and revokes the old token;
 * presenting a revoked token again means it was copied, so the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    public static final Duration TTL = Duration.ofDays(7);

    private final RefreshTokenRepository repo;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public record Issued(String rawToken, RefreshToken entity) {}

    /** A new token in {@code family}; pass a fresh UUID at login. */
    @Transactional
    public Issued issue(String username, UUID family) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken t = new RefreshToken(sha256(raw), username, family, Instant.now().plus(TTL));
        return new Issued(raw, repo.save(t));
    }

    /**
     * Exchanges a valid token for its successor. noRollbackFor keeps the family revocation
     * committed although the reuse case ends in an exception.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Issued rotate(String raw) {
        RefreshToken current = repo.findByTokenHash(sha256(raw))
                .orElseThrow(() -> new InvalidRefreshTokenException("unknown"));
        if (current.isRevoked()) {
            int revoked = repo.revokeFamily(current.getFamilyId(), Instant.now());
            audit.record(AuditType.REFRESH_REUSE_DETECTED, current.getUsername(),
                    "family=" + current.getFamilyId() + " tokenId=" + current.getId() + " revoked=" + revoked);
            throw new InvalidRefreshTokenException("reuse");
        }
        if (current.isExpired()) {
            throw new InvalidRefreshTokenException("expired");
        }
        Issued next = issue(current.getUsername(), current.getFamilyId());
        current.revoke(next.entity().getId());
        audit.record(AuditType.TOKEN_REFRESHED, current.getUsername(),
                "family=" + current.getFamilyId() + " tokenId=" + current.getId() + "->" + next.entity().getId());
        return next;
    }

    /** Logout: revokes every token of the presented token's family. Unknown tokens are ignored. */
    @Transactional
    public void logout(String raw) {
        repo.findByTokenHash(sha256(raw)).ifPresent(t -> {
            repo.revokeFamily(t.getFamilyId(), Instant.now());
            audit.record(AuditType.LOGOUT, t.getUsername(), "family=" + t.getFamilyId());
        });
    }

    @Transactional
    public void revokeFamily(UUID family) {
        repo.revokeFamily(family, Instant.now());
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
