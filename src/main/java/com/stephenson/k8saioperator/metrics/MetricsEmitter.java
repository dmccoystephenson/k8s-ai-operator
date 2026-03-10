package com.stephenson.k8saioperator.metrics;

/**
 * Strategy interface for emitting operational metrics.
 *
 * The production implementation publishes to Amazon CloudWatch; the local
 * implementation logs to the console so the application can run without
 * any AWS credentials.
 */
public interface MetricsEmitter {

    /** Records a command that passed the allowlist check. */
    void emitAllowedCommand();

    /** Records a command that was rejected by the verb guard. */
    void emitBlockedCommand();

    /** Records the end-to-end latency for a single request. */
    void emitLatency(long latencyMs);
}
