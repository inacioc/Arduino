package com.example.retrydemo.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake flaky upstream living in the same application, so the demo is self-contained.
 *
 * GET /upstream/stock/{id}?failures=N answers 503 N times in a row, then 200, then the cycle
 * starts again. With the default of 3 attempts: failures=2 recovers, failures=5 does not.
 */
@RestController
@RequestMapping("/upstream")
class UpstreamSimulator {

    private final ConcurrentMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    @GetMapping("/stock/{id}")
    ResponseEntity<String> stock(@PathVariable String id, @RequestParam(defaultValue = "2") int failures) {
        int n = calls.computeIfAbsent(id + ":" + failures, key -> new AtomicInteger()).incrementAndGet();
        if ((n - 1) % (failures + 1) < failures) {
            return ResponseEntity.status(503).body("temporarily unavailable (call " + n + ")");
        }
        return ResponseEntity.ok("stock-of-" + id);
    }
}
