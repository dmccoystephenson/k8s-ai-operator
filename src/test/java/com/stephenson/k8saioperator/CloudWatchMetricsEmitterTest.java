package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.metrics.CloudWatchMetricsEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates that CloudWatchMetricsEmitter publishes one datum per call to the
 * configured namespace with the expected metric name, value, unit and Service
 * dimension, and never propagates a CloudWatch failure to the caller.
 */
@ExtendWith(MockitoExtension.class)
class CloudWatchMetricsEmitterTest {

    private static final String NAMESPACE = "K8sAiOperator-test";

    @Mock
    private CloudWatchClient cloudWatchClient;

    private CloudWatchMetricsEmitter metricsEmitter;

    @BeforeEach
    void setUp() {
        metricsEmitter = new CloudWatchMetricsEmitter(cloudWatchClient, NAMESPACE);
    }

    private MetricDatum captureSingleDatum() {
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());
        PutMetricDataRequest request = captor.getValue();
        assertEquals(NAMESPACE, request.namespace());
        assertEquals(1, request.metricData().size());
        return request.metricData().get(0);
    }

    private static void assertServiceDimension(MetricDatum datum) {
        assertEquals(
                List.of(Dimension.builder().name("Service").value("k8s-ai-operator").build()),
                datum.dimensions());
    }

    // --- Counters ---

    @Test
    void emitAllowedCommand_publishesAllowedCommandsCountOfOne() {
        metricsEmitter.emitAllowedCommand();

        MetricDatum datum = captureSingleDatum();
        assertEquals("AllowedCommands", datum.metricName());
        assertEquals(1.0, datum.value());
        assertEquals(StandardUnit.COUNT, datum.unit());
        assertServiceDimension(datum);
    }

    @Test
    void emitBlockedCommand_publishesBlockedCommandsCountOfOne() {
        metricsEmitter.emitBlockedCommand();

        MetricDatum datum = captureSingleDatum();
        assertEquals("BlockedCommands", datum.metricName());
        assertEquals(1.0, datum.value());
        assertEquals(StandardUnit.COUNT, datum.unit());
        assertServiceDimension(datum);
    }

    // --- Latency ---

    @Test
    void emitLatency_publishesExecutionLatencyInMilliseconds() {
        metricsEmitter.emitLatency(1234L);

        MetricDatum datum = captureSingleDatum();
        assertEquals("ExecutionLatencyMs", datum.metricName());
        assertEquals(1234.0, datum.value());
        assertEquals(StandardUnit.MILLISECONDS, datum.unit());
        assertServiceDimension(datum);
    }

    @Test
    void emitLatency_withZero_publishesZeroValue() {
        metricsEmitter.emitLatency(0L);

        assertEquals(0.0, captureSingleDatum().value());
    }

    // --- Failure handling ---

    @Test
    void emitAllowedCommand_shouldNotThrowWhenCloudWatchFails() {
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(new RuntimeException("CloudWatch unavailable"));

        assertDoesNotThrow(() -> metricsEmitter.emitAllowedCommand());
    }

    @Test
    void emitBlockedCommand_shouldNotThrowWhenCloudWatchFails() {
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(new RuntimeException("CloudWatch unavailable"));

        assertDoesNotThrow(() -> metricsEmitter.emitBlockedCommand());
    }

    @Test
    void emitLatency_shouldNotThrowWhenCloudWatchFails() {
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(new RuntimeException("CloudWatch unavailable"));

        assertDoesNotThrow(() -> metricsEmitter.emitLatency(5L));
    }
}
