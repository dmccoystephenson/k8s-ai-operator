package com.stephenson.k8saioperator.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * No-op metrics emitter used when the {@code local} Spring profile is active.
 * Metrics are logged at DEBUG level instead of being sent to CloudWatch,
 * so no AWS credentials are required in a local development environment.
 */
@Slf4j
@Component
@Profile("local")
public class NoOpMetricsEmitter implements MetricsEmitter {

    @Override
    public void emitAllowedCommand() {
        log.debug("(local) metric: AllowedCommands +1");
    }

    @Override
    public void emitBlockedCommand() {
        log.debug("(local) metric: BlockedCommands +1");
    }

    @Override
    public void emitLatency(long latencyMs) {
        log.debug("(local) metric: ExecutionLatencyMs={}", latencyMs);
    }
}
