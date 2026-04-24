package com.securenet.testsupport;

import java.time.Duration;

public final class Eventually {

    private Eventually() {
    }

    public static void await(String description,
                             Duration timeout,
                             Duration pollInterval,
                             CheckedBooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable lastError = null;

        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (Throwable t) {
                lastError = t;
            }
            Thread.sleep(pollInterval.toMillis());
        }

        AssertionError error = new AssertionError("Timed out waiting for " + description);
        if (lastError != null) {
            error.initCause(lastError);
        }
        throw error;
    }

    @FunctionalInterface
    public interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
