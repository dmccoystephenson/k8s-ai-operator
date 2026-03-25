package com.stephenson.k8saioperator.metrics;

/**
 * Abstraction for operational metrics emission.
 * Use {@link CloudWatchMetricsEmitter} for AWS deployments and
 * {@link NoOpMetricsEmitter} for local development.
 */
public interface MetricsEmitter {

    void emitAllowedCommand();

    void emitBlockedCommand();

    void emitLatency(long latencyMs);
}
