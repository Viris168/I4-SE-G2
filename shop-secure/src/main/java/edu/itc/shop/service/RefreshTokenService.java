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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation. Each use hands out a successor and revokes the old token;
 * presenting a revoked token again means it was copied, so the whole family is revoked.
 * A token only rotates while its user still exists, is enabled and is not locked.
 */
@Service
public class RefreshTokenService {

    public static final Duration TTL = Duration.ofDays(7);

    private final RefreshTokenRepository repo;
    private final AuditService audit;
    private final UserDetailsService users;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo, AuditService audit, UserDetailsService users) {
        this.repo = repo;
        this.audit = audit;
        this.users = users;
    }

    public record Issued(String rawToken, RefreshToken entity) {}

    /** The successor token plus the freshly loaded user, whose current roles go into the new access token. */
    public record Rotated(String rawToken, UserDetails user) {}

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
     * committed although the reuse and the account cases end in an exception.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotated rotate(String raw) {
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
        UserDetails user = activeUser(current.getUsername());
        if (user == null) {
            repo.revokeFamily(current.getFamilyId(), Instant.now());
            throw new InvalidRefreshTokenException("account deleted, disabled or locked");
        }
        Issued next = issue(current.getUsername(), current.getFamilyId());
        current.revoke(next.entity().getId());
        audit.record(AuditType.TOKEN_REFRESHED, current.getUsername(),
                "family=" + current.getFamilyId() + " tokenId=" + current.getId() + "->" + next.entity().getId());
        return new Rotated(next.rawToken(), user);
    }

    /** Logout: revokes every token of the presented token's family. Unknown tokens are ignored. */
    @Transactional
    public void logout(String raw) {
        repo.findByTokenHash(sha256(raw)).ifPresent(t -> {
            repo.revokeFamily(t.getFamilyId(), Instant.now());
            audit.record(AuditType.LOGOUT, t.getUsername(), "family=" + t.getFamilyId());
        });
    }

    /** The user if they may still get tokens, otherwise null. */
    private UserDetails activeUser(String username) {
        try {
            UserDetails user = users.loadUserByUsername(username);
            return (user.isEnabled() && user.isAccountNonLocked()) ? user : null;
        } catch (UsernameNotFoundException e) {
            return null;
        }
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
