package com.stephenson.k8saioperator.screenshots;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * Screenshot-only overlay, compiled and put on the classpath by demo.sh; it is not part of the
 * application build. It adds primary AWS clients that talk to the local stub (stub_aws.py),
 * because the AWS SDK version in use does not read AWS_ENDPOINT_URL.
 */
@Configuration
public class StubEndpointConfig {

    @Value("${screenshots.stub-endpoint}")
    private String endpoint;

    @Bean @Primary
    public BedrockRuntimeClient stubBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint)).build();
    }

    @Bean @Primary
    public DynamoDbClient stubDynamoDbClient() {
        return DynamoDbClient.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint)).build();
    }

    @Bean @Primary
    public CloudWatchClient stubCloudWatchClient() {
        return CloudWatchClient.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint)).build();
    }
}
