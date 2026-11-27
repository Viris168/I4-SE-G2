package edu.itc.shop.web;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.itc.shop.config.JwtConfig;
import edu.itc.shop.config.SecurityProps;
import edu.itc.shop.security.LoginRateLimiter;
import edu.itc.shop.service.InvalidRefreshTokenException;
import edu.itc.shop.service.RefreshTokenService;
import edu.itc.shop.web.dto.LoginRequest;
import edu.itc.shop.web.dto.RefreshRequest;
import edu.itc.shop.web.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Exchanges a username and password for a short-lived HS256 access token plus an opaque refresh
 * token; rotates refresh tokens and revokes them on logout.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtEncoder encoder;
    private final SecurityProps props;
    private final RefreshTokenService refreshTokens;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthenticationManager authManager, JwtEncoder encoder, SecurityProps props,
                          RefreshTokenService refreshTokens, LoginRateLimiter rateLimiter) {
        this.authManager = authManager;
        this.encoder = encoder;
        this.props = props;
        this.refreshTokens = refreshTokens;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest req, HttpServletRequest http) {
        rateLimiter.check(http.getRemoteAddr(), req.username());
        Authentication auth = authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password()));
        var refresh = refreshTokens.issue(auth.getName(), UUID.randomUUID());
        return tokens(auth.getName(), auth.getAuthorities(), refresh.rawToken());
    }

    /** Rotates the refresh token; the new access token carries the user's current roles. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshRequest req) {
        var next = refreshTokens.rotate(req.refreshToken());
        return tokens(next.user().getUsername(), next.user().getAuthorities(), next.rawToken());
    }

    /** Revokes the whole family of the presented token. Always 204, so it says nothing about the token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshRequest req) {
        refreshTokens.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private TokenResponse tokens(String username, Collection<? extends GrantedAuthority> authorities,
                                 String refreshToken) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(JwtConfig.ROLES_CLAIM, roles(authorities))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", props.accessTokenTtl().toSeconds(),
                refreshToken, RefreshTokenService.TTL.toSeconds());
    }

    /**
     * Only the ROLE_ authorities. Spring Security 7 also adds FACTOR_PASSWORD after a password login;
     * that describes how this login happened, not what the user may do, so it stays out of the token.
     */
    private static List<String> roles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .toList();
    }

    /** Unknown user, wrong password, disabled or locked account: all get the same answer, so nothing leaks. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail badCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Bad credentials");
    }

    /** Unknown, expired, rotated or reused: one generic answer. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail invalidRefreshToken() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Refresh token invalid");
    }
}
