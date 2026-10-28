package com.local.noti_filter;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.function.Supplier;

/** Main-thread-only requests, offered in monotonic elapsed-time order. */
final class PendingRemovals<T> {
    private final LinkedHashMap<String, T> entries = new LinkedHashMap<>();
    private final int capacity;
    private final long maxAge;
    private final ToLongFunction<T> requestedAt;

    PendingRemovals(int capacity, long maxAge, ToLongFunction<T> requestedAt) {
        this.capacity = capacity;
        this.maxAge = maxAge;
        this.requestedAt = requestedAt;
    }

    boolean offer(String key, Supplier<T> factory, long now) {
        Iterator<Map.Entry<String, T>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            T oldest = iterator.next().getValue();
            if (now - requestedAt.applyAsLong(oldest) <= maxAge) break;
            iterator.remove();
        }
        if (entries.size() >= capacity && !entries.containsKey(key)) return false;
        T value = factory.get();
        // A refreshed key must move to the tail, preserving timestamp ordering.
        entries.remove(key);
        entries.put(key, value);
        return true;
    }

    T remove(String key) { return entries.remove(key); }
    void clear() { entries.clear(); }
}
