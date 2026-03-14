package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.metrics.NoOpMetricsEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link NoOpMetricsEmitter}.
 * Verifies that all three metric emission methods complete without error.
 */
class NoOpMetricsEmitterTest {

    private NoOpMetricsEmitter metricsEmitter;

    @BeforeEach
    void setUp() {
        metricsEmitter = new NoOpMetricsEmitter();
    }

    @Test
    void emitAllowedCommand_completesWithoutException() {
        assertDoesNotThrow(() -> metricsEmitter.emitAllowedCommand());
    }

    @Test
    void emitBlockedCommand_completesWithoutException() {
        assertDoesNotThrow(() -> metricsEmitter.emitBlockedCommand());
    }

    @Test
    void emitLatency_completesWithoutException() {
        assertDoesNotThrow(() -> metricsEmitter.emitLatency(42L));
    }
}
