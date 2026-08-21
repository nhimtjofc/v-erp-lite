package base.l0.system.api;

import java.util.List;

/**
 * One page of a result set, and how to ask for the next.
 *
 * <p>An explicit envelope rather than a bare array, deliberately. The cheap way to add paging is to
 * accept {@code limit} and keep returning a list, so nothing breaks at compile time and no client
 * has to change — and a caller that used to receive every row silently starts receiving the first
 * fifty. In a ledger a truncated answer that looks complete is worse than an error: it is the same
 * failure as a provider registry defaulting an unknown id to a mock, which this service refuses on
 * principle. Changing the shape makes callers fail loudly, once.
 *
 * <p>{@code nextCursor} is <b>opaque</b>. It is a key from the last row of this page and callers
 * must pass it back unmodified rather than parse it or construct one; what it is derived from is
 * free to change.
 *
 * @param hasMore whether another page exists. Carried explicitly rather than inferred from
 *                {@code items.size() == limit}, which is wrong exactly when the last page happens
 *                to be full — the case nobody tests and everybody hits eventually.
 */
public record Page<T>(List<T> items, String nextCursor, boolean hasMore, int limit) {

    public static <T> Page<T> of(List<T> items, String nextCursor, boolean hasMore, int limit) {
        return new Page<>(items, hasMore ? nextCursor : null, hasMore, limit);
    }
}

