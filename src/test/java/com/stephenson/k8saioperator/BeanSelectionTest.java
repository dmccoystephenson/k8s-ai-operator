package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.config.AwsConfig;
import com.stephenson.k8saioperator.metrics.CloudWatchMetricsEmitter;
import com.stephenson.k8saioperator.metrics.MetricsEmitter;
import com.stephenson.k8saioperator.metrics.NoOpMetricsEmitter;
import com.stephenson.k8saioperator.service.AnthropicCommandParser;
import com.stephenson.k8saioperator.service.AuditService;
import com.stephenson.k8saioperator.service.BedrockCommandParser;
import com.stephenson.k8saioperator.service.CommandParser;
import com.stephenson.k8saioperator.service.DynamoDbAuditService;
import com.stephenson.k8saioperator.service.PostgresAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies which implementation of each pluggable collaborator is registered
 * for a given {@code llm.provider} value and Spring profile: the Bedrock parser
 * and client by default, the Anthropic parser (and no Bedrock client) for
 * {@code anthropic}, no parser at all for an unrecognised provider, DynamoDB
 * audit and CloudWatch metrics outside the {@code local} profile, and
 * PostgreSQL audit and no-op metrics (with no DynamoDB or CloudWatch client)
 * inside it.
 *
 * <p>Every bean is made lazy, so only bean definitions are inspected: no AWS
 * SDK client, HTTP client or repository is ever constructed.
 */
class BeanSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.addBeanFactoryPostProcessor(
                    new LazyInitializationBeanFactoryPostProcessor()))
            .withUserConfiguration(
                    AwsConfig.class,
                    BedrockCommandParser.class,
                    AnthropicCommandParser.class,
                    DynamoDbAuditService.class,
                    PostgresAuditService.class,
                    CloudWatchMetricsEmitter.class,
                    NoOpMetricsEmitter.class);

    /** Counts definitions of the given type without instantiating any bean. */
    private static int definitions(AssertableApplicationContext context, Class<?> type) {
        return context.getBeanNamesForType(type, true, false).length;
    }

    // --- llm.provider ---

    @Test
    void missingProvider_registersBedrockParserAndClient() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, definitions(context, CommandParser.class));
            assertEquals(1, definitions(context, BedrockCommandParser.class));
            assertEquals(1, definitions(context, BedrockRuntimeClient.class));
        });
    }

    @Test
    void bedrockProvider_registersBedrockParserAndClient() {
        runner.withPropertyValues("llm.provider=bedrock").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, definitions(context, CommandParser.class));
            assertEquals(1, definitions(context, BedrockCommandParser.class));
            assertEquals(1, definitions(context, BedrockRuntimeClient.class));
        });
    }

    @Test
    void anthropicProvider_registersAnthropicParserAndNoBedrockClient() {
        runner.withPropertyValues("llm.provider=anthropic").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, definitions(context, CommandParser.class));
            assertEquals(1, definitions(context, AnthropicCommandParser.class));
            assertEquals(0, definitions(context, BedrockRuntimeClient.class));
        });
    }

    @Test
    void unrecognisedProvider_registersNoParser() {
        runner.withPropertyValues("llm.provider=sentinel-provider").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(0, definitions(context, CommandParser.class));
            assertEquals(0, definitions(context, BedrockRuntimeClient.class));
        });
    }

    // --- Spring profile ---

    @Test
    void defaultProfile_registersDynamoDbAuditAndCloudWatchMetrics() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, definitions(context, AuditService.class));
            assertEquals(1, definitions(context, DynamoDbAuditService.class));
            assertEquals(1, definitions(context, DynamoDbClient.class));
            assertEquals(1, definitions(context, MetricsEmitter.class));
            assertEquals(1, definitions(context, CloudWatchMetricsEmitter.class));
            assertEquals(1, definitions(context, CloudWatchClient.class));
        });
    }

    @Test
    void localProfile_registersPostgresAuditAndNoOpMetricsWithoutAwsStores() {
        runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, definitions(context, AuditService.class));
            assertEquals(1, definitions(context, PostgresAuditService.class));
            assertEquals(0, definitions(context, DynamoDbClient.class));
            assertEquals(1, definitions(context, MetricsEmitter.class));
            assertEquals(1, definitions(context, NoOpMetricsEmitter.class));
            assertEquals(0, definitions(context, CloudWatchClient.class));
        });
    }

    @Test
    void localProfile_keepsTheProviderChoice() {
        runner.withPropertyValues("spring.profiles.active=local", "llm.provider=bedrock").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, definitions(context, BedrockCommandParser.class));
            assertEquals(1, definitions(context, BedrockRuntimeClient.class));
        });
    }
}
