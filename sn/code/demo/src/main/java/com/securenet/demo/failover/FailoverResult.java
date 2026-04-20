package com.securenet.demo.failover;

/**
 * Result of a single service failover test.
 */
public record FailoverResult(
        String serviceName,
        boolean passed,
        String message,
        long durationMs
) {
    public static FailoverResult pass(String service, String message, long durationMs) {
        return new FailoverResult(service, true, message, durationMs);
    }

    public static FailoverResult fail(String service, String message, long durationMs) {
        return new FailoverResult(service, false, message, durationMs);
    }
}
