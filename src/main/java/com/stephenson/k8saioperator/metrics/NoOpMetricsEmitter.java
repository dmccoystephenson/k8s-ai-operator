package com.stephenson.k8saioperator.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local (no-AWS) implementation of {@link MetricsEmitter}.
 *
 * Writes metrics to the application log instead of Amazon CloudWatch.
 * Activate this bean by running with the {@code local} Spring profile.
 */
@Slf4j
@Component
@Profile("local")
public class NoOpMetricsEmitter implements MetricsEmitter {

    @Override
    public void emitAllowedCommand() {
        log.info("[LOCAL METRICS] AllowedCommands +1");
    }

    @Override
    public void emitBlockedCommand() {
        log.info("[LOCAL METRICS] BlockedCommands +1");
    }

    @Override
    public void emitLatency(long latencyMs) {
        log.info("[LOCAL METRICS] ExecutionLatencyMs={}", latencyMs);
    }
}
