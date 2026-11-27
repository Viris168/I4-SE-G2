package edu.itc.shop.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter for POST /api/auth/login: at most 5 attempts per minute per client IP and
 * per username, whichever runs out first. Every attempt counts, successful or not.
 * In-memory, so the limit is per instance; several instances would need a shared store (e.g. Redis).
 */
@Component
public class LoginRateLimiter {

    static final int LIMIT = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int PRUNE_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Counts one attempt; throws {@link RateLimitedException} once either bucket is over the limit. */
    public void check(String ip, String username) {
        Instant now = Instant.now();
        if (windows.size() > PRUNE_THRESHOLD) {   // a spray of random usernames must not grow the map forever
            windows.values().removeIf(w -> expired(w, now));
        }
        long retryAfter = Math.max(hit("ip:" + ip, now),
                                   hit("user:" + username.toLowerCase(Locale.ROOT), now));
        if (retryAfter > 0) {
            throw new RateLimitedException(retryAfter);
        }
    }

    /** Forgets every counter. Used by the integration tests, which all log in from 127.0.0.1. */
    public void reset() {
        windows.clear();
    }

    /** Seconds the caller has to wait, or 0 if this attempt is within the limit. */
    private long hit(String key, Instant now) {
        Window w = windows.compute(key, (k, old) -> (old == null || expired(old, now))
                ? new Window(now, 1)
                : new Window(old.start(), old.count() + 1));
        if (w.count() <= LIMIT) {
            return 0;
        }
        return Math.max(1, Duration.between(now, w.start().plus(WINDOW)).toSeconds());
    }

    private static boolean expired(Window w, Instant now) {
        return !now.isBefore(w.start().plus(WINDOW));
    }
}
