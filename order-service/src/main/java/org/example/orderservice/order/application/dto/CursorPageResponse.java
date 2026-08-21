package org.example.orderservice.order.application.dto;

import java.util.List;

/**
 * A keyset (cursor) page.
 *
 * Deliberately NOT a Spring {@code Page}: a Page carries totalElements/totalPages, and
 * computing those requires a COUNT over the whole result set — the exact full scan keyset
 * pagination exists to avoid. A cursor page knows only "here are N rows, and here is where
 * to resume".
 *
 * `nextCursor` is opaque to the client: hand it back verbatim on the next request.
 * Null means there is nothing more to fetch.
 */
public record CursorPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {
    public static <T> CursorPageResponse<T> of(List<T> items, String nextCursor) {
        return new CursorPageResponse<>(items, nextCursor, nextCursor != null);
    }
}
