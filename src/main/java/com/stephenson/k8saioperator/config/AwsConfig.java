package com.stephenson.k8saioperator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Produces AWS SDK v2 client beans.
 * Credentials are resolved automatically from the default provider chain
 * (environment variables, instance profile, etc.).
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "bedrock", matchIfMissing = true)
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    @Profile("!local")
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    @Profile("!local")
    public CloudWatchClient cloudWatchClient() {
        return CloudWatchClient.builder()
                .region(Region.of(region))
                .build();
    }
}

