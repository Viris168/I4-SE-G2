package edu.itc.shop.api.idempotency;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.core.Response;

/**
 * First response per Idempotency-Key, kept with a SHA-256 of the request body. Bound as a singleton;
 * {@link ConcurrentHashMap#computeIfAbsent} runs the action at most once per key, so two concurrent
 * retries create one order (the second waits and then sees the stored response). When the action
 * throws (404, 409, ...) nothing is stored and the client may retry with the same key.
 */
public class IdempotencyStore {

    /** The response to replay. */
    public record Stored(String bodyHash, Response.StatusType status, URI location, Object body) { }

    /** Result of {@link #once}: the stored response and whether this call replayed it. */
    public record Outcome(Stored stored, boolean replayed) { }

    private static final Jsonb JSONB = JsonbBuilder.create();      // thread-safe, reused

    private final ConcurrentMap<String, Stored> responses = new ConcurrentHashMap<>();

    public Outcome once(String key, String bodyHash, Supplier<Stored> action) {
        boolean[] created = { false };
        Stored stored = responses.computeIfAbsent(key, k -> {
            created[0] = true;
            return action.get();
        });
        return new Outcome(stored, !created[0]);
    }

    /** SHA-256 (hex) of the canonical JSON form of a request body. */
    public static String hash(Object body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(JSONB.toJson(body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every JVM", e);
        }
    }
}
