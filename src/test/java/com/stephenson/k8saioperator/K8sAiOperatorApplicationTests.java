package com.stephenson.k8saioperator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@SpringBootTest
class K8sAiOperatorApplicationTests {

    @MockBean
    BedrockRuntimeClient bedrockRuntimeClient;

    @MockBean
    DynamoDbClient dynamoDbClient;

    @MockBean
    CloudWatchClient cloudWatchClient;

    @Test
    void contextLoads() {
    }
}
