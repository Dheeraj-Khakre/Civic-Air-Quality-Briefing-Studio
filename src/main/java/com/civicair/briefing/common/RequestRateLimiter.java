package com.civicair.briefing.common;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestRateLimiter {
    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void check(UUID userId, String action, int limit, Duration interval) {
        Instant now = Instant.now();
        String key = userId + ":" + action;
        Window current = windows.get(key);
        if (current == null || current.startedAt().plus(interval).isBefore(now)) {
            windows.put(key, new Window(now, 1));
            if (windows.size() > 10_000) {
                windows.entrySet().removeIf(entry -> entry.getValue().startedAt().plus(interval.multipliedBy(2)).isBefore(now));
            }
            return;
        }
        if (current.count() >= limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Request limit exceeded; try again later.");
        }
        windows.put(key, new Window(current.startedAt(), current.count() + 1));
    }

    private record Window(Instant startedAt, int count) {}
}
