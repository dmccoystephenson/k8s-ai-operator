package com.stephenson.k8saioperator.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/**
 * Emits operational metrics to Amazon CloudWatch.
 *
 * Metrics published:
 * - AllowedCommands  (Count)
 * - BlockedCommands  (Count)
 * - ExecutionLatencyMs (Milliseconds)
 */
@Slf4j
@Component
public class CloudWatchMetricsEmitter {

    private final CloudWatchClient cloudWatchClient;
    private final String namespace;

    public CloudWatchMetricsEmitter(
            CloudWatchClient cloudWatchClient,
            @Value("${aws.cloudwatch.namespace}") String namespace) {
        this.cloudWatchClient = cloudWatchClient;
        this.namespace = namespace;
    }

    public void emitAllowedCommand() {
        emit("AllowedCommands", 1.0, StandardUnit.COUNT);
    }

    public void emitBlockedCommand() {
        emit("BlockedCommands", 1.0, StandardUnit.COUNT);
    }

    public void emitLatency(long latencyMs) {
        emit("ExecutionLatencyMs", (double) latencyMs, StandardUnit.MILLISECONDS);
    }

    private void emit(String metricName, double value, StandardUnit unit) {
        try {
            MetricDatum datum = MetricDatum.builder()
                    .metricName(metricName)
                    .value(value)
                    .unit(unit)
                    .dimensions(Dimension.builder()
                            .name("Service")
                            .value("k8s-ai-operator")
                            .build())
                    .build();

            cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datum)
                    .build());

            log.debug("CloudWatch metric emitted: {}={} {}", metricName, value, unit);
        } catch (Exception e) {
            log.warn("Failed to emit CloudWatch metric {}: {}", metricName, e.getMessage());
        }
    }
}

