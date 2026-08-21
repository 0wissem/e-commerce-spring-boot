package org.example.orderservice.order.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * The keyset cursor: the (createdAt, id) of the last row a client received.
 *
 * Base64-encoded on the wire so it reads as an opaque token rather than an invitation to
 * hand-craft one. This is encoding, NOT security — anyone can decode it. It carries no secret;
 * the worst a tampered cursor can do is resume from a different position in that customer's
 * own history, which the customerId filter still constrains.
 */
public record OrderHistoryCursor(Instant createdAt, String id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @return null for a null/blank cursor, meaning "first page". */
    public static OrderHistoryCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separatorAt = raw.indexOf(SEPARATOR);
            if (separatorAt < 0) throw new IllegalArgumentException("missing separator");
            return new OrderHistoryCursor(
                    Instant.parse(raw.substring(0, separatorAt)),
                    raw.substring(separatorAt + 1)
            );
        } catch (RuntimeException e) {
            // A malformed cursor is the caller's mistake, so it must surface as a 400.
            // GlobalExceptionHandler already maps IllegalArgumentException → 400; without
            // this the catch-all would dress it up as a 500 (the 2026-07-13 lesson).
            throw new IllegalArgumentException("Invalid cursor");
        }
    }
}
